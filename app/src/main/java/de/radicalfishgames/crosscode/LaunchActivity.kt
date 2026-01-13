package de.radicalfishgames.crosscode

import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.widget.ScrollView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import de.radicalfishgames.crosscode.databinding.ActivityLaunchBinding
import de.radicalfishgames.crosscode.databinding.GuideLayoutBinding
import de.radicalfishgames.crosscode.preferences.PreferencesActivity
import de.radicalfishgames.crosscode.preferences.SaveExportActivity
import de.radicalfishgames.crosscode.preferences.SaveImportActivity
import de.radicalfishgames.crosscode.preferences.SaveImportManager

class LaunchActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLaunchBinding
    private var guideBinding: GuideLayoutBinding? = null
    private lateinit var gameFolderManager: GameFolderManager

    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityLaunchBinding.inflate(layoutInflater)
        setContentView(binding.root)

        gameFolderManager = GameFolderManager(this)

        // Clear any leftover import queue from previous session
        if (savedInstanceState == null) {
            SaveImportManager(this).clear()
        }

        binding.launchButton.setOnClickListener {
            launchGameActivity()
        }

        binding.preferencesButton.setOnClickListener {
            launchPreferencesActivity()
        }

        binding.importSavesButton.setOnClickListener {
            launchSaveImportActivity()
        }

        binding.exportSavesButton.setOnClickListener {
            launchSaveExportActivity()
        }

        binding.aboutButton.setOnClickListener {
            launchAboutActivity()
        }
    }

    override fun onResume() {
        super.onResume()

        handler.post {
            tryToAccessInstance()
        }
    }

    private fun tryToAccessInstance(showDialogOnFailure: Boolean = false) {
        Log.i("CrossCode", "Attempting to access the CrossCode instance...")

        val gameDir = gameFolderManager.getGameDir()
        if (gameDir == null) {
            Log.e("CrossCode", "External storage not available")
            Toast.makeText(this, R.string.error_external_storage_unavailable, Toast.LENGTH_LONG).show()
            return
        }

        Log.i("CrossCode", "Game directory: ${gameDir.absolutePath}")

        if (!gameFolderManager.hasValidGameFolder()) {
            Log.i("CrossCode", "Game folder not found")
            instanceNotFound(showDialogOnFailure, getString(R.string.error_game_folder_not_found, gameDir.absolutePath))
            return
        }

        if (!gameFolderManager.hasAssetsFolder()) {
            Log.i("CrossCode", "Assets folder not found in game folder")
            instanceNotFound(showDialogOnFailure, getString(R.string.error_assets_folder_not_found))
            return
        }

        instanceFound(showDialogOnFailure)
    }

    private fun instanceFound(showDialogOnFailure: Boolean) {
        val hasModLoader = gameFolderManager.findModLoaderEntry() != null

        if (!gameFolderManager.exists("assets/node-webkit.html") && !hasModLoader) {
            instanceNotFound(showDialogOnFailure, getString(R.string.error_ccloader_not_found))
        } else {
            gameFilesIntact()
        }
    }

    private fun gameFilesIntact() {
        Log.i("CrossCode", "Instance directory found!")

        binding.mountingProgressBar.visibility = GONE
        binding.launchButton.isEnabled = true

        // Hide guide if visible
        findViewById<ScrollView>(R.id.setup_container)?.visibility = GONE
        binding.launchContainer.visibility = VISIBLE
    }

    private fun instanceNotFound(
        showDialog: Boolean = false,
        message: String? = null,
    ) {
        if (showDialog && message != null) {
            AlertDialog
                .Builder(this)
                .setTitle(R.string.error_game_files_not_found_title)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, null)
                .show()
        }
        showCurrentGuide()
    }

    private fun showCurrentGuide() {
        Log.i("CrossCode", "Showing user guide on installing the instance directory.")

        val setupContainer: View
        if (findViewById<ScrollView>(R.id.setup_container) == null) {
            val inflatedView = binding.setupContainerStub.inflate()
            guideBinding = GuideLayoutBinding.bind(inflatedView)
            setupContainer = inflatedView
        } else {
            setupContainer = findViewById<ScrollView>(R.id.setup_container)
            if (guideBinding == null) {
                guideBinding = GuideLayoutBinding.bind(setupContainer)
            }
        }

        val guide = guideBinding!!

        binding.launchContainer.visibility = GONE
        setupContainer.visibility = VISIBLE

        // Show all steps
        guide.selectFolderTitleTv.visibility = VISIBLE
        guide.selectFolderTextTv.visibility = VISIBLE
        guide.installCcloaderTextTv.visibility = VISIBLE
        guide.copyInstanceTextTv.visibility = VISIBLE

        // Show the path in step 3
        val gamePath = gameFolderManager.getDisplayPath() ?: "Unknown"
        guide.selectedFolderLabelTv.visibility = VISIBLE
        guide.selectedFolderLabelTv.text = getString(R.string.selected_folder_label, gamePath)

        // Single button at the end
        guide.copyInstanceBtn.visibility = VISIBLE
        guide.copyInstanceBtn.setOnClickListener {
            handler.post {
                tryToAccessInstance(showDialogOnFailure = true)
            }
        }
    }

    private fun launchGameActivity() {
        val intent = Intent(this, GameActivity::class.java)
        intent.addFlags(FLAG_ACTIVITY_NEW_TASK or FLAG_ACTIVITY_CLEAR_TASK)
        startActivity(intent)
    }

    private fun launchPreferencesActivity() {
        val intent = Intent(this, PreferencesActivity::class.java)
        startActivity(intent)
    }

    private fun launchSaveImportActivity() {
        val intent = Intent(this, SaveImportActivity::class.java)
        startActivity(intent)
    }

    private fun launchSaveExportActivity() {
        val intent = Intent(this, SaveExportActivity::class.java)
        startActivity(intent)
    }

    private fun launchAboutActivity() {
        val intent = Intent(this, AboutActivity::class.java)
        startActivity(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }
}
