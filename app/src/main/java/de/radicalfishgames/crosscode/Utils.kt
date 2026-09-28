package de.radicalfishgames.crosscode

import java.io.File

/**
 * True if [path] is strictly inside [dir]. Both must be canonical paths, so ".." and symlinks
 * can't be used to escape [dir].
 */
fun isPathInside(
    path: String,
    dir: String,
): Boolean = path.startsWith(dir + File.separator)
