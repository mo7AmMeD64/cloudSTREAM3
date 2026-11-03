package com.kodastream.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import android.app.Application
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.kodastream.app.R
import com.kodastream.app.data.AppEvents
import com.kodastream.app.data.AppPrefs
import com.kodastream.app.data.DetailsArgs
import com.kodastream.app.data.NavArgs
import com.kodastream.app.data.PlayerArgs
import com.kodastream.app.manga.MangaPrefs
import com.kodastream.app.manga.MangaRef
import com.kodastream.app.manga.reader.MangaReaderActivity
import com.kodastream.app.manga.extension.MangaSourceManager
import com.kodastream.app.manga.store.MangaStoreRepository
import com.kodastream.app.ui.components.softSpring
import com.kodastream.app.ui.details.DetailsScreen
import com.kodastream.app.ui.details.DetailsViewModel
import com.kodastream.app.ui.extensions.ExtensionsScreen
import com.kodastream.app.ui.history.HistoryScreen
import com.kodastream.app.ui.home.ExpandedHomeViewModel
import com.kodastream.app.ui.home.HomeScreen
import com.kodastream.app.ui.home.SectionScreen
import com.kodastream.app.ui.home.hero.LocalScreenActive
import com.kodastream.app.ui.library.LibraryScreen
import com.kodastream.app.ui.manga.MangaBrowseScreen
import com.kodastream.app.ui.manga.MangaDetailsScreen
import com.kodastream.app.ui.manga.MangaExtensionsScreen
import com.kodastream.app.ui.manga.MangaHistoryScreen
import com.kodastream.app.ui.manga.MangaLibraryScreen
import com.kodastream.app.ui.manga.MangaSearchScreen
import com.kodastream.app.ui.manga.MangaStoresScreen
import com.kodastream.app.ui.manga.SourcePreferencesScreen
import com.kodastream.app.ui.player.PlayerActivity
import com.kodastream.app.ui.search.SearchScreen
import com.kodastream.app.ui.settings.SettingsScreen
import com.kodastream.app.ui.sources.SourceSheet
import com.kodastream.app.ui.sources.SourceViewModel
import com.kodastream.app.ui.theme.ThemeViewModel
import com.lagradost.cloudstream3.SearchResponse

private object Routes {
    const val HOME = "home"
    const val SEARCH = "search"
    const val LIBRARY = "library"
    const val HISTORY = "history"
    const val EXTENSIONS = "extensions"
    const val SETTINGS = "settings"
    const val SECTION = "section"
    const val DETAILS = "details/{id}"
}

/** Manga navigation stays on its own graph; no route here is used by the video side. */
/** Set on the browse entry when a genre chip sends the user there. */
private const val GenreQueryKey = "manga_genre_query"
private const val GenreSourceKey = "manga_genre_source"

private object MangaRoutes {
    const val BROWSE = "manga_browse"
    const val SEARCH = "manga_search"
    const val LIBRARY = "manga_library"
    const val HISTORY = "manga_history"
    const val EXTENSIONS = "manga_extensions"
    const val SETTINGS = "manga_settings"
    const val STORES = "manga_stores"
    const val SOURCE = "manga_source/{id}"
    const val DETAILS = "manga_details/{id}"

    fun source(id: Long) = "manga_source/$id"

    fun details(ref: MangaRef) = "manga_details/${NavArgs.put(ref)}"
}

private data class TopLevel(
    val route: String,
    val labelRes: Int,
    val filledIcon: ImageVector,
    val outlinedIcon: ImageVector,
)

private val watchTopLevelDestinations = listOf(
    TopLevel(Routes.HOME, R.string.nav_home, Icons.Rounded.Home, Icons.Outlined.Home),
    TopLevel(Routes.SEARCH, R.string.nav_search, Icons.Rounded.Search, Icons.Outlined.Search),
    TopLevel(Routes.LIBRARY, R.string.nav_library, Icons.Rounded.VideoLibrary, Icons.Outlined.VideoLibrary),
    TopLevel(Routes.HISTORY, R.string.nav_history, Icons.Rounded.History, Icons.Outlined.History),
    TopLevel(Routes.EXTENSIONS, R.string.nav_extensions, Icons.Rounded.Extension, Icons.Outlined.Extension),
    TopLevel(Routes.SETTINGS, R.string.nav_settings, Icons.Rounded.Settings, Icons.Outlined.Settings),
)

private val mangaTopLevelDestinations = listOf(
    TopLevel(MangaRoutes.BROWSE, R.string.nav_browse, Icons.Rounded.Explore, Icons.Outlined.Explore),
    TopLevel(MangaRoutes.SEARCH, R.string.nav_search, Icons.Rounded.Search, Icons.Outlined.Search),
    TopLevel(
        MangaRoutes.LIBRARY,
        R.string.nav_library,
        Icons.AutoMirrored.Rounded.MenuBook,
        Icons.AutoMirrored.Outlined.MenuBook,
    ),
    TopLevel(MangaRoutes.HISTORY, R.string.nav_history, Icons.Rounded.History, Icons.Outlined.History),
    TopLevel(
        MangaRoutes.EXTENSIONS,
        R.string.nav_extensions,
        Icons.Rounded.Extension,
        Icons.Outlined.Extension,
    ),
    TopLevel(MangaRoutes.SETTINGS, R.string.nav_settings, Icons.Rounded.Settings, Icons.Outlined.Settings),
)

// ------------------------------------------------------------------ shared nav motion

/**
 * The animation language both navigation bars speak, extracted so the side
 * pill and the bottom pill feel identical: colours cross-fade on one 200ms
 * tween, the item's inner padding and the label's appearance/disappearance
 * ride the expressive fast-spatial spring, and the label shows only for the
 * open tab. The bars differ only in the axis the active pill expands on -
 * horizontally in the bottom bar with the label beside the icon, vertically
 * in the side bar with the label under it.
 */
private const val NAV_COLOR_MS = 200

private val NavItemPaddingCollapsed = 12.dp
private val NavItemPaddingExpanded = 20.dp

/** The shared size of every nav icon, active or not. */
private val NavIconSize = 24.dp

/** The gap between an item's icon and its expanding label. */
private val NavLabelGap = 8.dp

@Composable
private fun navItemPadding(selected: Boolean): Dp =
    animateDpAsState(
        targetValue = if (selected) NavItemPaddingExpanded else NavItemPaddingCollapsed,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "navItemPadding",
    ).value

@Composable
private fun navContainerColor(selected: Boolean, activeContainer: Color): Color =
    animateColorAsState(
        targetValue = if (selected) activeContainer else Color.Transparent,
        animationSpec = tween(durationMillis = NAV_COLOR_MS),
        label = "navContainerColor",
    ).value

@Composable
private fun navContentColor(selected: Boolean, activeContent: Color): Color =
    animateColorAsState(
        targetValue = if (selected) activeContent else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(durationMillis = NAV_COLOR_MS),
        label = "navContentColor",
    ).value

// The side bar's fixed geometry: the pill is 64dp wide, its items get a
// 48dp touch target inside, and the whole bar floats 14dp off the edge.
private val SideBarWidth = 64.dp
private val SideBarMargin = 14.dp

private fun NavHostController.navigateTopLevel(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private fun NavHostController.openDetails(args: DetailsArgs) {
    navigate("details/${NavArgs.put(args)}")
}

private fun NavHostController.openDetails(item: SearchResponse) {
    openDetails(
        DetailsArgs(
            apiName = item.apiName,
            url = item.url,
            title = item.name,
            posterUrl = item.posterUrl,
            posterHeaders = item.posterHeaders,
        )
    )
}

@Composable
fun AppRoot(themeViewModel: ThemeViewModel) {
    val context = LocalContext.current
    val watchNavController = rememberNavController()
    val mangaNavController = rememberNavController()

    var appMode by remember {
        mutableStateOf(
            if (MangaPrefs.mangaSectionEnabled(context)) AppPrefs.appMode(context)
            else AppPrefs.MODE_WATCH
        )
    }
    var mangaSection by remember { mutableStateOf(MangaPrefs.mangaSectionEnabled(context)) }
    val mangaActive = mangaSection && appMode == AppPrefs.MODE_MANGA
    val destinations = if (mangaActive) mangaTopLevelDestinations else watchTopLevelDestinations
    val navController = if (mangaActive) mangaNavController else watchNavController
    val backStackEntry by navController.currentBackStackEntryAsState()
    val route = backStackEntry?.destination?.route
    val showBar = destinations.any { it.route == route }
    val sourceVisible = !mangaActive && (route == Routes.HOME || route == Routes.SEARCH)

    val sources: SourceViewModel = viewModel()
    // Activity-scoped: paged Home sections survive rotation and Back to Home.
    val expanded: ExpandedHomeViewModel = viewModel()

    // The source sheet is hosted here so both routes and both shells share it.
    var sourceSheetOpen by remember { mutableStateOf(false) }
    val sourceList by sources.providers.collectAsState()
    val homeSource by sources.home.collectAsState()
    val searchScope by sources.search.collectAsState()

    val setMangaSection: (Boolean) -> Unit = { enabled ->
        mangaSection = enabled
        if (!enabled) {
            appMode = AppPrefs.MODE_WATCH
            AppPrefs.setAppMode(context, AppPrefs.MODE_WATCH)
        }
    }
    val switchMode: () -> Unit = {
        val next = if (mangaActive) AppPrefs.MODE_WATCH else AppPrefs.MODE_MANGA
        appMode = next
        AppPrefs.setAppMode(context, next)
        sourceSheetOpen = false
    }

    // Turning the manga section off always leaves Watch selected, including after a restart.
    LaunchedEffect(mangaSection) {
        if (!mangaSection && appMode == AppPrefs.MODE_MANGA) {
            appMode = AppPrefs.MODE_WATCH
            AppPrefs.setAppMode(context, AppPrefs.MODE_WATCH)
        }
    }

    // A repository link opened from outside the app: go to the Extensions tab, which shows the dialog.
    val pendingRepo by AppEvents.pendingRepoUrl.collectAsState()
    LaunchedEffect(pendingRepo) {
        if (pendingRepo != null) {
            appMode = AppPrefs.MODE_WATCH
            AppPrefs.setAppMode(context, AppPrefs.MODE_WATCH)
            watchNavController.navigateTopLevel(Routes.EXTENSIONS)
        }
    }

    // Installed extensions are loaded on Dispatchers.IO once manga mode is entered, never at app start.
    LaunchedEffect(mangaActive) {
        if (mangaActive) {
            val stores = MangaStoreRepository.get(context)
            stores.ensureDefaultStore()
            MangaSourceManager.get(context).ensureLoaded { stores.stores.value }
        }
    }

    val navPosition by AppPrefs.navPosition.collectAsState(initial = AppPrefs.navPosition(context))
    val sideMode = showBar && navPosition != AppPrefs.NAV_BOTTOM

    // Either shell hands the screens the same contract: system-bar insets,
    // plus the pill's strip (and the source button above it) when it is there.
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val contentPadding = when {
        !showBar -> PaddingValues(0.dp)
        sideMode -> PaddingValues(top = topInset, bottom = navInset)
        else -> PaddingValues(
            top = topInset,
            bottom = 84.dp + navInset + if (sourceVisible) 60.dp else 0.dp,
        )
    }

    Box(Modifier.fillMaxSize()) {
        // The bar mirrors with the locale: left in LTR, right in Arabic.
        // `start` on both the content padding and the bar keeps them on the
        // same side without direction-dependent branches.
        Box(
            Modifier
                .fillMaxSize()
                .then(
                    if (sideMode) Modifier.padding(start = SideBarWidth + SideBarMargin)
                    else Modifier
                )
        ) {
            if (mangaActive) {
                MangaNavGraph(
                    navController = mangaNavController,
                    themeViewModel = themeViewModel,
                    contentPadding = contentPadding,
                    onMangaSectionChanged = setMangaSection,
                )
            } else {
                AppNavGraph(
                    navController = watchNavController,
                    themeViewModel = themeViewModel,
                    contentPadding = contentPadding,
                    sources = sources,
                    expanded = expanded,
                    onOpenSources = { sourceSheetOpen = true },
                    onMangaSectionChanged = setMangaSection,
                )
            }
        }

        if (sideMode) {
            FloatingSideBar(
                destinations = destinations,
                selectedRoute = route,
                onSelect = { navController.navigateTopLevel(it) },
                sourceVisible = sourceVisible,
                onOpenSources = { sourceSheetOpen = true },
                showModeSwitch = mangaSection,
                inMangaMode = mangaActive,
                onToggleMode = switchMode,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .displayCutoutPadding()
                    .padding(start = SideBarMargin),
            )
        } else {
            AnimatedVisibility(
                visible = showBar,
                enter = fadeIn(tween(220)) +
                    slideInVertically(animationSpec = softSpring()) { it * 2 },
                exit = fadeOut(tween(180)) +
                    slideOutVertically(animationSpec = softSpring()) { it * 2 },
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                Column(
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(bottom = 20.dp),
                    horizontalAlignment = Alignment.End,
                ) {
                    // A separate button above the pill: attaching it to the
                    // toolbar would squeeze the six tabs on narrow screens.
                    AnimatedVisibility(
                        visible = sourceVisible,
                        enter = scaleIn(tween(220)) + fadeIn(tween(220)),
                        exit = scaleOut(tween(180)) + fadeOut(tween(180)),
                    ) {
                        SmallFloatingActionButton(
                            onClick = { sourceSheetOpen = true },
                            modifier = Modifier.padding(end = 16.dp, bottom = 12.dp),
                        ) {
                            Icon(
                                Icons.Rounded.Dns,
                                contentDescription = stringResource(R.string.cd_choose_source),
                            )
                        }
                    }
                    FloatingNavPill(
                        destinations = destinations,
                        selectedRoute = route,
                        onSelect = { navController.navigateTopLevel(it) },
                        showModeSwitch = mangaSection,
                        inMangaMode = mangaActive,
                        onToggleMode = switchMode,
                    )
                }
            }
        }
    }

    if (sourceSheetOpen) {
        val search = route == Routes.SEARCH
        SourceSheet(
            providers = sourceList,
            selected = if (search) searchScope else homeSource,
            showAllSources = search,
            onSelect = { name ->
                sourceSheetOpen = false
                if (search) sources.selectSearch(name) else if (name != null) sources.selectHome(name)
            },
            onOpenExtensions = {
                sourceSheetOpen = false
                navController.navigateTopLevel(Routes.EXTENSIONS)
            },
            onDismiss = { sourceSheetOpen = false },
        )
    }
}

/** The routes themselves, shared verbatim by both navigation shells. */
@Composable
private fun AppNavGraph(
    navController: NavHostController,
    themeViewModel: ThemeViewModel,
    contentPadding: PaddingValues,
    sources: SourceViewModel,
    expanded: ExpandedHomeViewModel,
    onOpenSources: () -> Unit,
    onMangaSectionChanged: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    fun openPlayer(playerArgs: PlayerArgs) {
        context.startActivity(PlayerActivity.newIntent(context, NavArgs.put(playerArgs)))
    }

    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
        modifier = Modifier.fillMaxSize(),
    ) {
        composable(Routes.HOME) { entry ->
            val lifecycleState by entry.lifecycle.currentStateFlow.collectAsState()
            CompositionLocalProvider(
                LocalScreenActive provides lifecycleState.isAtLeast(Lifecycle.State.RESUMED),
            ) {
                HomeScreen(
                    contentPadding = contentPadding,
                    onOpenItem = navController::openDetails,
                    onOpenExtensions = { navController.navigateTopLevel(Routes.EXTENSIONS) },
                    onOpenSources = onOpenSources,
                    sourceViewModel = sources,
                    expandedViewModel = expanded,
                    onOpenSection = { navController.navigate(Routes.SECTION) },
                )
            }
        }
        composable(Routes.SEARCH) {
            SearchScreen(
                contentPadding = contentPadding,
                onOpenItem = navController::openDetails,
                sourceViewModel = sources,
            )
        }
        composable(Routes.LIBRARY) {
            LibraryScreen(
                contentPadding = contentPadding,
                onOpenItem = navController::openDetails,
                onResume = { playerArgs -> openPlayer(playerArgs) },
            )
        }
        composable(Routes.HISTORY) {
            HistoryScreen(
                contentPadding = contentPadding,
                onOpenItem = navController::openDetails,
                onResume = { playerArgs -> openPlayer(playerArgs) },
            )
        }
        composable(Routes.EXTENSIONS) {
            ExtensionsScreen(contentPadding = contentPadding)
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                contentPadding = contentPadding,
                themeViewModel = themeViewModel,
                onOpenMangaExtensions = { navController.navigate(MangaRoutes.EXTENSIONS) },
                onOpenMangaStores = { navController.navigate(MangaRoutes.STORES) },
                onMangaSectionChanged = onMangaSectionChanged,
            )
        }
        composable(MangaRoutes.EXTENSIONS) {
            MangaExtensionsScreen(
                contentPadding = contentPadding,
                onOpenStores = { navController.navigate(MangaRoutes.STORES) },
                onOpenSourcePreferences = { navController.navigate(MangaRoutes.source(it)) },
                onBack = { navController.popBackStack() },
            )
        }
        mangaPushedRoutes(navController, contentPadding)
        composable(Routes.SECTION) {
            SectionScreen(
                viewModel = expanded,
                onBack = { navController.popBackStack() },
                onOpenItem = navController::openDetails,
            )
        }
        composable(
            route = Routes.DETAILS,
            arguments = listOf(navArgument("id") { type = NavType.IntType }),
        ) { entry ->
            val args = NavArgs.details(entry.arguments?.getInt("id") ?: -1)
            if (args == null) {
                // The process was recreated and the in-memory arguments are gone.
                LaunchedEffect(Unit) { navController.popBackStack() }
            } else {
                val viewModel: DetailsViewModel = viewModel(
                    factory = viewModelFactory { initializer { DetailsViewModel(args) } }
                )
                DetailsScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onOpenItem = navController::openDetails,
                    onOpenDownloads = { navController.navigateTopLevel(Routes.LIBRARY) },
                    onPlay = { playerArgs -> openPlayer(playerArgs) },
                )
            }
        }
    }
}

/**
 * The manga graph: its own start destination and back stack, so switching modes
 * keeps each side where it was. Only Extensions and Settings exist in Part 10.
 */
@Composable
private fun MangaNavGraph(
    navController: NavHostController,
    themeViewModel: ThemeViewModel,
    contentPadding: PaddingValues,
    onMangaSectionChanged: (Boolean) -> Unit,
) {
    NavHost(
        navController = navController,
        startDestination = MangaRoutes.BROWSE,
        modifier = Modifier.fillMaxSize(),
    ) {
        composable(MangaRoutes.BROWSE) { entry ->
            val genre = entry.savedStateHandle.get<String>(GenreQueryKey)
            val genreSource = entry.savedStateHandle.get<Long>(GenreSourceKey) ?: 0L
            MangaBrowseScreen(
                contentPadding = contentPadding,
                onOpenDetails = { ref -> navController.navigate(MangaRoutes.details(ref)) },
                onOpenExtensions = { navController.navigateTopLevel(MangaRoutes.EXTENSIONS) },
                pendingGenre = if (genre.isNullOrBlank()) null else genreSource to genre,
                onPendingGenreConsumed = {
                    entry.savedStateHandle.remove<String>(GenreQueryKey)
                    entry.savedStateHandle.remove<Long>(GenreSourceKey)
                },
            )
        }
        composable(MangaRoutes.SEARCH) {
            MangaSearchScreen(
                contentPadding = contentPadding,
                onOpenDetails = { ref -> navController.navigate(MangaRoutes.details(ref)) },
            )
        }
        composable(MangaRoutes.LIBRARY) {
            MangaLibraryScreen(
                contentPadding = contentPadding,
                onOpenDetails = { ref -> navController.navigate(MangaRoutes.details(ref)) },
                onOpenBrowse = { navController.navigateTopLevel(MangaRoutes.BROWSE) },
            )
        }
        composable(MangaRoutes.HISTORY) {
            val context = LocalContext.current
            MangaHistoryScreen(
                contentPadding = contentPadding,
                onOpenDetails = { ref -> navController.navigate(MangaRoutes.details(ref)) },
                onResume = { ref, chapterUrl, page ->
                    context.startActivity(
                        MangaReaderActivity.intent(context, ref, chapterUrl, page),
                    )
                },
            )
        }
        composable(MangaRoutes.EXTENSIONS) {
            MangaExtensionsScreen(
                contentPadding = contentPadding,
                onOpenStores = { navController.navigate(MangaRoutes.STORES) },
                onOpenSourcePreferences = { navController.navigate(MangaRoutes.source(it)) },
            )
        }
        composable(MangaRoutes.SETTINGS) {
            SettingsScreen(
                contentPadding = contentPadding,
                themeViewModel = themeViewModel,
                onOpenMangaExtensions = { navController.navigateTopLevel(MangaRoutes.EXTENSIONS) },
                onOpenMangaStores = { navController.navigate(MangaRoutes.STORES) },
                onMangaSectionChanged = onMangaSectionChanged,
            )
        }
        mangaPushedRoutes(navController, contentPadding)
        mangaDetailsRoute(navController, contentPadding)
    }
}

/** Routes both graphs push on top of their tabs; Back always returns. */
private fun NavGraphBuilder.mangaPushedRoutes(
    navController: NavHostController,
    contentPadding: PaddingValues,
) {
    composable(MangaRoutes.STORES) {
        MangaStoresScreen(
            contentPadding = contentPadding,
            onBack = { navController.popBackStack() },
        )
    }
    composable(
        route = MangaRoutes.SOURCE,
        arguments = listOf(navArgument("id") { type = NavType.LongType }),
    ) { entry ->
        SourcePreferencesScreen(
            sourceId = entry.arguments?.getLong("id") ?: 0L,
            onBack = { navController.popBackStack() },
        )
    }
}

/**
 * The manga details route. Arguments are in-memory like the video details;
 * after process death there is nothing to show, so Back is the only action.
 */
private fun NavGraphBuilder.mangaDetailsRoute(
    navController: NavHostController,
    contentPadding: PaddingValues,
) {
    composable(
        route = MangaRoutes.DETAILS,
        arguments = listOf(navArgument("id") { type = NavType.IntType }),
    ) { entry ->
        val ref = NavArgs.manga(entry.arguments?.getInt("id") ?: -1)
        if (ref == null) {
            LaunchedEffect(Unit) { navController.popBackStack() }
        } else {
            val context = LocalContext.current
            MangaDetailsScreen(
                ref = ref,
                contentPadding = contentPadding,
                onBack = { navController.popBackStack() },
                onOpenChapter = { chapter, page ->
                    context.startActivity(
                        MangaReaderActivity.intent(context, ref, chapter.url, page),
                    )
                },
                onSearchGenre = { genre ->
                    // Browse is the source's own search: land there with the
                    // genre typed in, on the source this title came from.
                    navController.navigateTopLevel(MangaRoutes.BROWSE)
                    navController.currentBackStackEntry?.savedStateHandle?.let { handle ->
                        handle[GenreQueryKey] = genre
                        handle[GenreSourceKey] = ref.sourceId
                    }
                },
            )
        }
    }
}

/**
 * The floating side pill: a stadium-ended vertical bar on the start edge
 * (left in LTR, right in Arabic), wrapping its content, over the page
 * rather than attached to it. Same five destinations, same icons, same
 * order as the bottom bar. Scrollable for short screens in landscape; the
 * insets in [modifier]'s chain cap its height to the space between the
 * status and nav bars.
 */
@Composable
private fun FloatingSideBar(
    destinations: List<TopLevel>,
    selectedRoute: String?,
    onSelect: (String) -> Unit,
    sourceVisible: Boolean,
    onOpenSources: () -> Unit,
    showModeSwitch: Boolean,
    inMangaMode: Boolean,
    onToggleMode: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier
                .width(SideBarWidth)
                .padding(horizontal = 8.dp, vertical = 10.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (showModeSwitch) {
                ModeSwitchButton(
                    inMangaMode = inMangaMode,
                    onToggle = onToggleMode,
                )
            }
            destinations.forEach { destination ->
                SideNavItem(
                    destination = destination,
                    selected = selectedRoute == destination.route,
                    onClick = { onSelect(destination.route) },
                )
            }
            AnimatedVisibility(
                visible = sourceVisible,
                enter = scaleIn(tween(220)) + fadeIn(tween(220)),
                exit = scaleOut(tween(180)) + fadeOut(tween(180)),
            ) {
                Surface(
                    onClick = onOpenSources,
                    shape = RoundedCornerShape(50),
                    color = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Box(
                        modifier = Modifier.height(48.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Rounded.Dns,
                            contentDescription = stringResource(R.string.cd_choose_source),
                            modifier = Modifier.size(NavIconSize),
                        )
                    }
                }
            }
        }
    }
}

/**
 * The compact mode switch that opens both navigation shells. The icon shows
 * where the tap goes; the state description names the mode that is open now.
 */
@Composable
private fun ModeSwitchButton(
    inMangaMode: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val action = stringResource(
        if (inMangaMode) R.string.manga_switch_to_watch else R.string.manga_switch_to_manga
    )
    val state = stringResource(
        if (inMangaMode) R.string.manga_mode_manga else R.string.manga_mode_watch
    )
    Surface(
        onClick = onToggle,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        modifier = modifier
            .size(48.dp)
            .semantics {
                contentDescription = action
                stateDescription = state
            },
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = if (inMangaMode) Icons.Rounded.VideoLibrary else Icons.Rounded.AutoStories,
                contentDescription = null,
                modifier = Modifier.size(NavIconSize),
            )
        }
    }
}

/**
 * One side-bar item: the open tab's pill expands vertically with its label
 * sliding in under the icon, on exactly the shared specs - the same 200ms
 * colour tween, the same fast-spatial spring, the same 12-to-20dp padding
 * growth - as the bottom bar's item.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SideNavItem(
    destination: TopLevel,
    selected: Boolean,
    onClick: () -> Unit,
) {    val animatedPadding = navItemPadding(selected)
    val animatedContainerColor = navContainerColor(
        selected = selected,
        activeContainer = MaterialTheme.colorScheme.secondaryContainer,
    )
    val animatedContentColor = navContentColor(
        selected = selected,
        activeContent = MaterialTheme.colorScheme.onSecondaryContainer,
    )

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = animatedContainerColor,
        contentColor = animatedContentColor,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .padding(vertical = animatedPadding)
                .animateContentSize(
                    animationSpec = MaterialTheme.motionScheme.fastSpatialSpec()
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = if (selected) destination.filledIcon else destination.outlinedIcon,
                contentDescription = stringResource(destination.labelRes),
                modifier = Modifier.size(NavIconSize),
            )
            // The pill names only the open tab, under its icon.
            AnimatedVisibility(
                visible = selected,
                enter = fadeIn() + expandVertically(
                    animationSpec = MaterialTheme.motionScheme.fastSpatialSpec()
                ),
                exit = fadeOut() + shrinkVertically(
                    animationSpec = MaterialTheme.motionScheme.fastSpatialSpec()
                ),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(modifier = Modifier.height(NavLabelGap))
                    SideBarLabel(text = stringResource(destination.labelRes))
                }
            }
        }
    }
}

/** The label column is 48dp wide; long labels and font scale 1.3 must still fit. */
private val SideLabelWidth = 48.dp

@Composable
private fun SideBarLabel(text: String) {
    val base = MaterialTheme.typography.labelSmall
    val measurer = rememberTextMeasurer()
    val available = with(LocalDensity.current) { SideLabelWidth.toPx() }
    val style = remember(text, base, available, measurer) {
        val width = measurer.measure(
            text = AnnotatedString(text),
            style = base,
            maxLines = 1,
            softWrap = false,
        ).size.width
        if (width <= 0 || width <= available) {
            base
        } else {
            // Shrink to the measured ratio, but never below an 8sp legible floor.
            base.copy(fontSize = (base.fontSize.value * available / width).coerceAtLeast(8f).sp)
        }
    }
    Text(
        text = text,
        style = style,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * The floating bottom pill: one tonal capsule, the open tab expanding to
 * say its name, the rest as icons. Its items use the same shared specs as
 * the side bar's, expanding on the horizontal axis instead.
 */@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FloatingNavPill(
    destinations: List<TopLevel>,
    selectedRoute: String?,
    onSelect: (String) -> Unit,
    showModeSwitch: Boolean,
    inMangaMode: Boolean,
    onToggleMode: () -> Unit,
) {
    HorizontalFloatingToolbar(
        expanded = true,
        content = {
            if (showModeSwitch) {
                ModeSwitchButton(
                    inMangaMode = inMangaMode,
                    onToggle = onToggleMode,
                )
            }
            destinations.forEach { destination ->
                val selected = selectedRoute == destination.route

                val animatedPadding = navItemPadding(selected)
                val animatedContainerColor = navContainerColor(
                    selected = selected,
                    activeContainer = MaterialTheme.colorScheme.primaryContainer,
                )
                val animatedContentColor = navContentColor(
                    selected = selected,
                    activeContent = MaterialTheme.colorScheme.onPrimaryContainer,
                )

                Surface(
                    onClick = { onSelect(destination.route) },
                    shape = CircleShape,
                    color = animatedContainerColor,
                    contentColor = animatedContentColor,
                    modifier = Modifier.height(48.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = animatedPadding)
                            .animateContentSize(
                                animationSpec = MaterialTheme.motionScheme.fastSpatialSpec()
                            ),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = if (selected) destination.filledIcon else destination.outlinedIcon,
                            contentDescription = stringResource(destination.labelRes),
                            modifier = Modifier.size(NavIconSize)
                        )
                        // The pill names only the open tab.
                        AnimatedVisibility(
                            visible = selected,
                            enter = fadeIn() + expandHorizontally(
                                animationSpec = MaterialTheme.motionScheme.fastSpatialSpec()
                            ),
                            exit = fadeOut() + shrinkHorizontally(
                                animationSpec = MaterialTheme.motionScheme.fastSpatialSpec()
                            ),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Spacer(modifier = Modifier.width(NavLabelGap))
                                Text(
                                    text = stringResource(destination.labelRes),
                                    style = MaterialTheme.typography.labelLarge,
                                )
                            }
                        }
                    }
                }
            }
        },
    )
}
