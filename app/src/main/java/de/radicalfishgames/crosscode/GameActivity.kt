package de.radicalfishgames.crosscode

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.os.storage.OnObbStateChangeListener
import android.os.storage.StorageManager
import android.util.Log
import android.view.InputDevice
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import de.radicalfishgames.crosscode.features.*
import de.radicalfishgames.crosscode.gamelisteners.GameEventManager
import de.radicalfishgames.crosscode.mapping.CombatLayoutMapper
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


        gameWrapper = GameWrapper(game_view)
        gameWrapper.initWebView()

        // Order is important, since some of these depend on one another and this is also the initialization order
        gameWrapper.features.addAll(listOf(
            GameEventManager(gameWrapper, this),
            VirtualControllerFeature(gameWrapper, this),
            AutoLayoutSwitchFeature(gameWrapper, this),
            CutsceneLayoutDisableFeature(gameWrapper, this),
            OverlayTransparencyFeature(gameWrapper, this),
            ImportExportFeature(gameWrapper, this),
            HapticFeedbackFeature(gameWrapper, this),
            OverlayScaleFeature(gameWrapper, this)
        ))



        val gameFilePath = loadGameFiles()
        Log.d("CrossCode", "Game path: $gameFilePath Attempting to load.")

        val launchFile = File("$gameFilePath/node-webkit.html")

        gameWrapper.loadGame("file:///${launchFile.absolutePath}")

    }

    private fun loadGameFiles(): String {

        Log.d("CrossCode", "Attempting to load game files.")

        obbName = "main.$version.$packageName.obb"
        obbPath = "$obbDir/$obbName"

        Log.d("CrossCode", "Looking for OBB on path $obbPath")

        storageManager = getSystemService(Context.STORAGE_SERVICE) as StorageManager

        return storageManager.getMountedObbPath(obbPath)
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
                Log.e("CrossCode", "Unmounting the game assets failed! State: $state")
            }
        })
    }
}
