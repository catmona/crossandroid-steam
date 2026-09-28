package de.radicalfishgames.crosscode.moddb

import io.github.z4kn4fein.semver.Version
import io.github.z4kn4fein.semver.constraints.toConstraint
import io.github.z4kn4fein.semver.satisfies
import io.github.z4kn4fein.semver.toVersionOrNull

enum class InstallReason { NEW, DEPENDENCY, UPDATE }

data class ResolvedMod(
    val entry: ModDbEntry,
    val reason: InstallReason,
)

class DependencyResolutionException(
    message: String,
) : Exception(message)

/**
 * Ports `getModDependencies`/`findDepsDatabase` from upstream CCModManager's
 * `src/mod-installer.ts`: given the mods the user wants installed, walks their `dependencies` map
 * recursively against the full database, already-installed local mods, and computed virtual-package
 * versions, and returns the full set of mods that need installing/updating.
 */
class DependencyResolver(
    private val database: Map<String, ModDbEntry>,
    private val localMods: Map<String, LocalMod>,
    private val virtualPackages: Map<String, Version>,
) {
    /** Throws [DependencyResolutionException] with the first unresolvable problem found. */
    fun resolve(selected: List<ModDbEntry>): List<ResolvedMod> {
        val requiredRanges = mutableMapOf<String, MutableList<String>>()
        val depMods = mutableMapOf<String, ModDbEntry>()
        val visiting = mutableSetOf<String>()

        for (mod in selected) collectDependencies(mod, requiredRanges, depMods, visiting)

        val selectedIds = selected.map { it.id }.toSet()
        val resolvedDeps =
            depMods.entries.filter { it.key !in selectedIds }.mapNotNull { (id, mod) ->
                val ranges = requiredRanges[id].orEmpty()
                val local = localMods[id]
                when {
                    local != null && rangesSatisfied(local.version, ranges) -> {
                        null
                    }

                    local != null -> {
                        ResolvedMod(mod, InstallReason.UPDATE)
                    }

                    else -> {
                        val dbVersion =
                            mod.version.toVersionOrNull(strict = false)
                                ?: throw DependencyResolutionException("Mod '${mod.id}' has an invalid version '${mod.version}'")
                        if (!rangesSatisfied(dbVersion, ranges)) {
                            val required = ranges.joinToString(", ")
                            throw DependencyResolutionException(
                                "Dependency '${mod.name}' version ${mod.version} does not satisfy required range(s): $required",
                            )
                        }
                        ResolvedMod(mod, InstallReason.DEPENDENCY)
                    }
                }
            }

        val resolvedSelected =
            selected.map { mod ->
                val reason = if (localMods.containsKey(mod.id)) InstallReason.UPDATE else InstallReason.NEW
                ResolvedMod(mod, reason)
            }

        return resolvedSelected + resolvedDeps
    }

    private fun collectDependencies(
        mod: ModDbEntry,
        requiredRanges: MutableMap<String, MutableList<String>>,
        depMods: MutableMap<String, ModDbEntry>,
        visiting: MutableSet<String>,
    ) {
        if (!visiting.add(mod.id)) return // Also breaks dependency cycles.

        for ((depId, range) in mod.dependencies) {
            val virtualVersion = virtualPackages[depId]
            if (virtualVersion != null) {
                if (!versionSatisfies(virtualVersion, range)) {
                    throw DependencyResolutionException(
                        "'${mod.name}' requires $depId $range, but the installed version is $virtualVersion",
                    )
                }
                continue
            }

            val depMod =
                database[depId]
                    ?: throw DependencyResolutionException(
                        "'${mod.name}' requires '$depId', which was not found in the mod database " +
                            "and is not installed/detected on this device.",
                    )

            requiredRanges.getOrPut(depId) { mutableListOf() }.add(range)
            depMods[depId] = depMod

            collectDependencies(depMod, requiredRanges, depMods, visiting)
        }
    }

    private fun rangesSatisfied(
        version: Version,
        ranges: List<String>,
    ): Boolean = ranges.all { versionSatisfies(version, it) }

    private fun versionSatisfies(
        version: Version,
        range: String,
    ): Boolean =
        try {
            version satisfies range.toConstraint()
        } catch (e: Exception) {
            false
        }
}
