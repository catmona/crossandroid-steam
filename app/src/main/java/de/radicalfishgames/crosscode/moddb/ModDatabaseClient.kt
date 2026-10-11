package de.radicalfishgames.crosscode.moddb

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException

/**
 * Fetches and parses CCModDB's mod database, the same data upstream CCModManager's online tab reads
 * (see `CCDirectLink/CCModManager`'s `src/moddb.ts`/`src/cache.ts`).
 */
class ModDatabaseClient(
    private val httpClient: OkHttpClient,
) {
    /** Throws [IOException] on network failure or malformed response. */
    fun fetchDatabase(): Map<String, ModDbEntry> {
        val request = Request.Builder().url(STABLE_DATABASE_URL).build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Failed to fetch mod database: HTTP ${response.code}")
            }
            val body = response.body.string()
            return parseDatabase(body)
        }
    }

    private fun parseDatabase(json: String): Map<String, ModDbEntry> {
        val root = JSONObject(json)
        val result = mutableMapOf<String, ModDbEntry>()
        for (id in root.keys()) {
            if (id in VirtualPackages.NOT_DOWNLOADABLE) continue
            val raw = root.opt(id) as? JSONObject ?: continue
            val entry = parseEntry(id, raw) ?: continue
            result[id] = entry
        }
        return result
    }

    private fun parseEntry(
        id: String,
        raw: JSONObject,
    ): ModDbEntry? {
        val metadata = raw.optJSONObject("metadataCCMod") ?: return null

        val dependencies = mutableMapOf<String, String>()
        metadata.optJSONObject("dependencies")?.let { deps ->
            for (depId in deps.keys()) {
                deps.optString(depId).takeIf { it.isNotEmpty() }?.let { dependencies[depId] = it }
            }
        }

        val tags = mutableListOf<String>()
        metadata.optJSONArray("tags")?.let { arr ->
            for (i in 0 until arr.length()) tags.add(arr.getString(i))
        }

        val installations = mutableListOf<ModInstallation>()
        raw.optJSONArray("installation")?.let { arr ->
            for (i in 0 until arr.length()) {
                val inst = arr.optJSONObject(i) ?: continue
                val sha256 = inst.optJSONObject("hash")?.optString("sha256")?.takeIf { it.isNotEmpty() } ?: continue
                installations.add(
                    ModInstallation(
                        type = inst.optString("type"),
                        url = inst.optString("url"),
                        sha256 = sha256,
                        source = if (inst.has("source")) inst.optString("source") else null,
                    ),
                )
            }
        }

        return ModDbEntry(
            id = id,
            name = stripCrossCodeMarkup(localizedText(metadata.opt("title")) ?: id),
            description = stripCrossCodeMarkup(localizedText(metadata.opt("description")) ?: ""),
            version = metadata.optString("version"),
            authors = stringOrStringList(metadata.opt("authors")),
            dependencies = dependencies,
            tags = tags,
            stars = raw.optInt("stars"),
            lastUpdateTimestamp = raw.optLong("lastUpdateTimestamp"),
            installation = installations,
        )
    }

    /** `title`/`description` are either a plain string or a `{locale: text}` object (upstream `ig.LangLabel`). */
    private fun localizedText(value: Any?): String? =
        when (value) {
            is String -> {
                value
            }

            is JSONObject -> {
                value.optString("en_US").takeIf { it.isNotEmpty() }
                    ?: value
                        .keys()
                        .asSequence()
                        .firstOrNull()
                        ?.let { value.optString(it) }
            }

            else -> {
                null
            }
        }

    /**
     * Strips CrossCode's inline rich-text codes (e.g. `\i[class-tri]`, `\c[green]`) that some mod
     * texts embed, since plain widgets would print them literally.
     */
    private fun stripCrossCodeMarkup(text: String): String = text.replace(CROSSCODE_MARKUP_REGEX, "").replace(Regex(" {2,}"), " ").trim()

    private fun stringOrStringList(value: Any?): List<String> =
        when (value) {
            is String -> listOf(value)
            is org.json.JSONArray -> (0 until value.length()).map { value.getString(it) }
            else -> listOf("unknown")
        }

    companion object {
        const val STABLE_DATABASE_URL =
            "https://raw.githubusercontent.com/CCDirectLink/CCModDB/stable/npDatabase.min.json"

        private val CROSSCODE_MARKUP_REGEX = Regex("""\\[a-zA-Z]\[[^\]]*\]""")
    }
}
