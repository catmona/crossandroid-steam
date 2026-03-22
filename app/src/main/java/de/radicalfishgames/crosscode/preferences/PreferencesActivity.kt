package de.radicalfishgames.crosscode.preferences

import android.app.UiModeManager
import android.content.res.Configuration
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import de.radicalfishgames.crosscode.R

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

class PreferenceFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        setPreferencesFromResource(R.xml.preferences, rootKey)

        val uiModeManager = requireContext().getSystemService(UiModeManager::class.java)
        if (uiModeManager.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION) {
            preferenceScreen.removePreferenceRecursively("pref_category_overlay_controls")
            preferenceScreen.removePreferenceRecursively("vibration_on_rumble")
        }

        // Remove categories left empty after individual preference removals
        for (i in preferenceScreen.preferenceCount - 1 downTo 0) {
            val pref = preferenceScreen.getPreference(i)
            if (pref is PreferenceCategory && pref.preferenceCount == 0) {
                preferenceScreen.removePreference(pref)
            }
        }
    }
}
