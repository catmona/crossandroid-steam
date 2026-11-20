package de.radicalfishgames.crosscode

import de.radicalfishgames.crosscode.features.FullSaveImportFeature
import de.radicalfishgames.crosscode.features.SteamFeature
import de.radicalfishgames.crosscode.steam.SteamBridge
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
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
    private var physicalControllerBridge: PhysicalControllerBridge? = null
    lateinit var gameFolderManager: GameFolderManager
        private set

    // Default file written by the settings screen's PreferenceManager
    val preferences: SharedPreferences
        get() = getSharedPreferences("de.radicalfishgames.crosscode_preferences", Context.MODE_PRIVATE)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityGameBinding.inflate(layoutInflater)
        setContentView(binding.root)

        gameFolderManager = GameFolderManager(this)
        SteamBridge.init(this)

        setupBackPressHandler()
        startGame()
    }

    private fun setupBackPressHandler() {
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    // Controller B sends BACK; don't let it close the game while a controller is connected
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
            Toast.makeText(this@GameActivity, R.string.error_no_game_folder, Toast.LENGTH_LONG).show()
            finish()
            return
        }

        var gameEntryPoint = "assets/node-webkit.html"
        val modLoaderEntry = gameFolderManager.findModLoader()?.entry
        var modLoaderPresent = false
        if (modLoaderEntry != null) {
            Log.d("CrossCode", "Modloader found!")
            gameEntryPoint = modLoaderEntry
            modLoaderPresent = true
        }

        Log.d("CrossCode", "Game entry point: $gameEntryPoint Attempting to load.")

        val pixelatedScaling = preferences.getBoolean("pixelated_scaling", false)
        val gaplessMusic = preferences.getBoolean("gapless_music", true)
        gameWrapper = GameWrapper(binding.gameView, modLoaderPresent, gameFolderManager, pixelatedScaling, gaplessMusic)
        gameWrapper.initWebView()

        // Initialized in list order; some features depend on earlier ones
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
                SteamFeature(gameWrapper, this),
                FullSaveImportFeature(gameWrapper, this),
            ),
        )

        // Physical input is only bridged when the virtual overlay is off; force_virtual deliberately ignores it
        val vcf = gameWrapper.getFeature<VirtualControllerFeature>()
        if (!vcf.enabled) {
            physicalControllerBridge = PhysicalControllerBridge(vcf.gamepadState)
        }

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

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val device = event.device
        if (device != null && isController(device)) {
            if (physicalControllerBridge?.onKeyEvent(event) == true) {
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        val device = event.device
        // Controllers can also emit touchpad/mouse motion (e.g. DualShock touchpad), which reads all
        // joystick axes as 0 and would reset sticks, triggers and hat mid-input
        if (device != null && isController(device) && event.isFromSource(InputDevice.SOURCE_JOYSTICK)) {
            if (physicalControllerBridge?.onMotionEvent(event) == true) {
                return true
            }
        }
        return super.dispatchGenericMotionEvent(event)
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

        SteamBridge.setPlaying(false)
    }

    override fun onResume() {
        super.onResume()

        Log.d("CrossCode", "Resuming game.")

        gameWrapper.onResume()

        // Counts playtime and shows "Playing CrossCode" while the game is in front
        SteamBridge.setPlaying(true)

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
