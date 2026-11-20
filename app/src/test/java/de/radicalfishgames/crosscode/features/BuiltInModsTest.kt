package de.radicalfishgames.crosscode.features

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BuiltInModsTest {
    private val id = BuiltInMods.EXTENSION_ASSET_PRELOADER
    private val active = setOf(id)

    @Test
    fun servesTheModFolderAndItsFiles() {
        assertEquals("", BuiltInMods.serve("assets/mods/$id", active))
        assertEquals("", BuiltInMods.serve("assets/mods/$id/", active))
        val manifest = BuiltInMods.serve("assets/mods/$id/package.json", active)
        assertNotNull(manifest)
        assertTrue(manifest!!.contains("\"$id\""))
        assertNotNull(BuiltInMods.serve("assets/mods/$id/ccmod.json", active))
        assertTrue(BuiltInMods.serve("assets/mods/$id/postload.js", active)!!.contains("getFileForwardingAsJson"))
    }

    @Test
    fun acceptsPathsWithAndWithoutTheLoaderPrefix() {
        assertNotNull(BuiltInMods.serve("mods/$id/package.json", active))
        assertNotNull(BuiltInMods.serve("ccloader/assets/mods/$id/package.json", active))
    }

    @Test
    fun servesNothingWhenInactiveOrElsewhere() {
        assertNull(BuiltInMods.serve("assets/mods/$id/package.json", emptySet()))
        assertNull(BuiltInMods.serve("assets/mods/$id/unknown.js", active))
        assertNull(BuiltInMods.serve("assets/mods/other-mod/package.json", active))
        assertNull(BuiltInMods.serve("assets/data/$id/package.json", active))
        assertNull(BuiltInMods.serve("assets/node-webkit.html", active))
    }
}
