package de.radicalfishgames.crosscode.steam

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import de.radicalfishgames.crosscode.R
import de.radicalfishgames.crosscode.databinding.ActivitySteamLoginBinding
import de.radicalfishgames.crosscode.steam.SteamBridge.Status as S
import de.radicalfishgames.crosscode.ui.applySystemBarInsetsAsPadding
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/**
 * Sign in to Steam: scan a QR code with the Steam app on another phone, open the same code in the
 * Steam app on this device, or type account name and password (Steam Guard follows).
 * Once signed in, a panel shows what Steam itself has confirmed, with a "Check now" button.
 */
class SteamLoginActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySteamLoginBinding
    private var shownQrUrl: String? = null
    private val timeFormat: DateFormat = DateFormat.getTimeInstance(DateFormat.SHORT)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySteamLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarInsetsAsPadding()

        SteamBridge.init(this)

        binding.steamSignInButton.setOnClickListener { SteamBridge.startQrLogin() }
        binding.steamSignOutButton.setOnClickListener { SteamBridge.signOut() }
        binding.steamOpenInAppButton.setOnClickListener { openQrInSteamApp() }
        binding.steamPasswordButton.setOnClickListener { signInWithPassword() }
        binding.steamPassword.setOnEditorActionListener { _, action, _ ->
            (action == EditorInfo.IME_ACTION_DONE).also { if (it) signInWithPassword() }
        }
        binding.steamCodeButton.setOnClickListener { submitCode() }
        binding.steamCode.setOnEditorActionListener { _, action, _ ->
            (action == EditorInfo.IME_ACTION_DONE).also { if (it) submitCode() }
        }
        binding.steamCancelButton.setOnClickListener { SteamBridge.cancelLogin() }
        binding.steamCheckButton.setOnClickListener { SteamBridge.checkNow() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { SteamBridge.state.collect { render(it) } }
                launch { SteamBridge.health.collect { renderHealth(it) } }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        SteamBridge.setPanelOpen(true)
        SteamBridge.ensureConnected()
    }

    override fun onStop() {
        super.onStop()
        SteamBridge.setPanelOpen(false)
    }

    override fun onDestroy() {
        super.onDestroy()
        // Leaving the screen mid-sign-in abandons it
        if (isFinishing) SteamBridge.cancelLogin()
    }

    private fun signInWithPassword() {
        val user = binding.steamUsername.text?.toString()?.trim().orEmpty()
        val password = binding.steamPassword.text?.toString().orEmpty()
        if (user.isEmpty() || password.isEmpty()) return
        // Clear the field right away; the password only travels on to Steam's auth call
        binding.steamPassword.text?.clear()
        SteamBridge.startCredentialsLogin(user, password)
    }

    private fun submitCode() {
        val code = binding.steamCode.text?.toString()?.trim().orEmpty()
        if (code.isEmpty()) return
        binding.steamCode.text?.clear()
        SteamBridge.submitCode(code)
    }

    /** The QR challenge URL (https://s.team/q/...) opens the Steam app's approval screen on this device. */
    private fun openQrInSteamApp() {
        val url = SteamBridge.state.value.qrUrl ?: return
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val steamApp = "com.valvesoftware.android.steam.community"
        try {
            startActivity(Intent(intent).setPackage(steamApp))
        } catch (_: ActivityNotFoundException) {
            try {
                startActivity(intent) // no Steam app: the browser page offers the same approval
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(this, R.string.steam_open_in_app_missing, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun render(state: SteamBridge.State) {
        binding.steamStatus.text =
            when (state.status) {
                S.SIGNED_OUT -> getString(R.string.steam_status_signed_out)
                S.CONNECTING -> getString(R.string.steam_status_connecting)
                S.WAITING_FOR_QR -> getString(R.string.steam_status_scan)
                S.SIGNING_IN -> getString(R.string.steam_status_signing_in)
                S.WAITING_FOR_APPROVAL -> getString(R.string.steam_status_approve)
                S.NEEDS_CODE -> getString(R.string.steam_status_code)
                S.ONLINE -> getString(R.string.steam_status_online, state.accountName)
                S.OFFLINE -> getString(R.string.steam_status_offline, state.accountName)
            }
        binding.steamHint.text =
            state.message ?: when (state.status) {
                S.WAITING_FOR_QR -> getString(R.string.steam_hint_scan)
                S.WAITING_FOR_APPROVAL -> getString(R.string.steam_hint_approve)
                S.NEEDS_CODE -> codePromptText(state.codePrompt)
                S.SIGNED_OUT, S.SIGNING_IN -> getString(R.string.steam_hint_signed_out)
                else -> getString(R.string.steam_hint_signed_in)
            }

        val signedIn = SteamBridge.isSignedIn
        val inSignIn = state.status in setOf(S.WAITING_FOR_QR, S.SIGNING_IN, S.WAITING_FOR_APPROVAL, S.NEEDS_CODE)
        val idleSignedOut = !signedIn && !inSignIn

        binding.steamSignInButton.visibility = if (idleSignedOut) View.VISIBLE else View.GONE
        binding.steamSignInButton.isEnabled = state.status != S.CONNECTING
        binding.steamPasswordGroup.visibility = if (idleSignedOut) View.VISIBLE else View.GONE
        binding.steamPasswordButton.isEnabled = state.status != S.CONNECTING
        binding.steamCodeGroup.visibility = if (state.status == S.NEEDS_CODE) View.VISIBLE else View.GONE
        binding.steamCancelButton.visibility = if (inSignIn) View.VISIBLE else View.GONE
        binding.steamSignOutButton.visibility = if (signedIn && !inSignIn) View.VISIBLE else View.GONE
        binding.steamCheckGroup.visibility = if (signedIn && !inSignIn) View.VISIBLE else View.GONE

        val qr = state.qrUrl
        if (state.status == S.WAITING_FOR_QR && qr != null) {
            if (qr != shownQrUrl) {
                binding.steamQr.setImageBitmap(qrBitmap(qr, 512))
                shownQrUrl = qr
            }
            binding.steamQr.visibility = View.VISIBLE
            binding.steamOpenInAppButton.visibility = View.VISIBLE
        } else {
            binding.steamQr.visibility = View.GONE
            binding.steamOpenInAppButton.visibility = View.GONE
            shownQrUrl = null
        }
        if (state.status == S.NEEDS_CODE) binding.steamCode.requestFocus()
    }

    private fun codePromptText(prompt: SteamBridge.CodePrompt?): String {
        val base =
            when (prompt?.kind) {
                SteamBridge.CodeKind.EMAIL -> getString(R.string.steam_code_email, prompt.email ?: getString(R.string.steam_your_email))
                else -> getString(R.string.steam_code_authenticator)
            }
        return if (prompt?.previousWasWrong == true) getString(R.string.steam_code_wrong) + "\n" + base else base
    }

    private fun renderHealth(h: SteamBridge.Health) {
        fun t(ms: Long) = timeFormat.format(Date(ms))
        val lines = ArrayList<String>()
        lines +=
            when {
                h.playingConfirmedAt != null -> getString(R.string.steam_check_playing_confirmed, t(h.playingConfirmedAt), h.playingNote ?: "")
                h.playingSentAt != null && h.playingWaiting -> getString(R.string.steam_check_playing_waiting, t(h.playingSentAt))
                h.playingNote != null -> getString(R.string.steam_check_playing_note, h.playingNote)
                else -> getString(R.string.steam_check_playing_none)
            }
        lines +=
            if (h.unlockedOnSteam != null && h.schemaSize != null) {
                getString(R.string.steam_check_on_steam, h.unlockedOnSteam, h.schemaSize)
            } else {
                getString(R.string.steam_check_on_steam_unknown)
            }
        lines += getString(R.string.steam_check_reported, h.reported, h.verified, h.pending)
        if (h.lastCheckAt != null) lines += getString(R.string.steam_check_last, t(h.lastCheckAt), h.lastCheckNote ?: "")
        binding.steamCheckText.text = lines.joinToString("\n")
    }

    private fun qrBitmap(
        text: String,
        size: Int,
    ): Bitmap {
        val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, mapOf(EncodeHintType.MARGIN to 0))
        val pixels = IntArray(size * size)
        for (y in 0 until size) {
            for (x in 0 until size) {
                pixels[y * size + x] = if (matrix[x, y]) Color.BLACK else Color.WHITE
            }
        }
        return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
    }
}
