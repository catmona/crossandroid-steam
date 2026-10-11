package de.radicalfishgames.crosscode.preferences

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.CircularProgressIndicator
import de.radicalfishgames.crosscode.R
import de.radicalfishgames.crosscode.ui.applySystemBarInsetsAsPadding
import de.radicalfishgames.crosscode.ui.enableDarkEdgeToEdge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FileOutputStream

class SaveExportActivity : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyText: TextView
    private lateinit var loadingProgress: CircularProgressIndicator

    private var saveSlots: List<String> = emptyList()
    private var pendingExportIndex: Int = -1

    companion object {
        private const val KEY_PENDING_INDEX = "pending_export_index"
    }

    private val createDocument =
        registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
            handleExportResult(uri)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableDarkEdgeToEdge()
        setContentView(R.layout.activity_save_export)
        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener { finish() }
        findViewById<View>(R.id.content).applySystemBarInsetsAsPadding(top = false)

        recyclerView = findViewById(R.id.saves_recycler_view)
        emptyText = findViewById(R.id.empty_text)
        loadingProgress = findViewById(R.id.loading_progress)

        recyclerView.layoutManager = LinearLayoutManager(this)

        if (savedInstanceState != null) {
            pendingExportIndex = savedInstanceState.getInt(KEY_PENDING_INDEX, -1)
        }

        loadSaveSlots()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_PENDING_INDEX, pendingExportIndex)
    }

    private fun loadSaveSlots() {
        loadingProgress.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE
        emptyText.visibility = View.GONE

        lifecycleScope.launch {
            val slots =
                withContext(Dispatchers.IO) {
                    LocalStorageLevelDbReader(this@SaveExportActivity).readSaveSlots()
                }

            loadingProgress.visibility = View.GONE

            if (slots == null) {
                showError(getString(R.string.export_error_read_failed))
                emptyText.visibility = View.VISIBLE
            } else if (slots.isEmpty()) {
                emptyText.visibility = View.VISIBLE
            } else {
                saveSlots = slots
                showSlots()
            }
        }
    }

    private fun showSlots() {
        recyclerView.adapter =
            SaveExportAdapter(saveSlots) { index ->
                exportSlot(index)
            }
        recyclerView.visibility = View.VISIBLE
        emptyText.visibility = View.GONE
    }

    private fun exportSlot(index: Int) {
        pendingExportIndex = index
        createDocument.launch("crosscode_slot${index + 1}.txt")
    }

    private fun handleExportResult(uri: Uri?) {
        val index = pendingExportIndex
        if (index < 0 || index >= saveSlots.size) return

        if (uri != null) {
            try {
                contentResolver.openFileDescriptor(uri, "w")?.use { pfd ->
                    FileOutputStream(pfd.fileDescriptor).use { stream ->
                        stream.write(saveSlots[index].toByteArray())
                        stream.flush()
                    }
                    pfd.fileDescriptor.sync()
                }
            } catch (e: Exception) {
                showError(getString(R.string.export_error_write_failed, e.message ?: e.javaClass.simpleName))
            }
        }

        pendingExportIndex = -1
    }

    private fun showError(message: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.export_error_title)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private class SaveExportAdapter(
        private val saves: List<String>,
        private val onExport: (Int) -> Unit,
    ) : RecyclerView.Adapter<SaveExportAdapter.ViewHolder>() {
        class ViewHolder(
            view: View,
        ) : RecyclerView.ViewHolder(view) {
            val slotNumber: TextView = view.findViewById(R.id.slot_number)
            val saveStats: TextView = view.findViewById(R.id.save_stats)
            val saveLocation: TextView = view.findViewById(R.id.save_location)
            val exportButton: Button = view.findViewById(R.id.export_button)
        }

        override fun onCreateViewHolder(
            parent: ViewGroup,
            viewType: Int,
        ): ViewHolder {
            val view =
                LayoutInflater
                    .from(parent.context)
                    .inflate(R.layout.item_save_export, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(
            holder: ViewHolder,
            position: Int,
        ) {
            val save = saves[position]
            holder.slotNumber.text = "${position + 1}."

            val (stats, location) = SaveParser.summary(save)
            holder.saveStats.text = stats
            holder.saveLocation.text = location

            holder.exportButton.setOnClickListener {
                onExport(holder.bindingAdapterPosition)
            }
        }

        override fun getItemCount(): Int = saves.size
    }
}
