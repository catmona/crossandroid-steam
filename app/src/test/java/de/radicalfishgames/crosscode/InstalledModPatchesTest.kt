package de.radicalfishgames.crosscode

import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Runs every `.json.patch` of a real mods folder through [PatchStepsApplier] and prints each
 * failure with its reason. Skipped unless MODTEST_TREE names a copy of the game's assets folder
 * (holding `mods/` and the patched game files), e.g. pulled from a device.
 */
class InstalledModPatchesTest {
    @Test
    fun reportPatchFailures() {
        val treePath = System.getenv("MODTEST_TREE")
        assumeTrue("MODTEST_TREE not set", !treePath.isNullOrEmpty())
        val tree = File(treePath!!)
        val mods = File(tree, "mods").listFiles { f -> f.isDirectory }!!.sortedBy { it.name }

        // Mod assets override game files, as in the app's asset index.
        fun findAsset(path: String): File? =
            mods.map { File(it, "assets/$path") }.firstOrNull { it.isFile }
                ?: File(tree, path).takeIf { it.isFile }

        // Installed mod ids, as CcmodManager reads them
        val modIds =
            mods.mapNotNull { mod ->
                listOf("ccmod.json" to "id", "package.json" to "name").firstNotNullOfOrNull { (file, key) ->
                    File(mod, file).takeIf { it.isFile }?.let { org.json.JSONObject(it.readText()).optString(key).ifEmpty { null } }
                }
            }.toSet()
        println("MODTEST ids=$modIds")

        var applied = 0
        val failures = mutableListOf<String>()
        for (mod in mods) {
            val assets = File(mod, "assets")
            if (!assets.isDirectory) continue
            assets.walkTopDown().filter { it.isFile && it.name.endsWith(".json.patch") }.forEach { patchFile ->
                val target = patchFile.relativeTo(assets).invariantSeparatorsPath.removeSuffix(".patch")
                val base = findAsset(target)
                if (base == null) {
                    failures += "${mod.name}: $target - no base file"
                    return@forEach
                }
                val resolver: FileResolver = { path ->
                    val colon = path.indexOf(':')
                    val proto = path.substring(0, colon)
                    val rel = path.substring(colon + 1).trimStart('/')
                    when (proto) {
                        "mod" -> File(mod, rel).takeIf { it.isFile }?.readText()
                        "game" -> findAsset(rel)?.readText()
                        else -> null
                    }
                }
                try {
                    PatchStepsApplier.applyOrThrow(base.readText(), patchFile.readText(), resolver, modIds)
                    applied++
                } catch (e: Exception) {
                    failures += "${mod.name}: $target - ${e::class.simpleName}: ${e.message}"
                }
            }
        }
        println("MODTEST applied=$applied failed=${failures.size}")
        failures.forEach { println("MODTEST FAIL $it") }
    }
}
