package de.radicalfishgames.crosscode.preferences

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
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

// For whatever reason AndroidX _requires_ us to use a fragment, even though it could just have the whole activity
class PreferenceFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)
    }

}