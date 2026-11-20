package de.radicalfishgames.crosscode

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import java.io.File

/**
 * True if [path] is strictly inside [dir]. Both must be canonical paths, so ".." and symlinks
 * can't be used to escape [dir].
 */
fun isPathInside(
    path: String,
    dir: String,
): Boolean = path.startsWith(dir + File.separator)

/**
 * The settings screen's preferences: PreferenceManager's default file, named after the package
 * ("<applicationId>_preferences"), so it follows the application id instead of a hard-coded name.
 */
fun appPreferences(context: Context): SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
