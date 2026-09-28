package de.radicalfishgames.crosscode.moddb

import android.os.Bundle
import android.text.format.Formatter
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.radicalfishgames.crosscode.CcmodManager
import de.radicalfishgames.crosscode.GameFolderManager
import de.radicalfishgames.crosscode.R
import de.radicalfishgames.crosscode.ui.appColorScheme
import io.github.z4kn4fein.semver.Version
import io.github.z4kn4fein.semver.toVersionOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

class ModBrowserActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val gameFolderManager = GameFolderManager(this)
        val ccmodManager = CcmodManager()
        val httpClient = OkHttpClient()

        setContent {
            MaterialTheme(colorScheme = appColorScheme()) {
                ModBrowserScreen(
                    gameFolderManager = gameFolderManager,
                    ccmodManager = ccmodManager,
                    httpClient = httpClient,
                    onBack = { finish() },
                )
            }
        }
    }
}

private sealed class ScreenState {
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

private enum class BrowserTab { INSTALLED, BROWSE }

private data class PendingInstall(
    val target: ModDbEntry,
    val resolved: List<ResolvedMod>,
)

private data class InstallRun(
    val total: Int,
    val currentIndex: Int,
    val progress: InstallProgress,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModBrowserScreen(
    gameFolderManager: GameFolderManager,
    ccmodManager: CcmodManager,
    httpClient: OkHttpClient,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val modInstaller = remember { ModInstaller(gameFolderManager, httpClient) }
    val databaseClient = remember { ModDatabaseClient(httpClient) }
    val localModScanner = remember { LocalModScanner(gameFolderManager, ccmodManager) }

    var state by remember { mutableStateOf<ScreenState>(ScreenState.Loading) }
    var reloadTrigger by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var searchVisible by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(BrowserTab.INSTALLED) }
    var pendingInstall by remember { mutableStateOf<PendingInstall?>(null) }
    var pendingUninstall by remember { mutableStateOf<LocalMod?>(null) }
    var installRun by remember { mutableStateOf<InstallRun?>(null) }
    var installError by remember { mutableStateOf<String?>(null) }

    fun notify(message: String) {
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    /** Re-scans `assets/mods/` on disk and returns the fresh result, so callers can verify a write actually landed. */
    suspend fun refreshLocalMods(): Map<String, LocalMod> {
        val localMods = withContext(Dispatchers.IO) { localModScanner.scan().associateBy { it.id } }
        (state as? ScreenState.Loaded)?.let { state = it.copy(localMods = localMods) }
        return localMods
    }

    LaunchedEffect(reloadTrigger) {
        state = ScreenState.Loading
        val loader = ModLoaderDetector.detect(gameFolderManager)
        if (loader == null) {
            state = ScreenState.NoGameFolder
            return@LaunchedEffect
        }
        try {
            val loaded =
                withContext(Dispatchers.IO) {
                    val database = databaseClient.fetchDatabase()
                    val localMods = localModScanner.scan().associateBy { it.id }
                    val virtualPackages = VirtualPackages.compute(gameFolderManager, loader)
                    ScreenState.Loaded(database, localMods, virtualPackages)
                }
            state = loaded
        } catch (e: Exception) {
            state = ScreenState.Error(e.message ?: e.toString())
        }
    }

    fun runInstall(resolved: List<ResolvedMod>) {
        scope.launch {
            var failure: String? = null
            try {
                withContext(Dispatchers.IO) {
                    resolved.forEachIndexed { index, r ->
                        modInstaller.install(r.entry) { progress ->
                            installRun = InstallRun(resolved.size, index, progress)
                        }
                    }
                }
            } catch (e: Exception) {
                failure = e.message ?: e.toString()
            } finally {
                installRun = null
            }

            val localMods = refreshLocalMods()
            val unverified = resolved.firstOrNull { localMods[it.entry.id] == null }
            when {
                failure != null -> installError = failure
                unverified != null ->
                    installError = context.getString(R.string.mod_install_verify_failed, unverified.entry.name)
                resolved.size == 1 -> notify(context.getString(R.string.mod_install_success, resolved.first().entry.name))
                else -> notify(context.getString(R.string.mod_install_success_multiple, resolved.size))
            }
        }
    }

    fun requestInstall(mod: ModDbEntry) {
        val loaded = state as? ScreenState.Loaded ?: return
        scope.launch {
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
                installError = e.message
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.mod_browser_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = {
                        if (searchVisible) searchQuery = ""
                        searchVisible = !searchVisible
                    }) {
                        Icon(
                            if (searchVisible) Icons.Filled.Close else Icons.Filled.Search,
                            contentDescription = stringResource(R.string.mod_browser_search_hint),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (val current = state) {
                is ScreenState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.padding(32.dp))
                }

                is ScreenState.NoGameFolder -> {
                    Text(
                        text = stringResource(R.string.mod_browser_empty),
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                    )
                }

                is ScreenState.Error -> {
                    Column(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
                        Text(stringResource(R.string.mod_browser_load_error))
                        Text(current.message, style = MaterialTheme.typography.bodySmall)
                        Button(onClick = { reloadTrigger++ }, modifier = Modifier.padding(top = 16.dp)) {
                            Text(stringResource(R.string.mod_browser_retry))
                        }
                    }
                }

                is ScreenState.Loaded -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        if (searchVisible) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                label = { Text(stringResource(R.string.mod_browser_search_hint)) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                            )
                        }
                        SecondaryTabRow(selectedTabIndex = selectedTab.ordinal) {
                            Tab(
                                selected = selectedTab == BrowserTab.INSTALLED,
                                onClick = { selectedTab = BrowserTab.INSTALLED },
                                text = { Text(stringResource(R.string.mod_browser_tab_installed, current.localMods.size)) },
                            )
                            Tab(
                                selected = selectedTab == BrowserTab.BROWSE,
                                onClick = { selectedTab = BrowserTab.BROWSE },
                                text = { Text(stringResource(R.string.mod_browser_tab_browse)) },
                            )
                        }

                        when (selectedTab) {
                            BrowserTab.BROWSE -> {
                                val filtered =
                                    current.database.values
                                        .filter { mod -> mod.id !in current.localMods }
                                        .filter { mod -> matchesSearch(searchQuery, mod.name, mod.description, mod.tags) }
                                        .sortedByDescending { it.stars }
                                LazyColumn(modifier = Modifier.fillMaxSize()) {
                                    items(filtered, key = { it.id }) { mod ->
                                        ModRow(
                                            displayName = mod.name,
                                            description = mod.description,
                                            authors = mod.authors,
                                            installedVersion = null,
                                            latestVersion = mod.version,
                                            stars = mod.stars,
                                            hasUpdate = false,
                                            dbEntry = mod,
                                            localMod = null,
                                            onInstall = { requestInstall(mod) },
                                            onUninstall = { local -> pendingUninstall = local },
                                        )
                                    }
                                }
                            }

                            BrowserTab.INSTALLED -> {
                                val installed =
                                    current.localMods.values
                                        .filter { local -> matchesSearch(searchQuery, local.id) }
                                        .sortedWith(
                                            compareByDescending<LocalMod> { hasUpdateAvailable(it, current.database[it.id]) }
                                                .thenBy { (current.database[it.id]?.name ?: it.id).lowercase() },
                                        )
                                LazyColumn(modifier = Modifier.fillMaxSize()) {
                                    items(installed, key = { it.id }) { local ->
                                        val dbEntry = current.database[local.id]
                                        ModRow(
                                            displayName = dbEntry?.name ?: local.id,
                                            description = dbEntry?.description ?: "",
                                            authors = dbEntry?.authors ?: emptyList(),
                                            installedVersion = local.version.toString(),
                                            latestVersion = dbEntry?.version ?: local.version.toString(),
                                            stars = dbEntry?.stars,
                                            hasUpdate = hasUpdateAvailable(local, dbEntry),
                                            dbEntry = dbEntry,
                                            localMod = local,
                                            onInstall = { dbEntry?.let { requestInstall(it) } },
                                            onUninstall = { pendingUninstall = local },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    pendingInstall?.let { pending ->
        AlertDialog(
            onDismissRequest = { pendingInstall = null },
            title = { Text(stringResource(R.string.mod_install_deps_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.mod_install_deps_message, pending.target.name))
                    pending.resolved.filter { it.entry.id != pending.target.id }.forEach {
                        val suffix = if (it.reason == InstallReason.UPDATE) " (${stringResource(R.string.mod_update)})" else ""
                        Text("• ${it.entry.name}$suffix", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val resolved = pending.resolved
                    pendingInstall = null
                    runInstall(resolved)
                }) { Text(stringResource(R.string.mod_install_deps_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingInstall = null }) { Text(stringResource(android.R.string.cancel)) }
            },
        )
    }

    pendingUninstall?.let { local ->
        val displayName = (state as? ScreenState.Loaded)?.database?.get(local.id)?.name ?: local.id
        AlertDialog(
            onDismissRequest = { pendingUninstall = null },
            title = { Text(stringResource(R.string.mod_uninstall_confirm_title)) },
            text = { Text(stringResource(R.string.mod_uninstall_confirm_message, displayName)) },
            confirmButton = {
                TextButton(onClick = {
                    pendingUninstall = null
                    scope.launch {
                        var failure: String? = null
                        try {
                            withContext(Dispatchers.IO) { modInstaller.uninstall(local) }
                        } catch (e: Exception) {
                            failure = e.message ?: e.toString()
                        }
                        val localMods = refreshLocalMods()
                        when {
                            failure != null -> installError = failure
                            localMods.containsKey(local.id) ->
                                installError = context.getString(R.string.mod_uninstall_verify_failed, displayName)
                            else -> notify(context.getString(R.string.mod_uninstall_success, displayName))
                        }
                    }
                }) { Text(stringResource(R.string.mod_uninstall)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingUninstall = null }) { Text(stringResource(android.R.string.cancel)) }
            },
        )
    }

    installRun?.let { run ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text(installProgressTitle(run.progress)) },
            text = {
                Column {
                    if (run.total > 1) {
                        Text(
                            "${run.currentIndex + 1} / ${run.total}",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                    }
                    val progressFraction = installProgressFraction(run.progress)
                    if (progressFraction != null) {
                        LinearProgressIndicator(progress = { progressFraction }, modifier = Modifier.fillMaxWidth())
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    installProgressCaption(run.progress)?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            },
            confirmButton = {},
        )
    }

    installError?.let { message ->
        AlertDialog(
            onDismissRequest = { installError = null },
            title = { Text(stringResource(R.string.mod_install_error_title)) },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { installError = null }) { Text(stringResource(android.R.string.ok)) }
            },
        )
    }
}

private fun matchesSearch(
    query: String,
    vararg haystacks: String,
): Boolean = query.isBlank() || haystacks.any { it.contains(query, ignoreCase = true) }

private fun matchesSearch(
    query: String,
    name: String,
    description: String,
    tags: List<String>,
): Boolean = query.isBlank() || matchesSearch(query, name, description) || tags.any { it.contains(query, ignoreCase = true) }

/** Null [dbEntry] means the mod isn't in the online database, so an update can't be checked for. */
private fun hasUpdateAvailable(
    localMod: LocalMod?,
    dbEntry: ModDbEntry?,
): Boolean {
    if (localMod == null || dbEntry == null) return false
    val latest = dbEntry.version.toVersionOrNull(strict = false) ?: return false
    return latest > localMod.version
}

@Composable
private fun ModRow(
    displayName: String,
    description: String,
    authors: List<String>,
    installedVersion: String?,
    latestVersion: String,
    stars: Int?,
    hasUpdate: Boolean,
    dbEntry: ModDbEntry?,
    localMod: LocalMod?,
    onInstall: () -> Unit,
    onUninstall: (LocalMod) -> Unit,
) {
    val versionText =
        if (installedVersion != null && hasUpdate) {
            "v$installedVersion → v$latestVersion"
        } else {
            "v${installedVersion ?: latestVersion}"
        }
    val metaParts =
        buildList {
            if (authors.isNotEmpty()) add(stringResource(R.string.mod_by_authors, authors.joinToString(", ")))
            add(versionText)
            stars?.let { add("★$it") }
        }

    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 3.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    displayName,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    metaParts.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (description.isNotBlank()) {
                    Text(
                        description,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            when {
                localMod == null -> {
                    IconButton(onClick = onInstall) {
                        Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.mod_install))
                    }
                }
                hasUpdate -> {
                    IconButton(onClick = onInstall) {
                        Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.mod_update))
                    }
                }
                dbEntry != null -> {
                    // Matches IconButton's default 48dp touch target so it lines up with the other icon slots.
                    Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = stringResource(R.string.mod_installed),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                // else: installed but not in the online database, so we can't tell if it's current
            }

            if (localMod != null) {
                IconButton(onClick = { onUninstall(localMod) }) {
                    Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.mod_uninstall))
                }
            }
        }
    }
}

@Composable
private fun installProgressTitle(progress: InstallProgress): String =
    when (progress) {
        is InstallProgress.Preparing -> stringResource(R.string.mod_install_progress_preparing, progress.mod.name)
        is InstallProgress.Downloading -> stringResource(R.string.mod_install_progress_downloading, progress.mod.name)
        is InstallProgress.Installing -> stringResource(R.string.mod_install_progress_installing, progress.mod.name)
        is InstallProgress.Done -> progress.mod.name
    }

/** A short caption under the progress bar: a percentage when the size is known, bytes-so-far otherwise. */
@Composable
private fun installProgressCaption(progress: InstallProgress): String? =
    (progress as? InstallProgress.Downloading)?.let {
        if (it.totalBytes > 0) {
            stringResource(R.string.mod_install_progress_percent, (it.bytesRead * 100 / it.totalBytes).toInt())
        } else {
            // Some hosts (e.g. GitHub source-archive zips) omit Content-Length; show bytes so the
            // download doesn't look stuck.
            stringResource(R.string.mod_install_progress_bytes, Formatter.formatShortFileSize(LocalContext.current, it.bytesRead))
        }
    }

private fun installProgressFraction(progress: InstallProgress): Float? =
    (progress as? InstallProgress.Downloading)?.let {
        if (it.totalBytes > 0) (it.bytesRead.toFloat() / it.totalBytes.toFloat()) else null
    }
