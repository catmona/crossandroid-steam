package de.radicalfishgames.crosscode.preferences

import android.content.Context
import android.content.SharedPreferences
import de.radicalfishgames.crosscode.appPreferences
import org.json.JSONArray

class SaveImportManager(
    context: Context,
) {
    private val prefs: SharedPreferences =
        appPreferences(context)

    companion object {
        private const val KEY_SAVES_TO_IMPORT = "saves_to_import"
    }

    fun addSave(content: String): SaveValidationResult {
        val result = SaveValidator.validate(content)
        if (result is SaveValidationResult.Valid) {
            val trimmed = content.trim()
            val saves = getSavesMutable()
            if (trimmed !in saves) {
                saves.add(trimmed)
                savesToPrefs(saves)
            }
        }
        return result
    }

    fun removeSave(index: Int) {
        val saves = getSavesMutable()
        if (index in saves.indices) {
            saves.removeAt(index)
            savesToPrefs(saves)
        }
    }

    fun getSaves(): List<String> = getSavesMutable()

    fun consumeAll(): List<String> {
        val saves = getSaves()
        clear()
        return saves
    }

    fun clear() {
        prefs.edit().remove(KEY_SAVES_TO_IMPORT).apply()
    }

    private fun getSavesMutable(): MutableList<String> {
        val json = prefs.getString(KEY_SAVES_TO_IMPORT, null) ?: return mutableListOf()
        return try {
            val array = JSONArray(json)
            (0 until array.length()).map { array.getString(it) }.toMutableList()
        } catch (e: Exception) {
            mutableListOf()
        }
    }

    private fun savesToPrefs(saves: List<String>) {
        val array = JSONArray(saves)
        prefs.edit().putString(KEY_SAVES_TO_IMPORT, array.toString()).apply()
    }
}
