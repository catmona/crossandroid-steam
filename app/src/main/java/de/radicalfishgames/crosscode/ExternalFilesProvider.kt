package de.radicalfishgames.crosscode

import android.database.Cursor
import android.database.MatrixCursor
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsProvider
import android.webkit.MimeTypeMap
import java.io.File
import java.io.FileNotFoundException

class ExternalFilesProvider : DocumentsProvider() {
    companion object {
        private const val ROOT_ID = "crosscode"
        private const val ROOT_DOC_ID = "root"

        private val DEFAULT_ROOT_PROJECTION =
            arrayOf(
                DocumentsContract.Root.COLUMN_ROOT_ID,
                DocumentsContract.Root.COLUMN_MIME_TYPES,
                DocumentsContract.Root.COLUMN_FLAGS,
                DocumentsContract.Root.COLUMN_ICON,
                DocumentsContract.Root.COLUMN_TITLE,
                DocumentsContract.Root.COLUMN_SUMMARY,
                DocumentsContract.Root.COLUMN_DOCUMENT_ID,
                DocumentsContract.Root.COLUMN_AVAILABLE_BYTES,
            )

        private val DEFAULT_DOCUMENT_PROJECTION =
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED,
                DocumentsContract.Document.COLUMN_FLAGS,
                DocumentsContract.Document.COLUMN_SIZE,
            )
    }

    private val baseDir: File
        get() = context!!.getExternalFilesDir(null)!!

    override fun onCreate(): Boolean = true

    override fun queryRoots(projection: Array<out String>?): Cursor {
        val result = MatrixCursor(projection ?: DEFAULT_ROOT_PROJECTION)

        result.newRow().apply {
            add(DocumentsContract.Root.COLUMN_ROOT_ID, ROOT_ID)
            add(DocumentsContract.Root.COLUMN_TITLE, "CrossCode")
            add(DocumentsContract.Root.COLUMN_SUMMARY, "Game files")
            add(DocumentsContract.Root.COLUMN_ICON, R.drawable.lea_sprite)
            add(DocumentsContract.Root.COLUMN_DOCUMENT_ID, ROOT_DOC_ID)
            add(
                DocumentsContract.Root.COLUMN_FLAGS,
                DocumentsContract.Root.FLAG_SUPPORTS_CREATE or
                    DocumentsContract.Root.FLAG_SUPPORTS_IS_CHILD,
            )
            add(DocumentsContract.Root.COLUMN_MIME_TYPES, "*/*")
            add(DocumentsContract.Root.COLUMN_AVAILABLE_BYTES, baseDir.freeSpace)
        }

        return result
    }

    override fun queryDocument(
        documentId: String?,
        projection: Array<out String>?,
    ): Cursor {
        val result = MatrixCursor(projection ?: DEFAULT_DOCUMENT_PROJECTION)
        val file = getFileForDocId(documentId ?: ROOT_DOC_ID)
        includeFile(result, documentId ?: ROOT_DOC_ID, file)
        return result
    }

    override fun queryChildDocuments(
        parentDocumentId: String?,
        projection: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        val result = MatrixCursor(projection ?: DEFAULT_DOCUMENT_PROJECTION)
        val parent = getFileForDocId(parentDocumentId ?: ROOT_DOC_ID)

        parent.listFiles()?.forEach { file ->
            includeFile(result, getDocIdForFile(file), file)
        }

        return result
    }

    override fun openDocument(
        documentId: String?,
        mode: String?,
        signal: CancellationSignal?,
    ): ParcelFileDescriptor {
        val file = getFileForDocId(documentId ?: throw FileNotFoundException())
        val accessMode = ParcelFileDescriptor.parseMode(mode ?: "r")
        return ParcelFileDescriptor.open(file, accessMode)
    }

    override fun createDocument(
        parentDocumentId: String?,
        mimeType: String?,
        displayName: String?,
    ): String {
        val parent = getFileForDocId(parentDocumentId ?: ROOT_DOC_ID)
        val file = File(parent, displayName ?: "new_file")

        if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
            file.mkdirs()
        } else {
            file.createNewFile()
        }

        return getDocIdForFile(file)
    }

    override fun deleteDocument(documentId: String?) {
        val file = getFileForDocId(documentId ?: throw FileNotFoundException())
        if (file.isDirectory) {
            file.deleteRecursively()
        } else {
            file.delete()
        }
    }

    override fun renameDocument(
        documentId: String?,
        displayName: String?,
    ): String {
        val file = getFileForDocId(documentId ?: throw FileNotFoundException())
        val newFile = File(file.parentFile, displayName ?: file.name)
        file.renameTo(newFile)
        return getDocIdForFile(newFile)
    }

    override fun isChildDocument(
        parentDocumentId: String?,
        documentId: String?,
    ): Boolean {
        val parent = getFileForDocId(parentDocumentId ?: ROOT_DOC_ID)
        val child = getFileForDocId(documentId ?: return false)
        return child.absolutePath.startsWith(parent.absolutePath)
    }

    private fun getFileForDocId(documentId: String): File {
        if (documentId == ROOT_DOC_ID) {
            return baseDir
        }
        return File(baseDir, documentId)
    }

    private fun getDocIdForFile(file: File): String {
        val path = file.absolutePath
        val basePath = baseDir.absolutePath

        return if (path == basePath) {
            ROOT_DOC_ID
        } else {
            path.removePrefix("$basePath/")
        }
    }

    private fun includeFile(
        result: MatrixCursor,
        docId: String,
        file: File,
    ) {
        var flags = 0

        if (file.isDirectory) {
            flags = flags or DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE
        } else {
            flags = flags or DocumentsContract.Document.FLAG_SUPPORTS_WRITE
        }
        flags = flags or DocumentsContract.Document.FLAG_SUPPORTS_DELETE
        flags = flags or DocumentsContract.Document.FLAG_SUPPORTS_RENAME

        val mimeType =
            if (file.isDirectory) {
                DocumentsContract.Document.MIME_TYPE_DIR
            } else {
                getMimeType(file)
            }

        result.newRow().apply {
            add(DocumentsContract.Document.COLUMN_DOCUMENT_ID, docId)
            add(DocumentsContract.Document.COLUMN_DISPLAY_NAME, file.name)
            add(DocumentsContract.Document.COLUMN_SIZE, file.length())
            add(DocumentsContract.Document.COLUMN_MIME_TYPE, mimeType)
            add(DocumentsContract.Document.COLUMN_LAST_MODIFIED, file.lastModified())
            add(DocumentsContract.Document.COLUMN_FLAGS, flags)
        }
    }

    private fun getMimeType(file: File): String {
        val extension = file.extension.lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
            ?: "application/octet-stream"
    }
}
