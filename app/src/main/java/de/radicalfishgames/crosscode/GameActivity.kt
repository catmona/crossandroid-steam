package de.radicalfishgames.crosscode

import android.os.Bundle
import android.os.storage.StorageManager
import android.view.View
import androidx.appcompat.app.AppCompatActivity


class GameActivity : AppCompatActivity() {

    private lateinit var gameWrapper: GameWrapper
    private lateinit var controllerMapper: ControllerMapper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_game)

        gameWrapper = GameWrapper(findViewById(R.id.game_view))
        gameWrapper.initWebView()

        controllerMapper = ControllerMapper(gameWrapper, findViewById(R.id.root_view))
        controllerMapper.initControls()

        //TODO instead of bundling all game files in the APK, put them in an Android expansion file/OBB
        // - the first attempt at this didn't work because WebView didn't interpret the mounted path
        // correctly.
        // Expansion files general : https://developer.android.com/google/play/expansion-files
        // Creating an OBB file: https://developer.android.com/studio/command-line/jobb
        // Mounting an OBB file: https://developer.android.com/reference/android/os/storage/StorageManager.html#mountObb(java.lang.String,%20java.lang.String,%20android.os.storage.OnObbStateChangeListener)
        gameWrapper.loadGame("file:///android_asset/www/node-webkit.html")

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
