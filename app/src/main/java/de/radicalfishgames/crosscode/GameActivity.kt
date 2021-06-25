package de.radicalfishgames.crosscode

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.os.storage.OnObbStateChangeListener
import android.os.storage.StorageManager
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

    private lateinit var storageManager: StorageManager

    private val version = 1
    private lateinit var obbName: String
    private lateinit var obbPath: String

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

        val gameFilePath = getGamePath()
        val modLoaderPresent = gameFilePath.contains("ccloader")

        Log.d("CrossCode", "Game path: $gameFilePath Attempting to load.")


        gameWrapper = GameWrapper(game_view, modLoaderPresent, storageManager.getMountedObbPath(obbPath))
        gameWrapper.initWebView()

        // Order is important, since some of these depend on one another and this is also the initialization order
        gameWrapper.features.addAll(listOf(
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

        gameWrapper.loadGame("file:///$gameFilePath")

    }

    private fun getGamePath(): String {

        obbName = "main.$version.$packageName.obb"
        obbPath = "$obbDir/$obbName"

        storageManager = getSystemService(Context.STORAGE_SERVICE) as StorageManager

        val gameDir = storageManager.getMountedObbPath(obbPath)

        val normalLaunchFile = File("$gameDir/node-webkit.html")
        val modLoaderLaunchFile = File("$gameDir/ccloader/index.html")

        val launchFile: File
        if(modLoaderLaunchFile.exists()){
            launchFile = modLoaderLaunchFile

            Toast.makeText(this, "Warning: Newer versions of the modloader currently don't work with CrossAndroid!", Toast.LENGTH_LONG).show()

            Log.d("CrossCode", "Modloader found!")
        }else{
            launchFile = normalLaunchFile

            Log.d("CrossCode", "")
        }

        return launchFile.absolutePath
    }

    override fun onBackPressed() {
        // Disable going back with controller B button
        if(!isNativeControllerActive()){
            super.onBackPressed()

        }else{

            Log.d("CrossCode", "Blocked back-press-event because a native controller is being used.")
        }
    }

    fun isNativeControllerActive(): Boolean{
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

    private fun isController(device: InputDevice): Boolean{
        return device.supportsSource(InputDevice.SOURCE_GAMEPAD)
                && device.supportsSource(InputDevice.SOURCE_JOYSTICK)
        // SOURCE_DPAD is apparently not how a gamepad is labelled, despite having a DPad available
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

    override fun onDestroy() {
        super.onDestroy()

        Log.d("CrossCode", "Attempting to unmount game files...")

        storageManager.unmountObb(obbPath, true, object : OnObbStateChangeListener() {
            // Do nothing, we can't really do much about it if unmounting fails
            override fun onObbStateChange(path: String?, state: Int) {
                if(state != UNMOUNTED){
                    Log.e("CrossCode", "Unmounting the game assets failed! State: $state")
                }
            }
        })
    }
}
