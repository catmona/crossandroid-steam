package de.radicalfishgames.crosscode.moddb

import de.radicalfishgames.crosscode.ModLoaderFlavor
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.Date

/** A downloadable modloader archive, extracted over the game folder. Null [sha256] skips verification. */
data class LoaderRelease(
    val version: String,
    val url: String,
    val sha256: String?,
)

/** GitHub's unauthenticated API limit (60 requests/hour per IP, shared by every device and app on the network) is used up until [resetAt]. */
class GitHubRateLimitException(
    val resetAt: Date,
) : IOException("GitHub API rate limit reached until $resetAt")

/**
 * Resolves where to download each modloader from `modloaders.json` in our repo, so a loader moving
 * hosts or renaming its files only needs that file changed, not an app update. Each entry is either
 * `{"type": "github", "repo": ..., "asset"?: regex}` (newest release, prereleases included) or
 * `{"type": "direct", "url": ..., "version": ..., "sha256"?: ...}`.
 */
class ModLoaderReleases(
    private val httpClient: OkHttpClient,
) {
    /**
     * Returns those of [flavors] the manifest lists, in [ModLoaderFlavor] order. A null value means
     * the entry's type is unknown to this app version. Throws [IOException] on network failure.
     */
    fun fetch(flavors: Collection<ModLoaderFlavor> = ModLoaderFlavor.entries): Map<ModLoaderFlavor, LoaderRelease?> {
        val manifest = JSONObject(get(MANIFEST_URL))
        return ModLoaderFlavor.entries
            .filter { it in flavors && manifest.has(it.name.lowercase()) }
            .associateWith { resolve(manifest.getJSONObject(it.name.lowercase())) }
    }

    private fun resolve(entry: JSONObject): LoaderRelease? =
        when (entry.optString("type")) {
            "github" -> resolveGithub(entry.getString("repo"), entry.optString("asset").takeIf { it.isNotEmpty() })
            "direct" -> LoaderRelease(entry.getString("version"), entry.getString("url"), entry.optString("sha256").takeIf { it.isNotEmpty() })
            else -> null
        }

    private fun resolveGithub(
        repo: String,
        assetPattern: String?,
    ): LoaderRelease {
        // Not /releases/latest: that skips prereleases, and CCLoader3 only publishes prereleases
        val release =
            JSONArray(get("https://api.github.com/repos/$repo/releases?per_page=1")).optJSONObject(0)
                ?: throw IOException("$repo has no releases.")
        val tag = release.getString("tag_name")
        // CCLoader2 tags are "v<ccloader>/v<bundled Simplify>", e.g. v2.26.1/v2.14.4
        val version = tag.substringBefore('/').removePrefix("v")

        if (assetPattern == null) return LoaderRelease(version, release.getString("zipball_url"), null)

        val regex = Regex(assetPattern)
        val assets = release.getJSONArray("assets")
        val asset =
            (0 until assets.length())
                .map { assets.getJSONObject(it) }
                .firstOrNull { regex.containsMatchIn(it.getString("name")) }
                ?: throw IOException("No file in $repo $tag matches '$assetPattern'.")
        val sha256 = asset.optString("digest").takeIf { it.startsWith("sha256:") }?.removePrefix("sha256:")
        return LoaderRelease(version, asset.getString("browser_download_url"), sha256)
    }

    private fun get(url: String): String {
        httpClient.newCall(Request.Builder().url(url).build()).execute().use { response ->
            val reset = response.header("x-ratelimit-reset")?.toLongOrNull()
            if (response.code in listOf(403, 429) && response.header("x-ratelimit-remaining") == "0" && reset != null) {
                throw GitHubRateLimitException(Date(reset * 1000))
            }
            if (!response.isSuccessful) throw IOException("Failed to fetch $url: HTTP ${response.code}")
            return response.body.string()
        }
    }

    companion object {
        const val MANIFEST_URL = "https://gitlab.com/rumpnisse/crossandroid/-/raw/master/modloaders.json"
    }
}
