package de.radicalfishgames.crosscode.moddb

import de.radicalfishgames.crosscode.GameFolderManager
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.ZipInputStream

sealed class InstallProgress {
    data class Preparing(
        val mod: ModDbEntry,
    ) : InstallProgress()

    data class Downloading(
        val mod: ModDbEntry,
        val bytesRead: Long,
        val totalBytes: Long,
    ) : InstallProgress()

    data class Installing(
        val mod: ModDbEntry,
    ) : InstallProgress()

    data class Done(
        val mod: ModDbEntry,
    ) : InstallProgress()
}

class ModInstallException(
    message: String,
) : Exception(message)

/**
 * Port of `downloadAndInstallMod`/`installCCMod`/`installModZip` from upstream CCModManager's
 * `src/mod-installer.ts`. `.ccmod` files are saved unextracted, since `CcmodManager` serves them
 * on demand for both CCLoader2 and CCLoader3. `.zip` files are extracted, keeping only entries under
 * the `source` prefix.
 */
class ModInstaller(
    private val gameFolderManager: GameFolderManager,
    private val httpClient: OkHttpClient,
) {
    fun install(
        mod: ModDbEntry,
        onProgress: (InstallProgress) -> Unit,
    ) {
        onProgress(InstallProgress.Preparing(mod))

        val installation =
            mod.installation.firstOrNull { it.type == "zip" }
                ?: throw ModInstallException("Mod '${mod.name}' has no known installation method.")

        val modsDir =
            gameFolderManager.getFile("assets/mods")
                ?: throw ModInstallException("No game folder available.")
        modsDir.mkdirs()

        val request = Request.Builder().url(installation.url).build()
        val data =
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw ModInstallException("Failed to download '${mod.name}': HTTP ${response.code}")
                }
                val body = response.body ?: throw ModInstallException("Empty response downloading '${mod.name}'")
                val total = body.contentLength()
                downloadWithProgress(body.byteStream(), total) { read ->
                    onProgress(InstallProgress.Downloading(mod, read, total))
                }
            }

        if (!verifySha256(data, installation.sha256)) {
            throw ModInstallException("Downloaded file for '${mod.name}' failed checksum verification.")
        }

        onProgress(InstallProgress.Installing(mod))

        val modId = mod.id.replace(" ", "_")
        if (installation.url.endsWith(".ccmod")) {
            File(modsDir, "$modId.ccmod").writeBytes(data)
        } else {
            val source =
                installation.source
                    ?: throw ModInstallException("Mod '${mod.name}' is a .zip with no source field (database error).")
            extractZip(data, File(modsDir, modId), source)
        }

        onProgress(InstallProgress.Done(mod))
    }

    fun uninstall(localMod: LocalMod) {
        if (!localMod.file.deleteRecursively()) {
            throw ModInstallException("Failed to delete '${localMod.file.name}'.")
        }
    }

    private fun downloadWithProgress(
        input: InputStream,
        totalBytes: Long,
        onProgress: (bytesRead: Long) -> Unit,
    ): ByteArray {
        val buffer = ByteArray(DOWNLOAD_BUFFER_SIZE)
        val output = ByteArrayOutputStream()
        var read = 0L
        // Reporting every 8KB chunk triggers thousands of Compose recompositions and visibly slows
        // large downloads, so throttle.
        var lastReportAt = 0L
        input.use { stream ->
            while (true) {
                val n = stream.read(buffer)
                if (n == -1) break
                output.write(buffer, 0, n)
                read += n
                val now = System.currentTimeMillis()
                if (now - lastReportAt >= PROGRESS_REPORT_INTERVAL_MS) {
                    lastReportAt = now
                    onProgress(read)
                }
            }
        }
        onProgress(read)
        check(totalBytes < 0 || read == totalBytes) { "Download truncated" }
        return output.toByteArray()
    }

    private fun verifySha256(
        data: ByteArray,
        expectedHex: String,
    ): Boolean {
        val digest = MessageDigest.getInstance("SHA-256").digest(data)
        val hex = digest.joinToString("") { "%02x".format(it) }
        return hex.equals(expectedHex, ignoreCase = true)
    }

    private fun extractZip(
        data: ByteArray,
        destDir: File,
        source: String,
    ) {
        val normalizedSource = source.trim('/')
        ZipInputStream(data.inputStream()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val relative = relativeToSource(entry.name, normalizedSource)
                    if (relative != null && relative.isNotEmpty()) {
                        val outFile = File(destDir, relative)
                        if (!outFile.canonicalPath.startsWith(destDir.canonicalPath + File.separator)) {
                            throw ModInstallException("Zip entry escapes destination: ${entry.name}")
                        }
                        outFile.parentFile?.mkdirs()
                        outFile.outputStream().use { out -> zip.copyTo(out) }
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
    }

    private fun relativeToSource(
        entryName: String,
        source: String,
    ): String? {
        if (source.isEmpty()) return entryName
        val prefix = "$source/"
        if (!entryName.startsWith(prefix)) return null
        return entryName.removePrefix(prefix)
    }

    companion object {
        private const val DOWNLOAD_BUFFER_SIZE = 8192
        private const val PROGRESS_REPORT_INTERVAL_MS = 150L
    }
}
