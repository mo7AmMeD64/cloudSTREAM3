package com.kodastream.app.ui.extensions

import android.app.Application
import android.content.ClipboardManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kodastream.app.R
import com.kodastream.app.data.AppEvents
import com.kodastream.app.engine.Engine
import com.kodastream.app.ui.components.EmptyBox
import com.kodastream.app.ui.components.ErrorBox
import com.kodastream.app.ui.components.RemoteImage
import com.lagradost.cloudstream3.CommonActivity
import com.lagradost.cloudstream3.plugins.InstalledPlugin
import com.lagradost.cloudstream3.plugins.PluginManager
import com.lagradost.cloudstream3.plugins.RepositoryData
import com.lagradost.cloudstream3.plugins.RepositoryManager
import com.lagradost.cloudstream3.plugins.SitePlugin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface BrowseState {
    data object Idle : BrowseState
    data object Loading : BrowseState
    data object Failed : BrowseState
    data class Ready(val plugins: List<SitePlugin>) : BrowseState
}

class ExtensionsViewModel(application: Application) : AndroidViewModel(application) {
    private val _repositories = MutableStateFlow<List<RepositoryData>>(emptyList())
    val repositories: StateFlow<List<RepositoryData>> = _repositories.asStateFlow()

    private val _installed = MutableStateFlow<List<InstalledPlugin>>(emptyList())
    val installed: StateFlow<List<InstalledPlugin>> = _installed.asStateFlow()

    private val _selectedRepo = MutableStateFlow<String?>(null)
    val selectedRepo: StateFlow<String?> = _selectedRepo.asStateFlow()

    private val _browse = MutableStateFlow<BrowseState>(BrowseState.Idle)
    val browse: StateFlow<BrowseState> = _browse.asStateFlow()

    /** Plugins being installed right now, as "repoUrl|internalName". */
    private val _busy = MutableStateFlow<Set<String>>(emptySet())
    val busy: StateFlow<Set<String>> = _busy.asStateFlow()

    private val _updating = MutableStateFlow(false)
    val updating: StateFlow<Boolean> = _updating.asStateFlow()

    private var browseJob: Job? = null

    init {
        viewModelScope.launch {
            PluginManager.revision.collect { reload() }
        }
    }

    private fun reload() {
        val context = getApplication<Application>()
        val repos = PluginManager.getRepositories(context)
        _repositories.value = repos
        _installed.value = PluginManager.getInstalled(context)

        val selected = _selectedRepo.value
        if (selected == null || repos.none { it.url == selected }) {
            selectRepo(repos.firstOrNull()?.url)
        }
    }

    private fun toast(resId: Int, vararg args: Any) {
        CommonActivity.showToast(getApplication<Application>().getString(resId, *args))
    }

    fun key(repo: RepositoryData, plugin: SitePlugin) = repo.url + "|" + plugin.internalName

    fun selectRepo(url: String?) {
        if (url != null && url == _selectedRepo.value && _browse.value is BrowseState.Ready) return
        _selectedRepo.value = url
        browseJob?.cancel()
        val repo = _repositories.value.firstOrNull { it.url == url }
        if (repo == null) {
            _browse.value = BrowseState.Idle
            return
        }
        browseJob = viewModelScope.launch {
            _browse.value = BrowseState.Loading
            val plugins = RepositoryManager.getRepoPlugins(repo)
            _browse.value = if (plugins == null) BrowseState.Failed else BrowseState.Ready(plugins)
        }
    }

    fun refreshBrowse() {
        val url = _selectedRepo.value
        _browse.value = BrowseState.Idle
        _selectedRepo.value = null
        selectRepo(url)
    }

    fun addRepository(input: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val context = getApplication<Application>()
            val url = RepositoryManager.parseRepoUrl(input)
            if (url == null) {
                toast(R.string.repo_invalid)
                onResult(false)
                return@launch
            }
            val parsed = RepositoryManager.parseRepository(url)
            if (parsed == null) {
                toast(R.string.repo_unreadable)
                onResult(false)
                return@launch
            }
            PluginManager.addRepository(context, RepositoryData(parsed.name, url, parsed.iconUrl))
            _selectedRepo.value = null
            selectRepo(url)
            toast(R.string.repo_added, parsed.name)
            onResult(true)
        }
    }

    fun removeRepository(repo: RepositoryData) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { PluginManager.removeRepository(getApplication<Application>(), repo) }
        }
    }

    fun install(repo: RepositoryData, plugin: SitePlugin) {
        val key = key(repo, plugin)
        if (key in _busy.value) return
        _busy.update { it + key }
        viewModelScope.launch {
            // Loading a dex file is slow, so it stays off the main thread.
            val error = withContext(Dispatchers.IO) {
                PluginManager.install(getApplication<Application>(), repo, plugin)
            }
            _busy.update { it - key }
            if (error == null) toast(R.string.installed_ok, plugin.name) else toast(R.string.install_failed, error)
        }
    }

    fun uninstall(plugin: InstalledPlugin) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { PluginManager.uninstall(getApplication<Application>(), plugin) }
        }
    }

    fun updateAll() {
        if (_updating.value) return
        _updating.value = true
        viewModelScope.launch {
            val count = withContext(Dispatchers.IO) { PluginManager.updateAll(getApplication<Application>()) }
            _updating.value = false
            toast(if (count > 0) R.string.updated_n else R.string.nothing_to_update, count)
        }
    }
}

@Composable
fun ExtensionsScreen(
    contentPadding: PaddingValues,
    viewModel: ExtensionsViewModel = viewModel(),
) {
    val repositories by viewModel.repositories.collectAsState()
    val installed by viewModel.installed.collectAsState()
    val selectedRepo by viewModel.selectedRepo.collectAsState()
    val browse by viewModel.browse.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val updating by viewModel.updating.collectAsState()
    val revision by PluginManager.revision.collectAsState()
    val pendingRepo by AppEvents.pendingRepoUrl.collectAsState()
    val context = LocalContext.current

    var tab by rememberSaveable { mutableStateOf(0) }
    var showAddDialog by rememberSaveable { mutableStateOf(false) }
    var dialogText by rememberSaveable { mutableStateOf("") }
    var adding by remember { mutableStateOf(false) }

    // Same as original CloudStream: a copied repo address is filled in for you.
    val openAddDialog = {
        val text = (context.getSystemService(ClipboardManager::class.java))
            ?.primaryClip?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)?.text?.toString()?.trim()
        // Shared repos arrive as "<name> : <url>"; the dialog only needs the url.
        val copied = text?.substringAfterLast(" : ")?.trim()
        if (!copied.isNullOrBlank()) dialogText = copied
        showAddDialog = true
    }

    // A cloudstreamrepo:// or https://cs.repo link lands here with the address filled in.
    LaunchedEffect(pendingRepo) {
        val link = pendingRepo ?: return@LaunchedEffect
        dialogText = link
        showAddDialog = true
        AppEvents.pendingRepoUrl.value = null
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = contentPadding.calculateTopPadding()),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.nav_extensions),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
            if (updating) {
                LoadingIndicator(Modifier.size(28.dp))
                Spacer(Modifier.width(12.dp))
            } else if (installed.isNotEmpty()) {
                IconButton(onClick = viewModel::updateAll) {
                    Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.update_all))
                }
            }
            IconButton(onClick = openAddDialog) {
                Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.add_repository))
            }
        }

        PrimaryTabRow(selectedTabIndex = tab) {
            Tab(
                selected = tab == 0,
                onClick = { tab = 0 },
                text = { Text(stringResource(R.string.tab_installed, installed.size)) },
            )
            Tab(
                selected = tab == 1,
                onClick = { tab = 1 },
                text = { Text(stringResource(R.string.tab_browse)) },
            )
        }

        Box(Modifier.weight(1f)) {
            when (tab) {
                0 -> InstalledTab(
                    installed = installed,
                    revision = revision,
                    bottomPadding = contentPadding.calculateBottomPadding(),
                    onUninstall = viewModel::uninstall,
                    onBrowse = { tab = 1 },
                )

                else -> BrowseTab(
                    repositories = repositories,
                    selectedRepo = selectedRepo,
                    browse = browse,
                    installed = installed,
                    busy = busy,
                    bottomPadding = contentPadding.calculateBottomPadding(),
                    onSelectRepo = viewModel::selectRepo,
                    onRemoveRepo = viewModel::removeRepository,
                    onRetry = viewModel::refreshBrowse,
                    onInstall = viewModel::install,
                    onAddRepo = openAddDialog,
                    keyOf = viewModel::key,
                )
            }
        }
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text(stringResource(R.string.add_repository)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.add_repository_hint),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = dialogText,
                        onValueChange = { dialogText = it },
                        singleLine = true,
                        placeholder = { Text("https://…/repo.json") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = dialogText.isNotBlank() && !adding,
                    onClick = {
                        adding = true
                        viewModel.addRepository(dialogText) { added ->
                            adding = false
                            // A failed address keeps the dialog open so it can be fixed.
                            if (added) {
                                tab = 1
                                showAddDialog = false
                                dialogText = ""
                            }
                        }
                    },
                ) { Text(stringResource(R.string.add)) }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun InstalledTab(
    installed: List<InstalledPlugin>,
    revision: Int,
    bottomPadding: androidx.compose.ui.unit.Dp,
    onUninstall: (InstalledPlugin) -> Unit,
    onBrowse: () -> Unit,
) {
    if (installed.isEmpty()) {
        EmptyBox(
            title = stringResource(R.string.no_extensions_title),
            hint = stringResource(R.string.no_extensions_hint),
        ) {
            Button(onClick = onBrowse) { Text(stringResource(R.string.tab_browse)) }
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = bottomPadding + 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(installed, key = { it.filePath }) { plugin ->
            // Recomputed whenever the loaded set changes.
            val providerNames = remember(revision, plugin.filePath) {
                Engine.providers().filter { it.sourcePlugin == plugin.filePath }.joinToString(", ") { it.name }
            }
            val loaded = remember(revision, plugin.filePath) { PluginManager.isLoaded(plugin.filePath) }
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    PluginIcon(plugin.iconUrl)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = plugin.name,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "v${plugin.version}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (!loaded) {
                            Text(
                                text = stringResource(R.string.plugin_not_loaded),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                        } else if (providerNames.isNotBlank()) {
                            Text(
                                text = providerNames,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    IconButton(onClick = { onUninstall(plugin) }) {
                        Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.uninstall))
                    }
                }
            }
        }
    }
}

@Composable
private fun BrowseTab(
    repositories: List<RepositoryData>,
    selectedRepo: String?,
    browse: BrowseState,
    installed: List<InstalledPlugin>,
    busy: Set<String>,
    bottomPadding: androidx.compose.ui.unit.Dp,
    onSelectRepo: (String?) -> Unit,
    onRemoveRepo: (RepositoryData) -> Unit,
    onRetry: () -> Unit,
    onInstall: (RepositoryData, SitePlugin) -> Unit,
    onAddRepo: () -> Unit,
    keyOf: (RepositoryData, SitePlugin) -> String,
) {
    if (repositories.isEmpty()) {
        EmptyBox(
            title = stringResource(R.string.no_repos_title),
            hint = stringResource(R.string.no_repos_hint),
        ) {
            Button(onClick = onAddRepo) { Text(stringResource(R.string.add_repository)) }
        }
        return
    }

    val repo = repositories.firstOrNull { it.url == selectedRepo }
    var filter by rememberSaveable { mutableStateOf("") }

    Column(Modifier.fillMaxSize()) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(repositories) { r ->
                FilterChip(
                    selected = r.url == selectedRepo,
                    onClick = { onSelectRepo(r.url) },
                    label = { Text(r.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                )
            }
        }

        if (repo != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = filter,
                    onValueChange = { filter = it },
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.filter_extensions)) },
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { onRemoveRepo(repo) }) {
                    Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.remove_repository))
                }
            }
        }

        Box(Modifier.weight(1f)) {
            when (browse) {
                BrowseState.Idle -> Unit

                BrowseState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    LoadingIndicator(Modifier.size(44.dp))
                }

                BrowseState.Failed -> ErrorBox(
                    message = stringResource(R.string.repo_load_failed),
                    onRetry = onRetry,
                )

                is BrowseState.Ready -> {
                    val shown = remember(browse, filter) {
                        browse.plugins.filter { filter.isBlank() || it.name.contains(filter, ignoreCase = true) }
                    }
                    if (shown.isEmpty()) {
                        EmptyBox(title = stringResource(R.string.nothing_found), hint = null)
                    } else if (repo != null) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottomPadding + 16.dp,
                            ),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            items(shown) { plugin ->
                                val local = installed.firstOrNull {
                                    it.repoUrl == repo.url && it.internalName == plugin.internalName
                                }
                                PluginCard(
                                    plugin = plugin,
                                    installedVersion = local?.version,
                                    busy = keyOf(repo, plugin) in busy,
                                    onInstall = { onInstall(repo, plugin) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PluginCard(
    plugin: SitePlugin,
    installedVersion: Int?,
    busy: Boolean,
    onInstall: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PluginIcon(plugin.iconUrl)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = plugin.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val details = listOfNotNull(
                    "v${plugin.version}",
                    plugin.language?.takeIf { it.isNotBlank() },
                    plugin.tvTypes?.takeIf { it.isNotEmpty() }?.joinToString(", "),
                ).joinToString("  •  ")
                Text(
                    text = details,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                plugin.description?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (plugin.status == 0) {
                    Text(
                        text = stringResource(R.string.plugin_down),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            when {
                busy -> LoadingIndicator(Modifier.size(32.dp))

                installedVersion == null -> FilledTonalButton(onClick = onInstall) {
                    Text(stringResource(R.string.install))
                }

                installedVersion < plugin.version -> Button(onClick = onInstall) {
                    Text(stringResource(R.string.update))
                }

                else -> Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = stringResource(R.string.installed),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun PluginIcon(iconUrl: String?) {
    // Repositories write "%size%" where the app is meant to put the size it wants.
    val url = iconUrl?.replace("%size%", "128")
    RemoteImage(
        url = url,
        headers = null,
        modifier = Modifier
            .size(44.dp)
            .clip(MaterialTheme.shapes.small),
    )
}
