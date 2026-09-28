package de.radicalfishgames.crosscode.moddb

import android.app.Application
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.radicalfishgames.crosscode.GameFolderManager
import de.radicalfishgames.crosscode.R
import io.github.z4kn4fein.semver.Version
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

    var state by mutableStateOf<ScreenState>(ScreenState.Loading)
        private set

    /** Set when an install pulls in dependencies, so the user can confirm them first. */
    var pendingInstall by mutableStateOf<PendingInstall?>(null)
        private set
    var installRun by mutableStateOf<InstallRun?>(null)
        private set
    var error by mutableStateOf<ErrorMessage?>(null)
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
            val loader = ModLoaderDetector.detect(gameFolderManager)
            if (loader == null) {
                state = ScreenState.NoGameFolder
                return@launch
            }
            state =
                try {
                    withContext(Dispatchers.IO) {
                        val database = databaseClient.fetchDatabase()
                        val localMods = localModScanner.scan()
                        val virtualPackages = VirtualPackages.compute(gameFolderManager, loader)
                        ScreenState.Loaded(database, localMods, virtualPackages)
                    }
                } catch (e: Exception) {
                    ScreenState.Error(e.message ?: e.toString())
                }
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
