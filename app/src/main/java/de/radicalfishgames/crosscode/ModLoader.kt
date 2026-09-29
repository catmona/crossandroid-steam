package de.radicalfishgames.crosscode

enum class ModLoaderFlavor { CCLOADER2, CCLOADER3 }

/** An installed modloader: its [dir] relative to the game folder and the [entry] page to load. */
data class ModLoader(
    val flavor: ModLoaderFlavor,
    val dir: String,
    val entry: String,
)
