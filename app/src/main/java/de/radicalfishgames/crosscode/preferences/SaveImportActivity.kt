package de.radicalfishgames.crosscode.preferences

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import de.radicalfishgames.crosscode.R
import de.radicalfishgames.crosscode.ui.applySystemBarInsetsAsPadding
import de.radicalfishgames.crosscode.ui.enableDarkEdgeToEdge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SaveImportActivity : AppCompatActivity() {
    private lateinit var manager: SaveImportManager
    private lateinit var adapter: SavesAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyText: TextView

    private val openMultipleDocuments =
        registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            handleFilesSelected(uris)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableDarkEdgeToEdge()
        setContentView(R.layout.activity_save_import)
        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener { finish() }
        findViewById<View>(R.id.content).applySystemBarInsetsAsPadding(top = false)

        manager = SaveImportManager(this)
        adapter =
            SavesAdapter(manager.getSaves()) { index ->
                manager.removeSave(index)
                refreshList()
            }

        recyclerView = findViewById(R.id.saves_recycler_view)
        emptyText = findViewById(R.id.empty_text)

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        findViewById<View>(R.id.import_string_button).setOnClickListener {
            showImportStringDialog()
        }

        findViewById<View>(R.id.import_files_button).setOnClickListener {
            openMultipleDocuments.launch(arrayOf("text/plain", "*/*"))
        }

        updateEmptyState()
    }

    private fun showImportStringDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_import_string, null)
        val editText = view.findViewById<EditText>(R.id.import_string_edit)

        MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_App_Dialog_FilledPositive)
            .setTitle(R.string.import_string_dialog_title)
            .setView(view)
            .setPositiveButton(R.string.import_string_dialog_import) { _, _ ->
                val content = editText.text.toString()
                if (content.isNotBlank()) {
                    when (val result = manager.addSave(content)) {
                        is SaveValidationResult.Valid -> refreshList()
                        is SaveValidationResult.Invalid -> showError(getString(result.reasonResId))
                    }
                }
            }.setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun handleFilesSelected(uris: List<Uri>) {
        if (uris.isEmpty()) return

        lifecycleScope.launch {
            val errors = mutableListOf<String>()

            for (uri in uris) {
                val content =
                    withContext(Dispatchers.IO) {
                        readFileContent(uri)
                    }

                if (content == null) {
                    errors.add(getString(R.string.import_error_read_failed))
                    continue
                }

                when (val result = manager.addSave(content)) {
                    is SaveValidationResult.Valid -> {}

                    is SaveValidationResult.Invalid -> {
                        errors.add(getString(result.reasonResId))
                    }
                }
            }

            refreshList()

            if (errors.isNotEmpty()) {
                showError(errors.distinct().joinToString("\n"))
            }
        }
    }

    private fun readFileContent(uri: Uri): String? =
        try {
            contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        } catch (e: Exception) {
            null
        }

    private fun refreshList() {
        adapter.updateData(manager.getSaves())
        updateEmptyState()
    }

    private fun updateEmptyState() {
        val isEmpty = adapter.itemCount == 0
        emptyText.visibility = if (isEmpty) View.VISIBLE else View.GONE
        recyclerView.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }

    private fun showError(message: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.import_error_title)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private class SavesAdapter(
        private var saves: List<String>,
        private val onDelete: (Int) -> Unit,
    ) : RecyclerView.Adapter<SavesAdapter.ViewHolder>() {
        class ViewHolder(
            view: View,
        ) : RecyclerView.ViewHolder(view) {
            val indexText: TextView = view.findViewById(R.id.save_index)
            val saveStats: TextView = view.findViewById(R.id.save_stats)
            val saveLocation: TextView = view.findViewById(R.id.save_location)
            val deleteButton: View = view.findViewById(R.id.delete_button)
        }

        override fun onCreateViewHolder(
            parent: ViewGroup,
            viewType: Int,
        ): ViewHolder {
            val view =
                LayoutInflater
                    .from(parent.context)
                    .inflate(R.layout.item_save_import, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(
            holder: ViewHolder,
            position: Int,
        ) {
            val save = saves[position]
            holder.indexText.text = "${position + 1}."

            val (stats, location) = SaveParser.summary(save)
            holder.saveStats.text = stats
            holder.saveLocation.text = location

            holder.deleteButton.setOnClickListener {
                onDelete(holder.bindingAdapterPosition)
            }
        }

        override fun getItemCount(): Int = saves.size

        fun updateData(newSaves: List<String>) {
            saves = newSaves
            notifyDataSetChanged()
        }
    }
}
