package de.radicalfishgames.crosscode

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.format.Formatter
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import de.radicalfishgames.crosscode.databinding.ActivityLaunchBinding
import de.radicalfishgames.crosscode.databinding.GuideLayoutBinding
import de.radicalfishgames.crosscode.moddb.ModBrowserActivity
import de.radicalfishgames.crosscode.preferences.PreferencesActivity
import de.radicalfishgames.crosscode.preferences.SaveExportActivity
import de.radicalfishgames.crosscode.preferences.SaveImportActivity
import de.radicalfishgames.crosscode.preferences.SaveImportManager
import de.radicalfishgames.crosscode.ui.applySystemBarInsetsAsPadding
import de.radicalfishgames.crosscode.ui.enableDarkEdgeToEdge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class LaunchActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLaunchBinding
    private var guideBinding: GuideLayoutBinding? = null
    private lateinit var gameFolderManager: GameFolderManager

    private val handler = Handler(Looper.getMainLooper())

    private val openGameFolder =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) chooseImportTarget(uri)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableDarkEdgeToEdge()

        binding = ActivityLaunchBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarInsetsAsPadding()

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

        binding.browseModsButton.setOnClickListener {
            launchModBrowserActivity(fromController = !it.isInTouchMode)
        }
    }

    override fun onResume() {
        super.onResume()

        handler.post {
            tryToAccessInstance()
        }
    }

    override fun onKeyDown(
        keyCode: Int,
        event: KeyEvent,
    ): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BUTTON_START && binding.launchButton.isEnabled) {
            launchGameActivity()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun tryToAccessInstance(showDialogOnFailure: Boolean = false) {
        Log.i("CrossCode", "Attempting to access the CrossCode instance...")

        val gameDir = gameFolderManager.getGameDir()
        if (gameDir == null) {
            Log.e("CrossCode", "External storage not available")
            Snackbar.make(binding.root, R.string.error_external_storage_unavailable, Snackbar.LENGTH_LONG).show()
            return
        }

        Log.i("CrossCode", "Game directory: ${gameDir.absolutePath}")

        if (!gameFolderManager.hasValidGameFolder()) {
            val unreadable = gameFolderManager.findUnreadableGameFolder()
            if (unreadable != null) {
                Log.w("CrossCode", "Game folder exists but is not readable by the app: ${unreadable.absolutePath}")
                instanceNotFound(showDialogOnFailure, getString(R.string.error_game_folder_unreadable, unreadable.absolutePath))
                return
            }
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
        val hasModLoader = gameFolderManager.findModLoader() != null

        if (!gameFolderManager.exists("assets/node-webkit.html") && !hasModLoader) {
            instanceNotFound(showDialogOnFailure, getString(R.string.error_game_files_incomplete))
        } else {
            gameFilesIntact()
        }
    }

    private fun gameFilesIntact() {
        Log.i("CrossCode", "Instance directory found!")

        binding.mountingProgressBar.visibility = GONE
        binding.launchButton.isEnabled = true

        findViewById<ScrollView>(R.id.setup_container)?.visibility = GONE
        binding.launchContainer.visibility = VISIBLE

        binding.launchButton.requestFocus()
    }

    private fun instanceNotFound(
        showDialog: Boolean = false,
        message: String? = null,
    ) {
        if (showDialog && message != null) {
            MaterialAlertDialogBuilder(this)
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

        // Show the adb push target path so the full guide can be followed
        val gamePath = gameFolderManager.getDisplayPath() ?: "Unknown"
        guide.gameFolderPathTv.text = getString(R.string.game_folder_path, gamePath)

        guide.importInstanceBtn.setOnClickListener {
            try {
                openGameFolder.launch(null)
            } catch (e: ActivityNotFoundException) {
                // Android TV and some stripped ROMs ship without a DocumentsUI
                Snackbar.make(binding.root, R.string.import_instance_no_picker, Snackbar.LENGTH_LONG).show()
            }
        }

        guide.copyInstanceBtn.setOnClickListener {
            handler.post {
                tryToAccessInstance(showDialogOnFailure = true)
            }
        }
    }

    private fun chooseImportTarget(uri: Uri) {
        val targets = gameFolderManager.getCandidateGameDirs()
        when (targets.size) {
            0 -> {
                Snackbar.make(binding.root, R.string.error_external_storage_unavailable, Snackbar.LENGTH_LONG).show()
            }

            1 -> {
                confirmAndImportGameFolder(uri, targets[0])
            }

            else -> {
                val labels =
                    targets
                        .map { dir ->
                            val free = Formatter.formatFileSize(this, dir.parentFile?.freeSpace ?: 0)
                            getString(R.string.import_instance_target_entry, gameFolderManager.getVolumeLabel(dir), free)
                        }.toTypedArray()
                var selected = 0
                MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_App_Dialog_FilledPositive)
                    .setTitle(R.string.import_instance_target_title)
                    .setSingleChoiceItems(labels, selected) { _, which -> selected = which }
                    .setPositiveButton(R.string.import_instance_target_copy) { _, _ ->
                        confirmAndImportGameFolder(uri, targets[selected])
                    }.setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
        }
    }

    private fun confirmAndImportGameFolder(
        uri: Uri,
        target: File,
    ) {
        if (target.exists() && target.canRead()) {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.import_instance_replace_title)
                .setMessage(getString(R.string.import_instance_replace_message, target.absolutePath))
                .setPositiveButton(R.string.import_instance_replace_confirm) { _, _ -> importGameFolder(uri, target) }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        } else {
            importGameFolder(uri, target)
        }
    }

    private fun importGameFolder(
        uri: Uri,
        target: File,
    ) {
        val progressText =
            TextView(this).apply {
                // Match the M3 dialog content inset (24dp) so the text lines up with the title
                val density = resources.displayMetrics.density
                val horizontal = (24 * density).toInt()
                setPadding(horizontal, (8 * density).toInt(), horizontal, (24 * density).toInt())
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
                text = getString(R.string.import_instance_counting)
            }
        val progressDialog =
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.import_instance_progress_title)
                .setView(progressText)
                .setCancelable(false)
                .show()
        guideBinding?.importInstanceBtn?.isEnabled = false
        guideBinding?.copyInstanceBtn?.isEnabled = false

        lifecycleScope.launch {
            val result =
                withContext(Dispatchers.IO) {
                    runCatching {
                        GameFolderImporter(this@LaunchActivity).import(uri, target) { copied, total, name ->
                            // Throttle UI updates; posting for every file just floods the main thread
                            if (copied % 20 == 0 || copied == total) {
                                progressText.post {
                                    progressText.text = getString(R.string.import_instance_progress, copied, total, name)
                                }
                            }
                        }
                    }
                }

            progressDialog.dismiss()
            guideBinding?.importInstanceBtn?.isEnabled = true
            guideBinding?.copyInstanceBtn?.isEnabled = true

            result
                .onFailure { e ->
                    val message =
                        if (e is GameFolderImporter.NotAGameFolderException) {
                            getString(R.string.import_instance_error_not_game_folder)
                        } else {
                            Log.e("CrossCode", "Game folder import failed", e)
                            e.message ?: e.toString()
                        }
                    MaterialAlertDialogBuilder(this@LaunchActivity)
                        .setTitle(R.string.import_instance_error_title)
                        .setMessage(message)
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
                }.onSuccess { imported ->
                    val movedAside = imported.movedAside
                    if (movedAside != null) {
                        MaterialAlertDialogBuilder(this@LaunchActivity)
                            .setTitle(R.string.import_instance_moved_aside_title)
                            .setMessage(getString(R.string.import_instance_moved_aside_message, movedAside.absolutePath))
                            .setPositiveButton(android.R.string.ok) { _, _ -> tryToAccessInstance(showDialogOnFailure = true) }
                            .setCancelable(false)
                            .show()
                    } else {
                        tryToAccessInstance(showDialogOnFailure = true)
                    }
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

    private fun launchModBrowserActivity(fromController: Boolean) {
        val intent = Intent(this, ModBrowserActivity::class.java).putExtra(ModBrowserActivity.EXTRA_FROM_CONTROLLER, fromController)
        startActivity(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }
}
