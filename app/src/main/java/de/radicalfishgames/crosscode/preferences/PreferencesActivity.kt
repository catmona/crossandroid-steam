package de.radicalfishgames.crosscode.preferences

import android.app.UiModeManager
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import com.google.android.material.appbar.MaterialToolbar
import de.radicalfishgames.crosscode.R
import de.radicalfishgames.crosscode.ui.applySystemBarInsetsAsPadding
import de.radicalfishgames.crosscode.ui.enableDarkEdgeToEdge

class PreferencesActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableDarkEdgeToEdge()

        setContentView(R.layout.actvitity_preferences)
        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener { finish() }
        findViewById<View>(R.id.preferences_placeholder).applySystemBarInsetsAsPadding(top = false)

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
