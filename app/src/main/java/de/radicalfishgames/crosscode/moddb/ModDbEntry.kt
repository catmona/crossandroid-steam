package de.radicalfishgames.crosscode.moddb

/** A single mod as listed in CCModDB's `npDatabase.min.json`. */
data class ModDbEntry(
    val id: String,
    val name: String,
    val description: String,
    val version: String,
    val authors: List<String>,
    /** Dependency id to required semver range. */
    val dependencies: Map<String, String>,
    val tags: List<String>,
    val stars: Int,
    val installation: List<ModInstallation>,
)

data class ModInstallation(
    val type: String,
    val url: String,
    val sha256: String,
    /** Path prefix inside the archive to extract from; absent/empty for `.ccmod` installs. */
    val source: String?,
)
