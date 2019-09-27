package de.radicalfishgames.crosscode

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity


class GameActivity : AppCompatActivity() {

    private lateinit var gameWrapper: GameWrapper
    private lateinit var controllerMapper: ControllerMapper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_game)

        gameWrapper = GameWrapper(findViewById(R.id.game_view))
        gameWrapper.initWebView()
        gameWrapper.loadGame()

        controllerMapper = ControllerMapper(gameWrapper, findViewById(R.id.root_view))
        controllerMapper.initControls()

    }
}
