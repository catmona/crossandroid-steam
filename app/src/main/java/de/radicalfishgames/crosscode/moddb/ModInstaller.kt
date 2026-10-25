package de.radicalfishgames.crosscode.moddb

import de.radicalfishgames.crosscode.GameFolderManager
import de.radicalfishgames.crosscode.isPathInside
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.ZipInputStream

sealed class InstallProgress {
    data class Preparing(
        val name: String,
    ) : InstallProgress()

    data class Downloading(
        val name: String,
        val bytesRead: Long,
        val totalBytes: Long,
    ) : InstallProgress()

    data class Installing(
        val name: String,
    ) : InstallProgress()

    data class Done(
        val name: String,
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
    /** Installs [mod], replacing [existing] (the currently installed copies, if any). */
    fun install(
        mod: ModDbEntry,
        existing: LocalMod?,
        onProgress: (InstallProgress) -> Unit,
    ) {
        onProgress(InstallProgress.Preparing(mod.name))

        val installation =
            mod.installation.firstOrNull { it.type == "zip" }
                ?: throw ModInstallException("Mod '${mod.name}' has no known installation method.")

        val modsDir =
            gameFolderManager.getFile("assets/mods")
                ?: throw ModInstallException("No game folder available.")
        modsDir.mkdirs()

        val data = download(mod.name, installation.url, installation.sha256, onProgress)

        onProgress(InstallProgress.Installing(mod.name))

        // Build the new copy next to assets/mods (same volume, so the final rename is atomic) but
        // outside it, so a half-written copy is never loaded as a mod
        val staging = File(modsDir.parentFile, STAGING_NAME).apply { deleteRecursively() }
        try {
            val modId = mod.id.replace(" ", "_")
            val target =
                if (installation.url.endsWith(".ccmod")) {
                    staging.writeBytes(data)
                    File(modsDir, "$modId.ccmod")
                } else {
                    val source =
                        installation.source
                            ?: throw ModInstallException("Mod '${mod.name}' is a .zip with no source field (database error).")
                    extractZip(data, staging, source)
                    if (!staging.exists()) {
                        throw ModInstallException("Download for '${mod.name}' has no files under '$source'.")
                    }
                    File(modsDir, modId)
                }

            // Remove the old copy, which may differ in name or format, so none of its files survive
            existing?.let { uninstall(it) }
            if (target.exists() && !target.deleteRecursively()) {
                throw ModInstallException("Failed to replace '${target.name}'.")
            }
            if (!staging.renameTo(target)) {
                throw ModInstallException("Failed to move '${mod.name}' into place.")
            }
        } finally {
            staging.deleteRecursively()
        }

        onProgress(InstallProgress.Done(mod.name))
    }

    /**
     * Extracts [release] over the game folder, merging into existing directories such as
     * `assets/mods/`. A single top-level folder wrapping the whole archive (GitHub source zips) is
     * stripped. A bundled mod only replaces an installed one if it's newer: mods are updated
     * separately, so the bundled copy may be older (e.g. CCLoader3 bundles an old CCModManager).
     */
    fun installModLoader(
        name: String,
        release: LoaderRelease,
        onProgress: (InstallProgress) -> Unit,
    ) {
        onProgress(InstallProgress.Preparing(name))
        val gameDir = gameFolderManager.getGameDir() ?: throw ModInstallException("No game folder available.")
        val data = download(name, release.url, release.sha256, onProgress)

        onProgress(InstallProgress.Installing(name))
        // Extract fully before touching the game folder, so a bad archive leaves it unchanged
        val staging = File(gameDir.parentFile, STAGING_NAME).apply { deleteRecursively() }
        try {
            extractZip(data, staging, singleTopLevelDir(data).orEmpty())
            val entries = staging.listFiles()
            if (entries.isNullOrEmpty()) throw ModInstallException("Download for '$name' is empty.")
            keepNewerInstalledMods(File(staging, "assets/mods"))
            entries.forEach { moveMerging(it, File(gameDir, it.name)) }
        } finally {
            staging.deleteRecursively()
        }

        onProgress(InstallProgress.Done(name))
    }

    fun uninstall(localMod: LocalMod) {
        val failed = localMod.files.filterNot { it.deleteRecursively() }
        if (failed.isNotEmpty()) {
            throw ModInstallException("Failed to delete ${failed.joinToString { "'${it.name}'" }}.")
        }
    }

    /** Downloads [url], verifying it against [sha256] unless that's null. */
    private fun download(
        name: String,
        url: String,
        sha256: String?,
        onProgress: (InstallProgress) -> Unit,
    ): ByteArray {
        val request = Request.Builder().url(url).build()
        val data =
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw ModInstallException("Failed to download '$name': HTTP ${response.code}")
                }
                val body = response.body
                val total = body.contentLength()
                downloadWithProgress(body.byteStream(), total) { read ->
                    onProgress(InstallProgress.Downloading(name, read, total))
                }
            }
        if (sha256 != null && !verifySha256(data, sha256)) {
            throw ModInstallException("Downloaded file for '$name' failed checksum verification.")
        }
        return data
    }

    /** The folder every entry of the zip [data] lives in, or null if there's more than one top-level entry. */
    private fun singleTopLevelDir(data: ByteArray): String? {
        var top: String? = null
        ZipInputStream(data.inputStream()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.name.contains('/')) return null
                val first = entry.name.substringBefore('/')
                if (top != null && top != first) return null
                top = first
                entry = zip.nextEntry
            }
        }
        return top
    }

    /**
     * Deletes the mods in [bundledDir] whose installed copy is the same version or newer, and
     * uninstalls the installed copies of the rest (they may differ in name or format).
     */
    private fun keepNewerInstalledMods(bundledDir: File) {
        val scanner = LocalModScanner(gameFolderManager)
        val installed = scanner.scan()
        for (bundled in scanner.scan(bundledDir.listFiles()?.toList().orEmpty()).values) {
            val existing = installed[bundled.id] ?: continue
            if (bundled.version > existing.version) {
                uninstall(existing)
            } else {
                bundled.files.forEach { it.deleteRecursively() }
            }
        }
    }

    /** Moves [source] to [target], merging directories into existing ones and replacing files. */
    private fun moveMerging(
        source: File,
        target: File,
    ) {
        if (source.isDirectory && target.isDirectory) {
            source.listFiles()?.forEach { moveMerging(it, File(target, it.name)) }
            return
        }
        if (target.exists() && !target.deleteRecursively()) {
            throw ModInstallException("Failed to replace '${target.name}'.")
        }
        if (!source.renameTo(target)) {
            throw ModInstallException("Failed to move '${source.name}' into place.")
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
                        if (!isPathInside(outFile.canonicalPath, destDir.canonicalPath)) {
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
        private const val STAGING_NAME = ".crossandroid-mod-install"
        private const val PROGRESS_REPORT_INTERVAL_MS = 150L
    }
}
