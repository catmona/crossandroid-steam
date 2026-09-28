package de.radicalfishgames.crosscode.moddb

import de.radicalfishgames.crosscode.GameFolderManager

enum class ModLoaderFlavor { CCLOADER2, CCLOADER3 }

object ModLoaderDetector {
    fun detect(gameFolderManager: GameFolderManager): ModLoaderFlavor? {
        val entry = gameFolderManager.findModLoaderEntry() ?: return null
        return if (entry.startsWith("ccloader3/")) ModLoaderFlavor.CCLOADER3 else ModLoaderFlavor.CCLOADER2
    }
}
