package de.radicalfishgames.crosscode.moddb

import android.os.Bundle
import android.text.format.Formatter
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.radicalfishgames.crosscode.R
import de.radicalfishgames.crosscode.ui.appColorScheme
import io.github.z4kn4fein.semver.toVersionOrNull

class ModBrowserActivity : AppCompatActivity() {
    private val viewModel: ModBrowserViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MaterialTheme(colorScheme = appColorScheme()) {
                ModBrowserScreen(viewModel = viewModel, onBack = { finish() })
            }
        }
    }
}

private enum class BrowserTab { INSTALLED, BROWSE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModBrowserScreen(
    viewModel: ModBrowserViewModel,
    onBack: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var searchVisible by rememberSaveable { mutableStateOf(false) }
    var selectedTab by rememberSaveable { mutableStateOf(BrowserTab.INSTALLED) }
    var pendingUninstall by remember { mutableStateOf<LocalMod?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it) }
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
            when (val current = viewModel.state) {
                is ScreenState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.padding(32.dp))
                }

                is ScreenState.NoGameFolder -> {
                    Text(
                        text = stringResource(R.string.mod_browser_empty),
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                    )
                }

                is ScreenState.NoModLoader -> {
                    Column(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
                        Text(stringResource(R.string.modloader_missing))
                        for ((flavor, release) in current.releases) {
                            if (release != null) {
                                Button(
                                    onClick = { viewModel.installModLoader(flavor) },
                                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                                ) {
                                    Text(stringResource(R.string.modloader_install, flavor.displayName, release.version))
                                }
                            } else {
                                Text(
                                    stringResource(R.string.modloader_unsupported, flavor.displayName),
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(top = 16.dp),
                                )
                            }
                        }
                    }
                }

                is ScreenState.Error -> {
                    Column(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
                        Text(stringResource(R.string.mod_browser_load_error))
                        Text(current.message, style = MaterialTheme.typography.bodySmall)
                        Button(onClick = viewModel::load, modifier = Modifier.padding(top = 16.dp)) {
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
                                BrowseList(current, searchQuery, onInstall = viewModel::requestInstall)
                            }

                            BrowserTab.INSTALLED -> {
                                InstalledList(
                                    current,
                                    searchQuery,
                                    onInstall = viewModel::requestInstall,
                                    onUninstall = { pendingUninstall = it },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    viewModel.pendingInstall?.let { pending ->
        PendingInstallDialog(pending, onConfirm = viewModel::confirmPendingInstall, onDismiss = viewModel::dismissPendingInstall)
    }

    pendingUninstall?.let { local ->
        val displayName = (viewModel.state as? ScreenState.Loaded)?.database?.get(local.id)?.name ?: local.id
        AlertDialog(
            onDismissRequest = { pendingUninstall = null },
            title = { Text(stringResource(R.string.mod_uninstall_confirm_title)) },
            text = { Text(stringResource(R.string.mod_uninstall_confirm_message, displayName)) },
            confirmButton = {
                TextButton(onClick = {
                    pendingUninstall = null
                    viewModel.uninstall(local, displayName)
                }) { Text(stringResource(R.string.mod_uninstall)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingUninstall = null }) { Text(stringResource(android.R.string.cancel)) }
            },
        )
    }

    viewModel.loaderUpdate?.let { update ->
        AlertDialog(
            onDismissRequest = viewModel::dismissLoaderUpdate,
            title = { Text(stringResource(R.string.modloader_update_title)) },
            text = {
                Text(stringResource(R.string.modloader_update_message, update.flavor.displayName, update.release.version, update.installed))
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmLoaderUpdate) { Text(stringResource(R.string.modloader_update_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissLoaderUpdate) { Text(stringResource(R.string.modloader_update_later)) }
            },
        )
    }

    viewModel.installRun?.let { InstallProgressDialog(it) }

    viewModel.error?.let { error ->
        AlertDialog(
            onDismissRequest = viewModel::dismissError,
            title = { Text(stringResource(error.titleId)) },
            text = { Text(error.message) },
            confirmButton = {
                TextButton(onClick = viewModel::dismissError) { Text(stringResource(android.R.string.ok)) }
            },
        )
    }
}

/** Mods from the database that aren't installed yet, alphabetically. */
@Composable
private fun BrowseList(
    loaded: ScreenState.Loaded,
    searchQuery: String,
    onInstall: (ModDbEntry) -> Unit,
) {
    val filtered =
        loaded.database.values
            .filter { mod -> mod.id !in loaded.localMods && mod.id !in loaded.virtualPackages }
            .filter { mod -> matchesSearch(searchQuery, mod.name, mod.description, mod.tags) }
            .sortedBy { it.name.lowercase() }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(filtered, key = { it.id }) { mod ->
            ModRow(dbEntry = mod, localMod = null, hasUpdate = false, onInstall = { onInstall(mod) }, onUninstall = {})
        }
    }
}

/** Installed mods, those with an update first. */
@Composable
private fun InstalledList(
    loaded: ScreenState.Loaded,
    searchQuery: String,
    onInstall: (ModDbEntry) -> Unit,
    onUninstall: (LocalMod) -> Unit,
) {
    val installed =
        loaded.localMods.values
            .filter { local -> matchesSearch(searchQuery, local.id, loaded.database[local.id]?.name.orEmpty()) }
            .map { local -> local to hasUpdateAvailable(local, loaded.database[local.id]) }
            .sortedWith(
                compareByDescending<Pair<LocalMod, Boolean>> { it.second }
                    .thenBy { (local, _) -> (loaded.database[local.id]?.name ?: local.id).lowercase() },
            )
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(installed, key = { it.first.id }) { (local, hasUpdate) ->
            val dbEntry = loaded.database[local.id]
            ModRow(
                dbEntry = dbEntry,
                localMod = local,
                hasUpdate = hasUpdate,
                onInstall = { dbEntry?.let(onInstall) },
                onUninstall = { onUninstall(local) },
            )
        }
    }
}

@Composable
private fun PendingInstallDialog(
    pending: PendingInstall,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
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
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.mod_install_deps_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        },
    )
}

@Composable
private fun InstallProgressDialog(run: InstallRun) {
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

/** A mod from the database ([dbEntry]), installed locally ([localMod]), or both. */
@Composable
private fun ModRow(
    dbEntry: ModDbEntry?,
    localMod: LocalMod?,
    hasUpdate: Boolean,
    onInstall: () -> Unit,
    onUninstall: () -> Unit,
) {
    val displayName = dbEntry?.name ?: localMod?.id.orEmpty()
    val description = dbEntry?.description.orEmpty()
    val authors = dbEntry?.authors.orEmpty()
    val installedVersion = localMod?.version?.toString()
    val latestVersion = dbEntry?.version ?: installedVersion
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
            dbEntry?.stars?.let { add("★$it") }
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
                IconButton(onClick = onUninstall) {
                    Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.mod_uninstall))
                }
            }
        }
    }
}

@Composable
private fun installProgressTitle(progress: InstallProgress): String =
    when (progress) {
        is InstallProgress.Preparing -> stringResource(R.string.mod_install_progress_preparing, progress.name)
        is InstallProgress.Downloading -> stringResource(R.string.mod_install_progress_downloading, progress.name)
        is InstallProgress.Installing -> stringResource(R.string.mod_install_progress_installing, progress.name)
        is InstallProgress.Done -> progress.name
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
