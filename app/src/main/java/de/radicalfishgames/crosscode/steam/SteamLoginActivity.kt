package de.radicalfishgames.crosscode.steam

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import de.radicalfishgames.crosscode.R
import de.radicalfishgames.crosscode.ui.applySystemBarInsetsAsPadding
import de.radicalfishgames.crosscode.databinding.ActivitySteamLoginBinding
import kotlinx.coroutines.launch

/** Sign in to Steam by scanning a QR code with the Steam mobile app. */
class SteamLoginActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySteamLoginBinding
    private var shownQrUrl: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySteamLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarInsetsAsPadding()

        SteamBridge.init(this)

        binding.steamSignInButton.setOnClickListener { SteamBridge.startQrLogin() }
        binding.steamSignOutButton.setOnClickListener { SteamBridge.signOut() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                SteamBridge.state.collect { render(it) }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        SteamBridge.ensureConnected()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Leaving the screen mid-scan abandons that sign-in
        if (isFinishing) SteamBridge.cancelQrLogin()
    }

    private fun render(state: SteamBridge.State) {
        val status =
            when (state.status) {
                SteamBridge.Status.SIGNED_OUT -> getString(R.string.steam_status_signed_out)
                SteamBridge.Status.CONNECTING -> getString(R.string.steam_status_connecting)
                SteamBridge.Status.WAITING_FOR_QR -> getString(R.string.steam_status_scan)
                SteamBridge.Status.ONLINE -> getString(R.string.steam_status_online, state.accountName)
                SteamBridge.Status.OFFLINE -> getString(R.string.steam_status_offline, state.accountName)
            }
        binding.steamStatus.text = status
        binding.steamHint.text =
            state.message ?: when (state.status) {
                SteamBridge.Status.WAITING_FOR_QR -> getString(R.string.steam_hint_scan)
                SteamBridge.Status.SIGNED_OUT -> getString(R.string.steam_hint_signed_out)
                else -> getString(R.string.steam_hint_signed_in)
            }

        val signedIn = SteamBridge.isSignedIn
        binding.steamSignInButton.visibility = if (signedIn || state.status == SteamBridge.Status.WAITING_FOR_QR) View.GONE else View.VISIBLE
        binding.steamSignInButton.isEnabled = state.status != SteamBridge.Status.CONNECTING
        binding.steamSignOutButton.visibility = if (signedIn) View.VISIBLE else View.GONE

        val qr = state.qrUrl
        if (state.status == SteamBridge.Status.WAITING_FOR_QR && qr != null) {
            if (qr != shownQrUrl) {
                binding.steamQr.setImageBitmap(qrBitmap(qr, 512))
                shownQrUrl = qr
            }
            binding.steamQr.visibility = View.VISIBLE
        } else {
            binding.steamQr.visibility = View.GONE
            shownQrUrl = null
        }
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
