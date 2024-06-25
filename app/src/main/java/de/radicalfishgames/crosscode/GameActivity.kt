package de.radicalfishgames.crosscode

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.InputDevice
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import de.radicalfishgames.crosscode.features.*
import de.radicalfishgames.crosscode.gamelisteners.GameEventManager
import kotlinx.android.synthetic.main.activity_game.*
import java.io.File


class GameActivity : AppCompatActivity() {

    val errorHandler = ErrorHandler(this)

    private lateinit var gameWrapper: GameWrapper

    // This SharedPreferences files is the one the preference activity automatically generates
    val preferences: SharedPreferences
         get() = getSharedPreferences("de.radicalfishgames.crosscode_preferences", Context.MODE_PRIVATE)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_game)

        startGame()
    }

    private fun startGame(){
        Thread.setDefaultUncaughtExceptionHandler(errorHandler)

        val nativeControllerActive = isNativeControllerActive()
        Log.d("CrossCode", "Native controller found: $nativeControllerActive")


        val dataFilesPath = this.getExternalFilesDir(null)
        if (dataFilesPath == null) {
            Toast.makeText(this@GameActivity, "Storage permission denied", Toast.LENGTH_LONG).show()
            return
        }
        val gameDir = "${dataFilesPath.absolutePath}/CrossCode"
        Log.d("CrossCode", "Game directory: $gameDir")

        var gameEntryPoint = "assets/node-webkit.html"
        val modLoaderEntryFile = (File("$gameDir/ccloader/index.html") or File("$gameDir/ccloader/main.html")) or File("$gameDir/ccloader3/main.html")
        var modLoaderPresent = false
        if(modLoaderEntryFile.exists()){
            Log.d("CrossCode", "Modloader found!")
            gameEntryPoint = modLoaderEntryFile.canonicalPath.replace(Regex("(.*:\\d+/)"), "")
            modLoaderPresent = true
        }

        Log.d("CrossCode", "Game entry point: $gameEntryPoint Attempting to load.")


        gameWrapper = GameWrapper(game_view, modLoaderPresent, gameDir)
        gameWrapper.initWebView()

        // Order is important, since some of these depend on one another and this is also the initialization order
        gameWrapper.features.addAll(listOf(
            ModListProvider(gameWrapper, this),
            ExtensionLoadingFix(gameWrapper, this),
            GameEventManager(gameWrapper, this),
            VirtualControllerFeature(gameWrapper, this),
            AutoLayoutSwitchFeature(gameWrapper, this),
            CutsceneLayoutDisableFeature(gameWrapper, this),
            OverlayTransparencyFeature(gameWrapper, this),
            ImportExportFeature(gameWrapper, this),
            HapticFeedbackFeature(gameWrapper, this),
            OverlayScaleFeature(gameWrapper, this)
        ))

        gameWrapper.loadGame(gameEntryPoint)

    }

    override fun onBackPressed() {
        // Disable going back with controller B button
        if(!isNativeControllerActive()){
            super.onBackPressed()

        }else{

            Log.d("CrossCode", "Blocked back-press-event because a native controller is being used.")
        }
    }

    fun isNativeControllerActive(): Boolean {
        Log.d("CrossCode", "Searching for native controller...")

        InputDevice.getDeviceIds().forEach { deviceId ->
            val device = InputDevice.getDevice(deviceId)

            if(isController(device)){
                Log.d("CrossCode", "Found device with gamepad and joystick support: ${device.name}")
                Log.v("CrossCode", device.toString())
                return true
            }
        }

        return false
    }

    private fun isController(device: InputDevice): Boolean {
        // SOURCE_DPAD is apparently not how a gamepad is labelled, despite having a DPad available
        val hasControllerSources = device.supportsSource(InputDevice.SOURCE_GAMEPAD) && device.supportsSource(InputDevice.SOURCE_JOYSTICK)

        if(hasControllerSources && device.name.equals("uinput-fpc")) {
            Log.w("CrossCode", "Ignoring input device uinput-fpc as it's known to be a fingerprint scanner disguising as a controller: https://github.com/libgdx/gdx-controllers/issues/9")
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

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        gameWrapper.getFeature(ImportExportFeature::class)
            .onActivityResult(requestCode, resultCode, data)

        super.onActivityResult(requestCode, resultCode, data)
    }

    fun goIntoFullScreen(){
        runOnUiThread {
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    // Set the content to appear under the system bars so that the
                    // content doesn't resize when the system bars hide and show.
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    // Hide the nav bar and status bar
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_FULLSCREEN)
        }
    }
}
