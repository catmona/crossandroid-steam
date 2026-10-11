package de.radicalfishgames.crosscode.moddb

import android.app.Application
import android.util.Log
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.radicalfishgames.crosscode.GameFolderManager
import de.radicalfishgames.crosscode.ModLoaderFlavor
import de.radicalfishgames.crosscode.R
import io.github.z4kn4fein.semver.Version
import io.github.z4kn4fein.semver.toVersionOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

sealed class ScreenState {
    object Loading : ScreenState()

    object NoGameFolder : ScreenState()

    /** No modloader installed; offers [releases] to install (null: this app can't resolve that entry). */
    data class NoModLoader(
        val releases: Map<ModLoaderFlavor, LoaderRelease?>,
    ) : ScreenState()

    data class Error(
        val message: String,
    ) : ScreenState()

    data class Loaded(
        val database: Map<String, ModDbEntry>,
        val localMods: Map<String, LocalMod>,
        val virtualPackages: Map<String, Version>,
    ) : ScreenState()
}

data class PendingInstall(
    val target: ModDbEntry,
    val resolved: List<ResolvedMod>,
)

data class LoaderUpdate(
    val flavor: ModLoaderFlavor,
    val installed: Version,
    val release: LoaderRelease,
)

data class ErrorMessage(
    @param:StringRes val titleId: Int,
    val message: String,
)

data class InstallRun(
    val total: Int,
    val currentIndex: Int,
    val progress: InstallProgress,
)

/**
 * Loads the mod database and installed mods, and runs installs and uninstalls. Lives in a ViewModel
 * so a rotation neither cancels an install mid-extract nor re-downloads the database.
 */
class ModBrowserViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val gameFolderManager = GameFolderManager(application)
    private val httpClient = OkHttpClient()
    private val modInstaller = ModInstaller(gameFolderManager, httpClient)
    private val databaseClient = ModDatabaseClient(httpClient)
    private val localModScanner = LocalModScanner(gameFolderManager)
    private val modLoaderReleases = ModLoaderReleases(httpClient)

    var state by mutableStateOf<ScreenState>(ScreenState.Loading)
        private set

    /** Set when an install pulls in dependencies, so the user can confirm them first. */
    var pendingInstall by mutableStateOf<PendingInstall?>(null)
        private set
    var installRun by mutableStateOf<InstallRun?>(null)
        private set
    var error by mutableStateOf<ErrorMessage?>(null)
        private set

    /** Set when a newer release of the installed modloader is available, so the user can choose to update. */
    var loaderUpdate by mutableStateOf<LoaderUpdate?>(null)
        private set

    private val messageChannel = Channel<String>(Channel.BUFFERED)

    /** One-off success messages for a snackbar. */
    val messages: Flow<String> = messageChannel.receiveAsFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            state = ScreenState.Loading
            if (!gameFolderManager.hasValidGameFolder()) {
                state = ScreenState.NoGameFolder
                return@launch
            }
            val loader = gameFolderManager.findModLoader()
            state =
                try {
                    withContext(Dispatchers.IO) {
                        if (loader == null) return@withContext ScreenState.NoModLoader(modLoaderReleases.fetch())
                        val database = databaseClient.fetchDatabase()
                        val localMods = localModScanner.scan()
                        val virtualPackages = VirtualPackages.compute(gameFolderManager, loader)
                        ScreenState.Loaded(database, localMods, virtualPackages)
                    }
                } catch (e: Exception) {
                    ScreenState.Error(e.message ?: e.toString())
                }
            val installedVersion = (state as? ScreenState.Loaded)?.virtualPackages?.get("ccloader")
            if (loader != null && installedVersion != null) launch { checkLoaderUpdate(loader.flavor, installedVersion) }
        }
    }

    /** Offers [flavor]'s latest release if it's newer than [installed]. Best effort: failures are only logged. */
    private suspend fun checkLoaderUpdate(
        flavor: ModLoaderFlavor,
        installed: Version,
    ) {
        val release =
            try {
                withContext(Dispatchers.IO) { modLoaderReleases.fetch(listOf(flavor))[flavor] }
            } catch (e: Exception) {
                Log.w("CrossCode", "Modloader update check failed: ${e.message}")
                null
            } ?: return
        val latest = release.version.toVersionOrNull(strict = false) ?: return
        if (latest > installed) loaderUpdate = LoaderUpdate(flavor, installed, release)
    }

    fun confirmLoaderUpdate() {
        val update = loaderUpdate ?: return
        loaderUpdate = null
        runModLoaderInstall(update.flavor, update.release)
    }

    fun dismissLoaderUpdate() {
        loaderUpdate = null
    }

    fun installModLoader(flavor: ModLoaderFlavor) {
        val release = (state as? ScreenState.NoModLoader)?.releases?.get(flavor) ?: return
        runModLoaderInstall(flavor, release)
    }

    /**
     * Installs or updates to [release], then checks the loader on disk is [flavor] at that version.
     * An update that lands in a different folder (CCLoader3 in `ccloader/` → `ccloader3/`) deletes
     * the old one, which would otherwise linger unused.
     */
    private fun runModLoaderInstall(
        flavor: ModLoaderFlavor,
        release: LoaderRelease,
    ) {
        viewModelScope.launch {
            try {
                val verified =
                    withContext(Dispatchers.IO) {
                        val previous = gameFolderManager.findModLoader()
                        modInstaller.installModLoader(flavor.displayName, release) { installRun = InstallRun(1, 0, it) }
                        val installed = gameFolderManager.findModLoader()?.takeIf { it.flavor == flavor }
                        val expectedVersion = release.version.toVersionOrNull(strict = false)
                        val verified =
                            installed != null &&
                                (expectedVersion == null || VirtualPackages.readCcloaderVersion(gameFolderManager, installed) == expectedVersion)
                        if (verified && previous?.flavor == flavor && previous.dir != installed?.dir) {
                            if (gameFolderManager.getFile(previous.dir)?.deleteRecursively() != true) {
                                Log.w("CrossCode", "Failed to fully delete old modloader folder ${previous.dir}")
                            }
                        }
                        verified
                    }
                if (verified) {
                    messageChannel.send(string(R.string.mod_install_success, flavor.displayName))
                } else {
                    error = ErrorMessage(R.string.mod_install_error_title, string(R.string.modloader_install_verify_failed, flavor.displayName))
                }
            } catch (e: Exception) {
                error = ErrorMessage(R.string.mod_install_error_title, e.message ?: e.toString())
            } finally {
                installRun = null
            }
            load()
        }
    }

    /** Installs or updates [mod], asking for confirmation first if it needs other mods too. */
    fun requestInstall(mod: ModDbEntry) {
        val loaded = state as? ScreenState.Loaded ?: return
        viewModelScope.launch {
            try {
                val resolved =
                    withContext(Dispatchers.IO) {
                        DependencyResolver(loaded.database, loaded.localMods, loaded.virtualPackages).resolve(listOf(mod))
                    }
                if (resolved.size > 1) {
                    pendingInstall = PendingInstall(mod, resolved)
                } else {
                    runInstall(resolved)
                }
            } catch (e: DependencyResolutionException) {
                error = ErrorMessage(R.string.mod_install_error_title, e.message.orEmpty())
            }
        }
    }

    fun confirmPendingInstall() {
        val pending = pendingInstall ?: return
        pendingInstall = null
        runInstall(pending.resolved)
    }

    fun dismissPendingInstall() {
        pendingInstall = null
    }

    fun dismissError() {
        error = null
    }

    fun uninstall(
        local: LocalMod,
        displayName: String,
    ) = runVerified(
        errorTitleId = R.string.mod_uninstall_error_title,
        action = { modInstaller.uninstall(local) },
        verify = { localMods ->
            if (local.id in localMods) string(R.string.mod_uninstall_verify_failed, displayName) else null
        },
        successMessage = string(R.string.mod_uninstall_success, displayName),
    )

    private fun runInstall(resolved: List<ResolvedMod>) {
        val installedMods = (state as? ScreenState.Loaded)?.localMods ?: return
        runVerified(
            errorTitleId = R.string.mod_install_error_title,
            action = {
                try {
                    resolved.forEachIndexed { index, r ->
                        modInstaller.install(r.entry, installedMods[r.entry.id]) { progress ->
                            installRun = InstallRun(resolved.size, index, progress)
                        }
                    }
                } finally {
                    installRun = null
                }
            },
            verify = { localMods ->
                resolved
                    .firstOrNull { it.entry.id !in localMods }
                    ?.let { string(R.string.mod_install_verify_failed, it.entry.name) }
            },
            successMessage =
                if (resolved.size == 1) {
                    string(R.string.mod_install_success, resolved.first().entry.name)
                } else {
                    string(R.string.mod_install_success_multiple, resolved.size)
                },
        )
    }

    /**
     * Runs [action] off the main thread, then rescans the mods on disk and checks with [verify] that
     * the change actually landed. [verify] returns an error message, or null on success. Errors are
     * shown under [errorTitleId].
     */
    private fun runVerified(
        @StringRes errorTitleId: Int,
        action: () -> Unit,
        verify: (Map<String, LocalMod>) -> String?,
        successMessage: String,
    ) {
        viewModelScope.launch {
            val failure =
                try {
                    withContext(Dispatchers.IO) { action() }
                    null
                } catch (e: Exception) {
                    e.message ?: e.toString()
                }

            val localMods = withContext(Dispatchers.IO) { localModScanner.scan() }
            (state as? ScreenState.Loaded)?.let { state = it.copy(localMods = localMods) }

            val message = failure ?: verify(localMods)
            if (message != null) {
                error = ErrorMessage(errorTitleId, message)
            } else {
                messageChannel.send(successMessage)
            }
        }
    }

    private fun string(
        @StringRes id: Int,
        vararg args: Any,
    ): String = getApplication<Application>().getString(id, *args)
}
