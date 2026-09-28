package de.radicalfishgames.crosscode.moddb

import de.radicalfishgames.crosscode.GameFolderManager

enum class ModLoaderFlavor { CCLOADER2, CCLOADER3 }

data class DetectedModLoader(
    val flavor: ModLoaderFlavor,
    val entryPath: String,
)

object ModLoaderDetector {
    fun detect(gameFolderManager: GameFolderManager): DetectedModLoader? {
        val entry = gameFolderManager.findModLoaderEntry() ?: return null
        val flavor = if (entry.startsWith("ccloader3/")) ModLoaderFlavor.CCLOADER3 else ModLoaderFlavor.CCLOADER2
        return DetectedModLoader(flavor, entry)
    }
}
