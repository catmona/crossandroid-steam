package de.radicalfishgames.crosscode

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.InputDevice
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import de.radicalfishgames.crosscode.databinding.ActivityGameBinding
import de.radicalfishgames.crosscode.features.AutoLayoutSwitchFeature
import de.radicalfishgames.crosscode.features.CutsceneLayoutDisableFeature
import de.radicalfishgames.crosscode.features.ExtensionLoadingFix
import de.radicalfishgames.crosscode.features.HapticFeedbackFeature
import de.radicalfishgames.crosscode.features.ImportExportFeature
import de.radicalfishgames.crosscode.features.ModListProvider
import de.radicalfishgames.crosscode.features.OverlayScaleFeature
import de.radicalfishgames.crosscode.features.OverlayTransparencyFeature
import de.radicalfishgames.crosscode.features.VirtualControllerFeature
import de.radicalfishgames.crosscode.gamelisteners.GameEventManager

class GameActivity : AppCompatActivity() {
    lateinit var binding: ActivityGameBinding
        private set

    val errorHandler = ErrorHandler(this)

    private lateinit var gameWrapper: GameWrapper
    lateinit var gameFolderManager: GameFolderManager
        private set

    // This SharedPreferences files is the one the preference activity automatically generates
    val preferences: SharedPreferences
        get() = getSharedPreferences("de.radicalfishgames.crosscode_preferences", Context.MODE_PRIVATE)

    // Activity Result API launcher for save export
    val createDocumentLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri: Uri? ->
            gameWrapper.getFeature(ImportExportFeature::class).onCreateDocumentResult(uri)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityGameBinding.inflate(layoutInflater)
        setContentView(binding.root)

        gameFolderManager = GameFolderManager(this)

        setupBackPressHandler()
        startGame()
    }

    private fun setupBackPressHandler() {
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    // Disable going back with controller B button
                    if (!isNativeControllerActive()) {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                    } else {
                        Log.d("CrossCode", "Blocked back-press-event because a native controller is being used.")
                    }
                }
            },
        )
    }

    private fun startGame() {
        Thread.setDefaultUncaughtExceptionHandler(errorHandler)

        val nativeControllerActive = isNativeControllerActive()
        Log.d("CrossCode", "Native controller found: $nativeControllerActive")

        if (!gameFolderManager.hasValidGameFolder()) {
            Toast.makeText(this@GameActivity, "No game folder selected", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        var gameEntryPoint = "assets/node-webkit.html"
        val modLoaderEntry = gameFolderManager.findModLoaderEntry()
        var modLoaderPresent = false
        if (modLoaderEntry != null) {
            Log.d("CrossCode", "Modloader found!")
            gameEntryPoint = modLoaderEntry
            modLoaderPresent = true
        }

        Log.d("CrossCode", "Game entry point: $gameEntryPoint Attempting to load.")

        gameWrapper = GameWrapper(binding.gameView, modLoaderPresent, gameFolderManager)
        gameWrapper.initWebView()

        // Order is important, since some of these depend on one another and this is also the initialization order
        gameWrapper.features.addAll(
            listOf(
                ModListProvider(gameWrapper, this),
                ExtensionLoadingFix(gameWrapper, this),
                GameEventManager(gameWrapper, this),
                VirtualControllerFeature(gameWrapper, this),
                AutoLayoutSwitchFeature(gameWrapper, this),
                CutsceneLayoutDisableFeature(gameWrapper, this),
                OverlayTransparencyFeature(gameWrapper, this),
                ImportExportFeature(gameWrapper, this),
                HapticFeedbackFeature(gameWrapper, this),
                OverlayScaleFeature(gameWrapper, this),
            ),
        )

        gameWrapper.loadGame(gameEntryPoint)
    }

    fun isNativeControllerActive(): Boolean {
        Log.d("CrossCode", "Searching for native controller...")

        InputDevice.getDeviceIds().forEach { deviceId ->
            val device = InputDevice.getDevice(deviceId) ?: return@forEach

            if (isController(device)) {
                Log.d("CrossCode", "Found device with gamepad and joystick support: ${device.name}")
                Log.v("CrossCode", device.toString())
                return true
            }
        }

        return false
    }

    private fun isController(device: InputDevice?): Boolean {
        if (device == null) return false
        // SOURCE_DPAD is apparently not how a gamepad is labelled, despite having a DPad available
        val hasControllerSources = device.supportsSource(InputDevice.SOURCE_GAMEPAD) && device.supportsSource(InputDevice.SOURCE_JOYSTICK)

        if (hasControllerSources && device.name.equals("uinput-fpc")) {
            Log.w(
                "CrossCode",
                "Ignoring input device uinput-fpc as it's known to be a fingerprint scanner disguising as a controller: https://github.com/libgdx/gdx-controllers/issues/9",
            )
            return false
        }

        return hasControllerSources
    }

    override fun onPause() {
        super.onPause()

        Log.d("CrossCode", "Pausing game.")

        gameWrapper.onPause()
    }

    override fun onResume() {
        super.onResume()

        Log.d("CrossCode", "Resuming game.")

        gameWrapper.onResume()

        goIntoFullScreen()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::gameWrapper.isInitialized) {
            gameWrapper.onDestroy()
        }
    }

    fun goIntoFullScreen() {
        runOnUiThread {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            WindowInsetsControllerCompat(window, window.decorView).let { controller ->
                controller.hide(WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
    }
}
