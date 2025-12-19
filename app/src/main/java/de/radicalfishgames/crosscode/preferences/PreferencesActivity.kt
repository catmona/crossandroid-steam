package de.radicalfishgames.crosscode.preferences

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import de.radicalfishgames.crosscode.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PreferencesActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.actvitity_preferences)

        supportFragmentManager
            .beginTransaction()
            .replace(R.id.preferences_placeholder, PreferenceFragment())
            .commit()
    }
}

// For whatever reason AndroidX _requires_ us to use a fragment, even though it could just have the whole activity
class PreferenceFragment : PreferenceFragmentCompat() {
    private val getContentLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            handleFilePickerResult(uri)
        }

    override fun onCreatePreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        setPreferencesFromResource(R.xml.preferences, rootKey)
        val filePicker = findPreference("fileToImport") as Preference?
        filePicker!!.onPreferenceClickListener =
            Preference.OnPreferenceClickListener {
                getContentLauncher.launch("text/plain")
                true
            }
    }

    private fun handleFilePickerResult(uri: Uri?) {
        if (uri == null) return

        lifecycleScope.launch {
            val content =
                withContext(Dispatchers.IO) {
                    readFileContent(uri)
                }

            if (content != null) {
                requireContext()
                    .getSharedPreferences("de.radicalfishgames.crosscode_preferences", Context.MODE_PRIVATE)
                    .edit()
                    .putString("save_to_import", content)
                    .apply()
            }
        }
    }

    private fun readFileContent(uri: Uri): String? =
        try {
            requireActivity().contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
                reader.readText()
            }
        } catch (e: Exception) {
            Log.e("PreferenceFragment", "Failed to read file", e)
            null
        }
}
