package de.radicalfishgames.crosscode

import android.content.Context
import android.hardware.input.InputManager
import android.os.Bundle
import android.os.storage.OnObbStateChangeListener
import android.os.storage.StorageManager
import android.util.Log
import android.view.View
import androidx.annotation.LayoutRes
import androidx.appcompat.app.AppCompatActivity
import de.radicalfishgames.crosscode.mapping.CombatLayoutMapper
import kotlinx.android.synthetic.main.activity_game.*
import java.io.File


class GameActivity : AppCompatActivity() {

    private lateinit var storageManager: StorageManager

    private val version = 1
    private lateinit var obbName: String
    private lateinit var obbPath: String

    private lateinit var gameWrapper: GameWrapper

    private val nativeControllerListener = NativeControllerListener(
        this::onFirstNativeControllerConnected,
        this::onNativeControllerSwitched,
        this::onLastNativeControllerDisconnected
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_game)

        Log.d("CrossCode", "Registering native controller listener...")

        val inputManager = getSystemService(Context.INPUT_SERVICE) as InputManager
        inputManager.registerInputDeviceListener(nativeControllerListener, null)

        Log.d("CrossCode", "Searching for native controller...")

        nativeControllerListener.searchForController()
        val nativeControllerActive = nativeControllerListener.hasNativeController

        Log.d("CrossCode", "Native controller found: $nativeControllerActive")
        Log.d("CrossCode, ", "Initializing WebView")

        gameWrapper = GameWrapper(this, game_view, !nativeControllerActive)
        gameWrapper.initWebView()

        if(!nativeControllerActive){
            gameWrapper.useControls(CombatLayoutMapper(gameWrapper))

        } else{
            Log.d("CrossCode", "Using native controller for input")

        }

        Log.d("CrossCode", "Attempting to load game files.")

        load()
    }

    private fun load() {

        obbName = "main.$version.$packageName.obb"
        obbPath = "$obbDir/$obbName"

        Log.d("CrossCode", "Looking for OBB on path $obbPath")

        storageManager = getSystemService(Context.STORAGE_SERVICE) as StorageManager

        val mountedPath = storageManager.getMountedObbPath(obbPath)

        Log.d("CrossCode", "Mounted path: $mountedPath Attempting to load.")

        val launchFile = File("$mountedPath/node-webkit.html")

        gameWrapper.loadGame("file:///${launchFile.absolutePath}")

    }

    override fun onBackPressed() {
        // Disable going back with controller B button
        if(!nativeControllerListener.hasNativeController){
            super.onBackPressed()

        }else{

            Log.d("CrossCode", "Blocked back-press-event because a native controller is being used.")
        }
    }

    fun loadControlLayout(@LayoutRes layoutId: Int): View{
        control_container.removeAllViews()
        return layoutInflater.inflate(layoutId, control_container)
    }

    private fun onFirstNativeControllerConnected(){
        // Don't do anything, since the controller input should be handled automatically
    }

    private fun onNativeControllerSwitched(){
        // Don't do anything, since the controller input should be handled automatically
    }

    private fun onLastNativeControllerDisconnected(){
        // Don't do anything, since the controller input should be handled automatically
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

    override fun onDestroy() {
        super.onDestroy()

        Log.d("CrossCode", "Unregistering native controller listeners...")

        val inputManager = getSystemService(Context.INPUT_SERVICE) as InputManager
        inputManager.unregisterInputDeviceListener(nativeControllerListener)

        Log.d("CrossCode", "Attempting to unmount game files...")

        storageManager.unmountObb(obbPath, true, object : OnObbStateChangeListener() {
            // Do nothing, we can't really do much about it if unmounting fails
            override fun onObbStateChange(path: String?, state: Int) {
                Log.e("CrossCode", "Unmounting the game assets failed! State: $state")
            }
        })
    }
}
