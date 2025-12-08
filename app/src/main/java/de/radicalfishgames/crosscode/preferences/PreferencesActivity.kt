package de.radicalfishgames.crosscode.preferences

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import de.radicalfishgames.crosscode.R
import java.io.BufferedReader
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader

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

        val preferences: SharedPreferences =
            requireContext().getSharedPreferences("de.radicalfishgames.crosscode_preferences", Context.MODE_PRIVATE)

        var reader: BufferedReader? = null
        try {
            // open the user-picked file for reading:
            val inputStream: InputStream? = requireActivity().contentResolver.openInputStream(uri)
            // now read the content:
            reader = BufferedReader(InputStreamReader(inputStream))
            var line: String?
            val builder = StringBuilder()
            while (reader.readLine().also { line = it } != null) {
                builder.append(line)
            }
            // Do something with the content in
            preferences
                .edit()
                .putString("save_to_import", builder.toString())
                .apply()
        } catch (e: FileNotFoundException) {
            e.printStackTrace()
        } catch (e: IOException) {
            e.printStackTrace()
        } finally {
            if (reader != null) {
                try {
                    reader.close()
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        }
    }
}
