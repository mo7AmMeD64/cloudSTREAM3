package com.kodastream.app.ui.manga

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kodastream.app.R
import com.kodastream.app.manga.MangaPrefs
import com.kodastream.app.manga.extension.InstallStep
import com.kodastream.app.manga.extension.MangaExtension
import com.kodastream.app.manga.extension.MangaSourceTestState
import com.kodastream.app.manga.extension.TestStep
import com.kodastream.app.manga.store.AvailableExtension
import com.kodastream.app.manga.store.ContentWarning
import com.kodastream.app.manga.store.MangaFilters
import com.kodastream.app.ui.components.EmptyBox
import com.kodastream.app.ui.components.ErrorBox
import com.kodastream.app.ui.components.ExpressivePullToRefresh
import com.kodastream.app.ui.components.RemoteImage
import com.kodastream.app.ui.components.SkeletonBox
import com.kodastream.app.ui.components.SkeletonPulseProvider
import com.kodastream.app.ui.components.SkeletonTextLine
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.online.HttpSource

/**
 * The manga extension manager: installed and available extensions, filters, the
 * details sheet with the four-step source test, and store management through a
 * separate screen.
 */
@Composable
fun MangaExtensionsScreen(
    contentPadding: PaddingValues,
    onOpenStores: () -> Unit,
    onOpenSourcePreferences: (Long) -> Unit,
    onBack: (() -> Unit)? = null,
    viewModel: MangaExtensionsViewModel = viewModel(),
) {
    val installed by viewModel.installed.collectAsState()
    val available by viewModel.available.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val refreshing by viewModel.refreshing.collectAsState()
    val storeError by viewModel.storeError.collectAsState()
    val installing by viewModel.installing.collectAsState()
    val sources by viewModel.sources.collectAsState()
    val testingSourceId by viewModel.testingSourceId.collectAsState()
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]

    var showNsfw by remember { mutableStateOf(MangaPrefs.showNsfw(context)) }
    var allLanguages by remember { mutableStateOf(MangaPrefs.allLanguages(context)) }
    var languages by remember { mutableStateOf(MangaPrefs.languages(context)) }
    var tab by rememberSaveable { mutableStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    var showFilters by rememberSaveable { mutableStateOf(false) }
    var showLanguageDialog by rememberSaveable { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var detailsTarget by remember { mutableStateOf<MangaExtension.Installed?>(null) }
    var uninstallTarget by remember { mutableStateOf<MangaExtension.Installed?>(null) }
    var trustTarget by remember { mutableStateOf<MangaExtension.NotLoaded?>(null) }
    var testState by remember { mutableStateOf<MangaSourceTestState?>(null) }
    var testSourceName by remember { mutableStateOf("") }

    val changeNsfw: (Boolean) -> Unit = {
        showNsfw = it
        MangaPrefs.setShowNsfw(context, it)
    }
    val changeLanguages: (Set<String>, Boolean) -> Unit = { selected, all ->
        allLanguages = all
        languages = selected
        MangaPrefs.setAllLanguages(context, all)
        MangaPrefs.setLanguages(context, selected)
    }

    // One row per package even when several stores list it; the newest listing wins.
    val newestListings = remember(available) {
        available.groupBy { it.pkgName }
            .values
            .map { list ->
                list.maxWith(
                    compareBy<AvailableExtension> { it.versionCode }.thenBy { it.libVersion ?: 0.0 },
                )
            }
            .sortedBy { it.name.lowercase() }
    }
    val availableLanguages = remember(available, locale) {
        mangaSourceLanguages(available.map { it.lang })
            .sortedBy { mangaLanguageSortKey(it, locale) }
    }
    val shownInstalled = remember(installed, languages, allLanguages, showNsfw, query) {
        installed.filter { extension ->
            val inLanguage = allLanguages || extension.lang == null ||
                extension.lang == "all" || extension.lang in languages
            val inWarning = showNsfw || extension.contentWarning != ContentWarning.NSFW
            val needle = query.trim()
            val inQuery = needle.isEmpty() ||
                extension.name.contains(needle, ignoreCase = true) ||
                extension.pkgName.contains(needle, ignoreCase = true)
            inLanguage && inWarning && inQuery
        }
    }
    val shownAvailable = remember(
        newestListings, languages, allLanguages, showNsfw, query,
    ) {
        MangaFilters.filter(newestListings, languages, allLanguages, showNsfw, query)
    }
    val updateCount = remember(installed, available) {
        installed.count { extension ->
            MangaFilters.findListing(extension.pkgName, available, extension.signatures)
                ?.let { MangaFilters.hasUpdate(extension.versionCode, extension.libVersion, it) } == true
        }
    }
    val testableSourceIds = remember(sources) { sources.map { it.source.id }.toSet() }

    LaunchedEffect(testingSourceId) {
        val id = testingSourceId ?: return@LaunchedEffect
        testState = null
        val source = sources.firstOrNull { it.source.id == id }?.source
        testSourceName = source?.name.orEmpty()
        val flow = if (source != null) viewModel.test(source.id) else null
        if (flow == null) {
            testState = MangaSourceTestState(
                popular = TestStep.Fail(context.getString(R.string.manga_test_no_source)),
                running = false,
            )
        } else {
            flow.collect { testState = it }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = contentPadding.calculateTopPadding()),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 8.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                    )
                }
            } else {
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = stringResource(R.string.manga_extensions_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
            )
            IconButton(onClick = { showFilters = true }) {
                Icon(Icons.Rounded.Tune, contentDescription = stringResource(R.string.manga_filters))
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.more))
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.manga_stores_title)) },
                        leadingIcon = { Icon(Icons.Rounded.Dns, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onOpenStores()
                        },
                    )
                }
            }
        }

        PrimaryTabRow(selectedTabIndex = tab) {
            Tab(
                selected = tab == 0,
                onClick = { tab = 0 },
                text = { Text(stringResource(R.string.manga_tab_installed, installed.size)) },
            )
            Tab(
                selected = tab == 1,
                onClick = { tab = 1 },
                text = { Text(stringResource(R.string.manga_tab_available)) },
            )
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            placeholder = { Text(stringResource(R.string.manga_search_extensions)) },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            trailingIcon = if (query.isNotEmpty()) {
                {
                    IconButton(onClick = { query = "" }) {
                        Icon(Icons.Rounded.Clear, contentDescription = stringResource(R.string.clear))
                    }
                }
            } else {
                null
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "all") {
                FilterChip(
                    selected = allLanguages,
                    onClick = { changeLanguages(languages, true) },
                    label = { Text(stringResource(R.string.manga_language_all)) },
                )
            }
            items(availableLanguages, key = { it }) { code ->
                FilterChip(
                    selected = !allLanguages && code in languages,
                    onClick = {
                        val next = if (code in languages) languages - code else languages + code
                        if (next.isNotEmpty()) changeLanguages(next, false)
                    },
                    label = { Text(mangaLanguageName(code)) },
                )
            }
        }

        Box(Modifier.weight(1f)) {
            when (tab) {
                0 -> InstalledTab(
                    installed = shownInstalled,
                    allInstalled = installed,
                    available = available,
                    loading = loading,
                    refreshing = refreshing,
                    installing = installing,
                    bottomPadding = contentPadding.calculateBottomPadding(),
                    updateCount = updateCount,
                    onBrowse = { tab = 1 },
                    onRefresh = viewModel::refresh,
                    onUpdate = viewModel::install,
                    onUpdateAll = viewModel::updateAll,
                    onSettings = onOpenSourcePreferences,
                    onDetails = { detailsTarget = it },
                    onTrust = { trustTarget = it },
                    onUninstall = { uninstallTarget = it },
                )

                else -> AvailableTab(
                    filtersActive = !allLanguages || languages.isNotEmpty() || showNsfw || query.isNotBlank(),
                    available = shownAvailable,
                    hasAvailable = newestListings.isNotEmpty(),
                    installed = installed,
                    availableAll = available,
                    refreshing = refreshing,
                    error = storeError,
                    installing = installing,
                    bottomPadding = contentPadding.calculateBottomPadding(),
                    onRefresh = viewModel::refresh,
                    onOpenStores = onOpenStores,
                    onOpenFilters = { showFilters = true },
                    onInstall = viewModel::install,
                )
            }
        }
    }

    if (showFilters) {
        MangaFiltersSheet(
            showNsfw = showNsfw,
            onNsfwChange = changeNsfw,
            allLanguages = allLanguages,
            languages = languages,
            onOpenLanguages = {
                showFilters = false
                showLanguageDialog = true
            },
            onDismiss = { showFilters = false },
        )
    }

    if (showLanguageDialog) {
        MangaLanguageDialog(
            languages = availableLanguages,
            selected = languages,
            allLanguages = allLanguages,
            onDismiss = { showLanguageDialog = false },
            onSave = changeLanguages,
        )
    }

    detailsTarget?.let { extension ->
        MangaExtensionDetailsSheet(
            extension = extension,
            listing = MangaFilters.findAnyListing(extension.pkgName, available),
            testableSourceIds = testableSourceIds,
            onTest = { id ->
                detailsTarget = null
                viewModel.startTest(id)
            },
            onTrust = (extension as? MangaExtension.NotLoaded)
                ?.takeIf { it.reason is MangaExtension.NotLoaded.Reason.Untrusted }
                ?.let { target -> { trustTarget = target } },
            onCopy = { copyToClipboard(context, it) },
            onDismiss = { detailsTarget = null },
        )
    }

    uninstallTarget?.let { extension ->
        MangaUninstallDialog(
            extension = extension,
            onDismiss = { uninstallTarget = null },
            onConfirm = { clearData ->
                viewModel.uninstall(extension, clearData)
                uninstallTarget = null
            },
        )
    }

    trustTarget?.let { extension ->
        MangaTrustDialog(
            extension = extension,
            onDismiss = { trustTarget = null },
            onConfirm = {
                viewModel.trust(extension)
                trustTarget = null
            },
        )
    }

    if (testingSourceId != null) {
        MangaSourceTestDialog(
            sourceName = testSourceName,
            state = testState,
            onCopy = { copyToClipboard(context, it) },
            onDismiss = {
                viewModel.stopTest()
                testState = null
            },
        )
    }
}

@Composable
private fun InstalledTab(
    installed: List<MangaExtension.Installed>,
    allInstalled: List<MangaExtension.Installed>,
    available: List<AvailableExtension>,
    loading: Boolean,
    refreshing: Boolean,
    installing: Map<String, InstallStep>,
    bottomPadding: androidx.compose.ui.unit.Dp,
    updateCount: Int,
    onBrowse: () -> Unit,
    onRefresh: () -> Unit,
    onUpdate: (AvailableExtension) -> Unit,
    onUpdateAll: () -> Unit,
    onSettings: (Long) -> Unit,
    onDetails: (MangaExtension.Installed) -> Unit,
    onTrust: (MangaExtension.NotLoaded) -> Unit,
    onUninstall: (MangaExtension.Installed) -> Unit,
) {
    when {
        loading && allInstalled.isEmpty() -> ExtensionSkeletonList(bottomPadding)
        allInstalled.isEmpty() -> EmptyBox(
            title = stringResource(R.string.manga_no_installed_title),
            hint = stringResource(R.string.manga_no_installed_hint),
        ) {
            Button(onClick = onBrowse, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.manga_tab_available))
            }
        }
        installed.isEmpty() -> EmptyBox(
            title = stringResource(R.string.manga_no_filtered_title),
            hint = stringResource(R.string.manga_no_filtered_hint),
        )
        else -> ExpressivePullToRefresh(isRefreshing = refreshing, onRefresh = onRefresh) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottomPadding + 16.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (updateCount > 1) {
                    item(key = "update-all") {
                        FilledTonalButton(
                            onClick = onUpdateAll,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp),
                        ) {
                            Text(stringResource(R.string.manga_update_all_n, updateCount))
                        }
                    }
                }
                items(installed, key = { it.pkgName }) { extension ->
                    val listing = remember(extension, available) {
                        MangaFilters.findAnyListing(extension.pkgName, available)
                    }
                    val update = remember(extension, available) {
                        MangaFilters.findListing(extension.pkgName, available, extension.signatures)
                            ?.takeIf {
                                MangaFilters.hasUpdate(
                                    extension.versionCode,
                                    extension.libVersion,
                                    it,
                                )
                            }
                    }
                    val configurableId = (extension as? MangaExtension.Loaded)
                        ?.sources
                        ?.firstOrNull { it is ConfigurableSource }
                        ?.id
                    InstalledExtensionCard(
                        extension = extension,
                        listing = listing,
                        update = update,
                        configurableSourceId = configurableId,
                        step = installing[extension.pkgName],
                        onInstall = { update?.let(onUpdate) },
                        onSettings = onSettings,
                        onDetails = { onDetails(extension) },
                        onTrust = (extension as? MangaExtension.NotLoaded)
                            ?.takeIf { it.reason is MangaExtension.NotLoaded.Reason.Untrusted }
                            ?.let { { onTrust(it) } },
                        onUninstall = { onUninstall(extension) },
                    )
                }
            }
        }
    }
}

@Composable
private fun AvailableTab(
    filtersActive: Boolean,
    available: List<AvailableExtension>,
    hasAvailable: Boolean,
    installed: List<MangaExtension.Installed>,
    availableAll: List<AvailableExtension>,
    refreshing: Boolean,
    error: String?,
    installing: Map<String, InstallStep>,
    bottomPadding: androidx.compose.ui.unit.Dp,
    onRefresh: () -> Unit,
    onOpenStores: () -> Unit,
    onOpenFilters: () -> Unit,
    onInstall: (AvailableExtension) -> Unit,
) {
    when {
        refreshing && !hasAvailable -> ExtensionSkeletonList(bottomPadding)
        error != null && !hasAvailable -> ErrorBox(
            message = error,
            onRetry = onRefresh,
        )
        !hasAvailable -> EmptyBox(
            title = stringResource(R.string.manga_no_available_title),
            hint = stringResource(R.string.manga_no_available_hint),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onRefresh, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.retry))
                }
                FilledTonalButton(onClick = onOpenStores, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.manga_stores_title))
                }
            }
        }
        available.isEmpty() -> EmptyBox(
            title = stringResource(R.string.manga_no_filtered_title),
            hint = stringResource(R.string.manga_no_filtered_hint),
        ) {
            if (filtersActive) {
                FilledTonalButton(onClick = onOpenFilters, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.manga_filters))
                }
            }
        }
        else -> ExpressivePullToRefresh(isRefreshing = refreshing, onRefresh = onRefresh) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottomPadding + 16.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (error != null) {
                    item(key = "error") {
                        StoreErrorBanner(message = error, onRetry = onRefresh)
                    }
                }
                items(available, key = { it.pkgName }) { extension ->
                    val local = installed.firstOrNull { it.pkgName == extension.pkgName }
                    val update = local?.let {
                        MangaFilters.findListing(it.pkgName, availableAll, it.signatures)
                            ?.takeIf { listing ->
                                MangaFilters.hasUpdate(it.versionCode, it.libVersion, listing)
                            }
                    }
                    AvailableExtensionCard(
                        extension = extension,
                        installed = local != null,
                        update = update,
                        step = installing[extension.pkgName],
                        onInstall = { onInstall(update ?: extension) },
                    )
                }
            }
        }
    }
}

@Composable
private fun InstalledExtensionCard(
    extension: MangaExtension.Installed,
    listing: AvailableExtension?,
    update: AvailableExtension?,
    configurableSourceId: Long?,
    step: InstallStep?,
    onInstall: () -> Unit,
    onSettings: (Long) -> Unit,
    onDetails: () -> Unit,
    onTrust: (() -> Unit)?,
    onUninstall: () -> Unit,
) {
    val sourceCount = (extension as? MangaExtension.Loaded)?.sources?.size
        ?: listing?.sources?.size
        ?: 0
    val language = extension.lang?.takeIf { it.isNotBlank() }?.let { mangaLanguageName(it) }
    val sourcesLabel = when {
        sourceCount <= 0 -> null
        sourceCount == 1 -> stringResource(R.string.manga_source_singular)
        else -> stringResource(R.string.manga_sources_n, sourceCount)
    }
    val metadata = listOfNotNull(
        "v" + extension.versionName.ifBlank { extension.versionCode.toString() },
        language,
        sourcesLabel,
    ).joinToString("  •  ")
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ExtensionIcon(iconUrl = listing?.iconUrl, contentDescription = extension.name)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = extension.name,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = metadata,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    ExtensionStateChip(extension = extension, hasUpdate = update != null)
                }
                if (step != null) {
                    InstallProgress(step)
                }
            }
            if (step == null || step is InstallStep.Error) {
                ExtensionActionRow(
                    update = update != null,
                    onInstall = onInstall,
                    onSettings = configurableSourceId?.let { { onSettings(it) } },
                    onDetails = onDetails,
                    onTrust = onTrust,
                    onUninstall = onUninstall,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExtensionActionRow(
    update: Boolean,
    onInstall: () -> Unit,
    onSettings: (() -> Unit)?,
    onDetails: () -> Unit,
    onTrust: (() -> Unit)?,
    onUninstall: () -> Unit,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (update) {
            TextButton(onClick = onInstall, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.update))
            }
        }
        onTrust?.let {
            TextButton(onClick = it, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.manga_trust))
            }
        }
        onSettings?.let {
            TextButton(onClick = it, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.manga_settings))
            }
        }
        TextButton(onClick = onDetails, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.manga_details))
        }
        TextButton(
            onClick = onUninstall,
            modifier = Modifier.heightIn(min = 48.dp),
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
        ) {
            Text(stringResource(R.string.uninstall))
        }
    }
}

@Composable
private fun AvailableExtensionCard(
    extension: AvailableExtension,
    installed: Boolean,
    update: AvailableExtension?,
    step: InstallStep?,
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
            ExtensionIcon(iconUrl = extension.iconUrl, contentDescription = extension.name)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = extension.name,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    when (extension.contentWarning) {
                        ContentWarning.NSFW -> WarningBadge(stringResource(R.string.manga_badge_nsfw))
                        ContentWarning.MIXED -> WarningBadge(stringResource(R.string.manga_badge_mixed))
                        ContentWarning.SAFE -> Unit
                    }
                }
                Text(
                    text = "v${extension.versionName}  •  ${mangaLanguageName(extension.lang)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = extension.storeName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            when {
                step != null -> InstallProgress(step)
                update != null -> Button(
                    onClick = onInstall,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(stringResource(R.string.update))
                }
                installed -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        Icons.Rounded.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = stringResource(R.string.installed),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                else -> FilledTonalButton(
                    onClick = onInstall,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(stringResource(R.string.install))
                }
            }
        }
    }
}

@Composable
private fun ExtensionStateChip(extension: MangaExtension.Installed, hasUpdate: Boolean) {
    val failed = extension is MangaExtension.NotLoaded
    val text = when {
        failed -> when ((extension as MangaExtension.NotLoaded).reason) {
            is MangaExtension.NotLoaded.Reason.Untrusted ->
                stringResource(R.string.manga_state_untrusted)
            MangaExtension.NotLoaded.Reason.Malformed ->
                stringResource(R.string.manga_state_malformed)
            MangaExtension.NotLoaded.Reason.UnsupportedLibVersion ->
                stringResource(R.string.manga_state_unsupported_lib)
            MangaExtension.NotLoaded.Reason.Unsigned ->
                stringResource(R.string.manga_state_unsigned)
            MangaExtension.NotLoaded.Reason.Filtered ->
                stringResource(R.string.manga_state_filtered)
            is MangaExtension.NotLoaded.Reason.Failed ->
                stringResource(R.string.manga_state_failed)
        }
        hasUpdate -> stringResource(R.string.manga_state_update)
        else -> stringResource(R.string.manga_state_ok)
    }
    val container = when {
        failed -> MaterialTheme.colorScheme.errorContainer
        hasUpdate -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val content = when {
        failed -> MaterialTheme.colorScheme.onErrorContainer
        hasUpdate -> MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        shape = MaterialTheme.shapes.small,
        color = container,
        contentColor = content,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun WarningBadge(text: String) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        modifier = Modifier.padding(start = 8.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun ExtensionIcon(iconUrl: String?, contentDescription: String) {
    if (!iconUrl.isNullOrBlank()) {
        RemoteImage(
            url = iconUrl,
            headers = null,
            contentDescription = contentDescription,
            modifier = Modifier
                .size(44.dp)
                .clip(MaterialTheme.shapes.small),
        )
    } else {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(MaterialTheme.shapes.small),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxSize(),
            ) {}
            Icon(
                Icons.Rounded.Extension,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@Composable
private fun InstallProgress(step: InstallStep, modifier: Modifier = Modifier) {
    val label = when (step) {
        InstallStep.Pending -> stringResource(R.string.manga_install_pending)
        is InstallStep.Downloading -> stringResource(R.string.manga_install_percent, step.percent)
        InstallStep.Installing -> stringResource(R.string.manga_installing)
        InstallStep.Installed -> stringResource(R.string.installed)
        is InstallStep.Error -> step.message
    }
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (step !is InstallStep.Installed && step !is InstallStep.Error) {
            LoadingIndicator(Modifier.size(20.dp))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (step is InstallStep.Error) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

@Composable
private fun ExtensionSkeletonList(bottomPadding: androidx.compose.ui.unit.Dp) {
    SkeletonPulseProvider {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottomPadding),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            repeat(5) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        SkeletonBox(
                            modifier = Modifier.size(44.dp),
                            shape = MaterialTheme.shapes.small,
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            SkeletonTextLine(width = 160.dp, height = 14.dp)
                            SkeletonTextLine(width = 100.dp, height = 11.dp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StoreErrorBanner(message: String, onRetry: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.retry))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MangaFiltersSheet(
    showNsfw: Boolean,
    onNsfwChange: (Boolean) -> Unit,
    allLanguages: Boolean,
    languages: Set<String>,
    onOpenLanguages: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
        ) {
            Text(
                text = stringResource(R.string.manga_filters),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .clickable { onNsfwChange(!showNsfw) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.manga_show_nsfw),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = stringResource(R.string.manga_show_nsfw_hint),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = showNsfw, onCheckedChange = onNsfwChange)
            }
            HorizontalDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .clickable(onClick = onOpenLanguages)
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.manga_extension_languages),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = if (allLanguages) {
                            stringResource(R.string.manga_language_all)
                        } else {
                            stringResource(R.string.manga_language_selected_n, languages.size)
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MangaExtensionDetailsSheet(
    extension: MangaExtension.Installed,
    listing: AvailableExtension?,
    testableSourceIds: Set<Long>,
    onTest: (Long) -> Unit,
    onTrust: (() -> Unit)?,
    onCopy: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val loadedSources = (extension as? MangaExtension.Loaded)?.sources.orEmpty()
    val sources = remember(listing, loadedSources) {
        if (listing != null) {
            listing.sources.map {
                SourceDetail(it.id, it.name, it.language, it.baseUrl, it.mirrorUrls, it.message)
            }
        } else {
            loadedSources.map {
                SourceDetail(
                    id = it.id,
                    name = it.name,
                    language = it.lang,
                    homeUrl = (it as? HttpSource)?.baseUrl.orEmpty(),
                    mirrors = emptyList(),
                    message = null,
                )
            }
        }
    }
    val hasFailed = (extension as? MangaExtension.NotLoaded)?.reason as?
        MangaExtension.NotLoaded.Reason.Failed
    val untrusted = (extension as? MangaExtension.NotLoaded)?.reason as?
        MangaExtension.NotLoaded.Reason.Untrusted

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = extension.name,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(top = 8.dp),
            )
            if (hasFailed != null) {
                DetailLine(
                    label = stringResource(R.string.manga_details_reason),
                    value = hasFailed.message.ifBlank {
                        stringResource(R.string.manga_state_failed)
                    },
                )
                Text(
                    text = hasFailed.stackTrace,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 12,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(
                    onClick = { onCopy(hasFailed.stackTrace) },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(stringResource(R.string.manga_details_copy))
                }
            }
            if (untrusted != null) {
                DetailLine(
                    label = stringResource(R.string.manga_details_signature),
                    value = signaturePreview(untrusted.signature),
                )
                onTrust?.let {
                    TextButton(onClick = it, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.manga_trust))
                    }
                }
            }
            DetailLine(
                label = stringResource(R.string.manga_details_package),
                value = extension.pkgName,
            )
            DetailLine(
                label = stringResource(R.string.manga_details_version),
                value = "v${extension.versionName} (${extension.versionCode})",
            )
            extension.libVersion?.let {
                DetailLine(
                    label = stringResource(R.string.manga_details_lib),
                    value = it.toString(),
                )
            }
            val storeLabel = extension.store?.let { it.badgeLabel.ifBlank { it.name } }
                ?: listing?.let { it.badgeLabel.ifBlank { it.storeName } }
            storeLabel?.let {
                DetailLine(
                    label = stringResource(R.string.manga_details_store),
                    value = it,
                )
            }
            DetailLine(
                label = stringResource(R.string.manga_details_signature),
                value = if (extension is MangaExtension.NotLoaded) {
                    stringResource(R.string.manga_details_signature_untrusted)
                } else {
                    stringResource(R.string.manga_details_signature_trusted)
                },
            )
            if (sources.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.manga_details_sources, sources.size),
                    style = MaterialTheme.typography.titleMedium,
                )
                sources.forEach { source ->
                    HorizontalDivider()
                    Text(
                        text = source.name,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Text(
                        text = stringResource(
                            R.string.manga_details_source_meta,
                            mangaLanguageName(source.language),
                            source.id,
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (source.homeUrl.isNotBlank()) {
                        DetailLine(
                            label = stringResource(R.string.manga_details_source_home),
                            value = source.homeUrl,
                        )
                    }
                    if (source.mirrors.isNotEmpty()) {
                        DetailLine(
                            label = stringResource(R.string.manga_details_source_mirrors),
                            value = source.mirrors.joinToString("\n"),
                        )
                    }
                    source.message?.takeIf { it.isNotBlank() }?.let {
                        DetailLine(
                            label = stringResource(R.string.manga_details_source_message),
                            value = it,
                        )
                    }
                    if (source.id in testableSourceIds) {
                        val testLabel = stringResource(R.string.manga_test_source_named, source.name)
                        TextButton(
                            onClick = { onTest(source.id) },
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .semantics { contentDescription = testLabel },
                        ) {
                            Text(stringResource(R.string.manga_test_source))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun MangaUninstallDialog(
    extension: MangaExtension.Installed,
    onDismiss: () -> Unit,
    onConfirm: (Boolean) -> Unit,
) {
    var clearData by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.manga_uninstall_title, extension.name)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.manga_uninstall_message))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clickable { clearData = !clearData },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = clearData, onCheckedChange = { clearData = it })
                    Text(
                        text = stringResource(R.string.manga_uninstall_clear_data),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(clearData) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text(stringResource(R.string.uninstall))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun MangaTrustDialog(
    extension: MangaExtension.NotLoaded,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val signature = (extension.reason as? MangaExtension.NotLoaded.Reason.Untrusted)?.signature
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.manga_trust_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.manga_trust_warning))
                DetailLine(
                    label = stringResource(R.string.manga_details_package),
                    value = extension.pkgName,
                )
                DetailLine(
                    label = stringResource(R.string.manga_details_version),
                    value = "v${extension.versionName} (${extension.versionCode})",
                )
                signature?.let {
                    DetailLine(
                        label = stringResource(R.string.manga_details_signature),
                        value = signaturePreview(it),
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onConfirm, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.manga_trust))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun MangaSourceTestDialog(
    sourceName: String,
    state: MangaSourceTestState?,
    onCopy: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.manga_test_title)) },
        text = {
            Column(
                Modifier
                    .heightIn(max = 400.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                if (sourceName.isNotBlank()) {
                    Text(
                        text = sourceName,
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                if (state == null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        LoadingIndicator(Modifier.size(20.dp))
                        Text(stringResource(R.string.manga_test_starting))
                    }
                } else {
                    TestStepRow(
                        title = stringResource(R.string.manga_test_step_popular),
                        step = state.popular,
                        onCopy = onCopy,
                    )
                    TestStepRow(
                        title = stringResource(R.string.manga_test_step_details),
                        step = state.details,
                        onCopy = onCopy,
                    )
                    TestStepRow(
                        title = stringResource(R.string.manga_test_step_pages),
                        step = state.pages,
                        onCopy = onCopy,
                    )
                    TestStepRow(
                        title = stringResource(R.string.manga_test_step_image),
                        step = state.image,
                        onCopy = onCopy,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.manga_test_close))
            }
        },
    )
}

@Composable
private fun TestStepRow(title: String, step: TestStep, onCopy: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when (step) {
                TestStep.Running -> LoadingIndicator(Modifier.size(16.dp))
                TestStep.Skipped -> Icon(
                    Icons.Rounded.Remove,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
                is TestStep.Pass -> Icon(
                    Icons.Rounded.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
                is TestStep.Fail -> Icon(
                    Icons.Rounded.Close,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(16.dp),
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = when (step) {
                    TestStep.Running -> stringResource(R.string.manga_test_running)
                    TestStep.Skipped -> stringResource(R.string.manga_test_skipped)
                    is TestStep.Pass -> stringResource(R.string.manga_test_passed)
                    is TestStep.Fail -> stringResource(R.string.manga_test_failed)
                },
                style = MaterialTheme.typography.labelSmall,
                color = when (step) {
                    is TestStep.Fail -> MaterialTheme.colorScheme.error
                    is TestStep.Pass -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        when (step) {
            is TestStep.Pass -> step.lines.forEach { line ->
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 24.dp),
                )
            }
            is TestStep.Fail -> {
                Text(
                    text = step.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(start = 24.dp),
                )
                step.detail?.let { detail ->
                    TextButton(
                        onClick = { onCopy(detail) },
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        Text(stringResource(R.string.manga_test_copy))
                    }
                }
            }
            TestStep.Running, TestStep.Skipped -> Unit
        }
    }
}

private data class SourceDetail(
    val id: Long,
    val name: String,
    val language: String,
    val homeUrl: String,
    val mirrors: List<String>,
    val message: String?,
)

private fun signaturePreview(signature: String): String =
    if (signature.length > 16) "${signature.take(8)}…${signature.takeLast(8)}" else signature

private fun copyToClipboard(context: Context, text: String) {
    (context.getSystemService(ClipboardManager::class.java))
        ?.setPrimaryClip(ClipData.newPlainText("KodaStream", text))
    Toast.makeText(context, context.getString(R.string.manga_details_copied), Toast.LENGTH_SHORT).show()
}
