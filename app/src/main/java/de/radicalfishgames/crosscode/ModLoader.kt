package de.radicalfishgames.crosscode

enum class ModLoaderFlavor(
    val displayName: String,
) {
    CCLOADER2("CCLoader2"),
    CCLOADER3("CCLoader3"),
}

/** An installed modloader: its [dir] relative to the game folder and the [entry] page to load. */
data class ModLoader(
    val flavor: ModLoaderFlavor,
    val dir: String,
    val entry: String,
)
