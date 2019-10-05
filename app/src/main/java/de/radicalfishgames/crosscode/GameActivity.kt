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
import de.radicalfishgames.crosscode.mapping.NativeMapper
import kotlinx.android.synthetic.main.activity_game.*
import java.io.File


class GameActivity : AppCompatActivity() {

    private lateinit var storageManager: StorageManager

    private val version = 1
    private lateinit var obbName: String
    private lateinit var obbPath: String

    private lateinit var gameWrapper: GameWrapper
    private val nativeControllerWatchDog = NativeControllerListener(
        this::onFirstNativeControllerConnected,
        this::onNativeControllerSwitched,
        this::onLastNativeControllerDisconnected
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_game)

        gameWrapper = GameWrapper(this, game_view)
        gameWrapper.initWebView()

        val inputManager = getSystemService(Context.INPUT_SERVICE) as InputManager
        inputManager.registerInputDeviceListener(nativeControllerWatchDog, null)

        if(!nativeControllerWatchDog.hasNativeController){
            gameWrapper.useControls(CombatLayoutMapper(gameWrapper))
        }

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

    fun loadControlLayout(@LayoutRes layoutId: Int): View{
        control_container.removeAllViews()
        return layoutInflater.inflate(layoutId, control_container)
    }

    private fun onFirstNativeControllerConnected(){
        gameWrapper.useControls(NativeMapper(gameWrapper, nativeControllerWatchDog.nativeControllerDeviceId))
    }

    private fun onNativeControllerSwitched(){
        gameWrapper.useControls(NativeMapper(gameWrapper, nativeControllerWatchDog.nativeControllerDeviceId))
    }

    private fun onLastNativeControllerDisconnected(){
        gameWrapper.useControls(CombatLayoutMapper(gameWrapper))
    }

    override fun onResume() {
        super.onResume()

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

        val inputManager = getSystemService(Context.INPUT_SERVICE) as InputManager
        inputManager.unregisterInputDeviceListener(nativeControllerWatchDog)

        storageManager.unmountObb(obbPath, true, object : OnObbStateChangeListener() {
            // Do nothing, we can't really do much about it if unmounting fails
            override fun onObbStateChange(path: String?, state: Int) {
                Log.e("CrossCode", "Unmounting the game assets failed! State: $state")
            }
        })
    }
}
