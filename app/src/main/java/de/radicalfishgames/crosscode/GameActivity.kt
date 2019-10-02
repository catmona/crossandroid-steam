package de.radicalfishgames.crosscode

import android.content.Context
import android.os.Bundle
import android.os.storage.StorageManager
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_game.*
import java.io.File


class GameActivity : AppCompatActivity() {

    private lateinit var storageManager: StorageManager

    private lateinit var gameWrapper: GameWrapper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_game)

        gameWrapper = GameWrapper(game_view)
        gameWrapper.initWebView()

        showControlLayout(CombatLayoutMapper(gameWrapper))

        load()
    }

    private fun load() {

        val version = 1
        val obbName = "main.$version.$packageName.obb"

        val obbPath = "$obbDir/$obbName"

        Log.d("CrossCode", "Looking for OBB on path $obbPath")

        storageManager = getSystemService(Context.STORAGE_SERVICE) as StorageManager

        val mountedPath = storageManager.getMountedObbPath(obbPath)
        Log.d("CrossCode", "Mounted path: $mountedPath Attempting to load.")

        val launchFile = File("$mountedPath/node-webkit.html")

        gameWrapper.loadGame("file:///${launchFile.absolutePath}")

    }

    private fun showControlLayout(controlLayout: ControllerMapper){
        val layoutId = controlLayout.layoutResId
        val view = layoutInflater.inflate(layoutId, control_container)
        controlLayout.containerView = view
        controlLayout.initControls()
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
}
