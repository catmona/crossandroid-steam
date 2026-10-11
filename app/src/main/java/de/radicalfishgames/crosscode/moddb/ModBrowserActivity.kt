package de.radicalfishgames.crosscode.moddb

import android.os.Bundle
import android.text.format.Formatter
import android.view.KeyEvent
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.radicalfishgames.crosscode.R
import de.radicalfishgames.crosscode.ui.appColorScheme
import io.github.z4kn4fein.semver.toVersionOrNull
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

class ModBrowserActivity : AppCompatActivity() {
    private val viewModel: ModBrowserViewModel by viewModels()

    // Shoulder buttons are caught here rather than in Compose so they work even when nothing has focus.
    private val shoulderButtons = MutableSharedFlow<Int>(extraBufferCapacity = 1)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MaterialTheme(colorScheme = appColorScheme()) {
                ModBrowserScreen(
                    viewModel = viewModel,
                    shoulderButtons = shoulderButtons,
                    fromController = intent.getBooleanExtra(EXTRA_FROM_CONTROLLER, false),
                    onBack = { finish() },
                )
            }
        }
    }

    override fun onKeyDown(
        keyCode: Int,
        event: KeyEvent,
    ): Boolean {
        if (keyCode in SHOULDER_BUTTONS && shoulderButtons.tryEmit(keyCode)) return true
        return super.onKeyDown(keyCode, event)
    }

    companion object {
        const val EXTRA_FROM_CONTROLLER = "from_controller"
    }
}

private val SHOULDER_BUTTONS =
    setOf(KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_BUTTON_R1, KeyEvent.KEYCODE_BUTTON_L2, KeyEvent.KEYCODE_BUTTON_R2)

private enum class BrowserTab { INSTALLED, BROWSE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModBrowserScreen(
    viewModel: ModBrowserViewModel,
    shoulderButtons: Flow<Int>,
    fromController: Boolean,
    onBack: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var searchVisible by rememberSaveable { mutableStateOf(false) }
    var selectedTab by rememberSaveable { mutableStateOf(BrowserTab.INSTALLED) }
    var pendingUninstall by remember { mutableStateOf<LocalMod?>(null) }
    val installedListState = rememberLazyListState()
    val browseListState = rememberLazyListState()
    val listFocus = remember { FocusRequester() }
    var listHasFocus by remember { mutableStateOf(false) }
    val listModifier = Modifier.focusRequester(listFocus).onFocusChanged { listHasFocus = it.hasFocus }
    val focusManager = LocalFocusManager.current
    val inputModeManager = LocalInputModeManager.current

    // List buttons can't take focus in touch mode, which new windows start in even when opened with a controller.
    suspend fun focusFirstRow() {
        inputModeManager.requestInputMode(InputMode.Keyboard)
        withFrameNanos {} // let the list lay out its rows first
        listFocus.requestFocus()
    }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it) }
    }

    LaunchedEffect(shoulderButtons) {
        shoulderButtons.collect { keyCode ->
            if (viewModel.state !is ScreenState.Loaded) return@collect
            // Shoulder buttons don't leave touch mode by themselves, unlike the D-pad.
            inputModeManager.requestInputMode(InputMode.Keyboard)
            when (keyCode) {
                KeyEvent.KEYCODE_BUTTON_L1 -> {
                    selectedTab = BrowserTab.entries[(selectedTab.ordinal - 1).coerceAtLeast(0)]
                }

                KeyEvent.KEYCODE_BUTTON_R1 -> {
                    selectedTab = BrowserTab.entries[(selectedTab.ordinal + 1).coerceAtMost(BrowserTab.entries.lastIndex)]
                }

                else -> {
                    val listState = if (selectedTab == BrowserTab.INSTALLED) installedListState else browseListState
                    val pageUp = keyCode == KeyEvent.KEYCODE_BUTTON_L2
                    if (if (pageUp) !listState.canScrollBackward else !listState.canScrollForward) return@collect
                    // The last visible row is usually cut off, so it starts the next page.
                    val pageSize = (listState.layoutInfo.visibleItemsInfo.size - 1).coerceAtLeast(1)
                    val first = listState.firstVisibleItemIndex
                    // Clearing first unpins the focused row, so the list's first child is the first visible row again.
                    focusManager.clearFocus()
                    listState.scrollToItem(if (pageUp) (first - pageSize).coerceAtLeast(0) else first + pageSize)
                    focusFirstRow()
                }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.mod_browser_title)) },
                navigationIcon = {
                    IconButton(modifier = Modifier.focusRing(), onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(modifier = Modifier.focusRing(), onClick = {
                        if (searchVisible) searchQuery = ""
                        searchVisible = !searchVisible
                    }) {
                        Icon(
                            painterResource(if (searchVisible) R.drawable.ic_close else R.drawable.ic_search),
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
                    // Gives controller users a starting point.
                    LaunchedEffect(Unit) { if (fromController) focusFirstRow() }
                    // Switching tabs or (un)installing removes the focused row, and Compose then moves focus to the
                    // back arrow. Bring it back to the list instead.
                    LaunchedEffect(selectedTab, current.localMods) {
                        if (inputModeManager.inputMode == InputMode.Keyboard && !listHasFocus) focusFirstRow()
                    }
                    Column(modifier = Modifier.fillMaxSize()) {
                        if (searchVisible) {
                            // Focus the field right away; D-pad focus search skips it otherwise.
                            val searchFocus = remember { FocusRequester() }
                            LaunchedEffect(Unit) { searchFocus.requestFocus() }
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                label = { Text(stringResource(R.string.mod_browser_search_hint)) },
                                singleLine = true,
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                        .focusRequester(searchFocus)
                                        // A single-line field has no use for up/down but would otherwise keep D-pad focus.
                                        .onPreviewKeyEvent {
                                            val direction =
                                                when (it.key) {
                                                    Key.DirectionDown -> FocusDirection.Down
                                                    Key.DirectionUp -> FocusDirection.Up
                                                    else -> null
                                                }
                                            direction != null && it.type == KeyEventType.KeyDown && focusManager.moveFocus(direction)
                                        },
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
                                BrowseList(
                                    current,
                                    searchQuery,
                                    browseListState,
                                    listModifier,
                                    onInstall = viewModel::requestInstall,
                                )
                            }

                            BrowserTab.INSTALLED -> {
                                InstalledList(
                                    current,
                                    searchQuery,
                                    installedListState,
                                    listModifier,
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
    listState: LazyListState,
    modifier: Modifier,
    onInstall: (ModDbEntry) -> Unit,
) {
    val filtered =
        loaded.database.values
            .filter { mod -> mod.id !in loaded.localMods && mod.id !in loaded.virtualPackages }
            .filter { mod -> matchesSearch(searchQuery, mod.name, mod.description, mod.tags) }
            .sortedBy { it.name.lowercase() }
    LazyColumn(state = listState, modifier = modifier.fillMaxSize()) {
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
    listState: LazyListState,
    modifier: Modifier,
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
    LazyColumn(state = listState, modifier = modifier.fillMaxSize()) {
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

/** The default focus tint is too faint to spot on a TV or handheld, so outline focused buttons too. */
@Composable
private fun Modifier.focusRing(): Modifier {
    var focused by remember { mutableStateOf(false) }
    val color = if (focused) MaterialTheme.colorScheme.onSurface else Color.Transparent
    return onFocusChanged { focused = it.isFocused }.border(2.dp, color, CircleShape)
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
                    IconButton(modifier = Modifier.focusRing(), onClick = onInstall) {
                        Icon(painterResource(R.drawable.ic_add), contentDescription = stringResource(R.string.mod_install))
                    }
                }

                hasUpdate -> {
                    IconButton(modifier = Modifier.focusRing(), onClick = onInstall) {
                        Icon(painterResource(R.drawable.ic_refresh), contentDescription = stringResource(R.string.mod_update))
                    }
                }

                dbEntry != null -> {
                    // Matches IconButton's default 48dp touch target so it lines up with the other icon slots.
                    Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                        Icon(
                            painterResource(R.drawable.ic_check_circle),
                            contentDescription = stringResource(R.string.mod_installed),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                // else: installed but not in the online database, so we can't tell if it's current
            }

            if (localMod != null) {
                IconButton(modifier = Modifier.focusRing(), onClick = onUninstall) {
                    Icon(painterResource(R.drawable.ic_delete), contentDescription = stringResource(R.string.mod_uninstall))
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
