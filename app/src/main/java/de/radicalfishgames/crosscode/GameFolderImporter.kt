package de.radicalfishgames.crosscode

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import java.io.File
import java.io.IOException

/**
 * Copies a user-selected CrossCode folder (via the Storage Access Framework) into the app's own
 * files directory. Because the app itself writes the files, they are owned by the app uid and
 * are always readable - unlike folders moved into Android/data by adb or shell-based file managers.
 *
 * Uses DocumentsContract directly instead of DocumentFile: DocumentFile issues a separate content
 * query for every name/type lookup, which makes it several times slower on thousands of files.
 */
class GameFolderImporter(
    private val context: Context,
) {
    class NotAGameFolderException : IOException("Selected folder does not contain an 'assets' directory")

    /** @param movedAside the unreadable folder that was renamed out of the way, if any */
    class Result(
        val copied: Int,
        val movedAside: File?,
    )

    private class Entry(
        val uri: Uri,
        val name: String,
        val isDirectory: Boolean,
    ) {
        /** Filled by [readTree]; empty for files. */
        var children: List<Entry> = emptyList()
    }

    private val resolver = context.contentResolver

    /**
     * Imports the folder at [treeUri] into [target].
     *
     * - An unreadable [target] is renamed aside (we can't delete what we can't read).
     * - A readable [target] is deleted first; callers are expected to confirm this with the user.
     * - Files are copied into a temporary sibling directory and renamed into place on success.
     *
     * @param onProgress called with (copied, total, currentName); total is known after a quick count pass
     * @throws NotAGameFolderException if the selected folder has no `assets` subfolder
     * @throws IOException on any copy failure (the temp dir is cleaned up)
     */
    fun import(
        treeUri: Uri,
        target: File,
        onProgress: (copied: Int, total: Int, currentName: String) -> Unit,
    ): Result {
        val rootUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri))
        val rootEntries = listChildren(rootUri)
        if (rootEntries.none { it.isDirectory && it.name == "assets" }) {
            throw NotAGameFolderException()
        }

        // One query per directory, done once: the same tree is used for the count and the copy.
        val total = readTree(rootEntries)

        val parent = target.parentFile ?: throw IOException("Target has no parent directory")
        parent.mkdirs()
        val temp = File(parent, "${target.name}.importing")
        if (temp.exists()) temp.deleteRecursively()

        var copied = 0
        try {
            copyTree(rootEntries, temp) { name ->
                copied++
                onProgress(copied, total, name)
            }
        } catch (e: Exception) {
            temp.deleteRecursively()
            throw e
        }

        val movedAside = moveExistingAside(target)
        if (!temp.renameTo(target)) {
            temp.deleteRecursively()
            throw IOException("Failed to move imported folder into place")
        }
        Log.i("CrossCode", "Imported $copied files into ${target.absolutePath}")
        return Result(copied, movedAside)
    }

    /** Returns the folder an unreadable [target] was renamed to, or null if nothing was moved aside. */
    private fun moveExistingAside(target: File): File? {
        if (!target.exists()) return null
        if (target.canRead()) {
            if (!target.deleteRecursively()) {
                throw IOException("Failed to delete existing folder ${target.absolutePath}")
            }
            return null
        }
        // Unreadable: renaming only needs write access to the (app-owned) parent. The app can never
        // delete this folder itself (it can't list it), so the caller must tell the user about it.
        val aside = File(target.parentFile, "${target.name}_unreadable_${System.currentTimeMillis()}")
        if (!target.renameTo(aside)) {
            throw IOException("Failed to move unreadable folder ${target.absolutePath} out of the way")
        }
        Log.w("CrossCode", "Moved unreadable game folder to ${aside.absolutePath}")
        return aside
    }

    /** One content query per directory, returning all children with name and type. */
    private fun listChildren(dirUri: Uri): List<Entry> {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(dirUri, DocumentsContract.getDocumentId(dirUri))
        val projection =
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
            )
        val cursor =
            resolver.query(childrenUri, projection, null, null, null)
                ?: throw IOException("Failed to list $dirUri")
        return cursor.use {
            buildList {
                while (it.moveToNext()) {
                    // Provider-supplied name is used as a path segment: reject anything that could escape dest
                    val name: String? = it.getString(1)
                    if (name.isNullOrEmpty() || name == "." || name == ".." || name.contains('/') || name.contains('\u0000')) {
                        throw IOException("Refusing to copy entry with invalid name: $name")
                    }
                    add(
                        Entry(
                            uri = DocumentsContract.buildDocumentUriUsingTree(dirUri, it.getString(0)),
                            name = name,
                            isDirectory = it.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR,
                        ),
                    )
                }
            }
        }
    }

    /** Recursively lists all directories in [entries], filling [Entry.children]. Returns the file count. */
    private fun readTree(entries: List<Entry>): Int =
        entries.sumOf { entry ->
            if (entry.isDirectory) {
                entry.children = listChildren(entry.uri)
                readTree(entry.children)
            } else {
                1
            }
        }

    private fun copyTree(
        entries: List<Entry>,
        dest: File,
        onFile: (name: String) -> Unit,
    ) {
        if (!dest.mkdirs() && !dest.isDirectory) {
            throw IOException("Failed to create directory ${dest.absolutePath}")
        }
        for (entry in entries) {
            val childDest = File(dest, entry.name)
            if (entry.isDirectory) {
                copyTree(entry.children, childDest, onFile)
            } else {
                val input =
                    resolver.openInputStream(entry.uri)
                        ?: throw IOException("Failed to read ${entry.name}")
                input.use { inp ->
                    childDest.outputStream().use { out -> inp.copyTo(out, COPY_BUFFER_SIZE) }
                }
                onFile(entry.name)
            }
        }
    }

    private companion object {
        const val COPY_BUFFER_SIZE = 256 * 1024
    }
}
