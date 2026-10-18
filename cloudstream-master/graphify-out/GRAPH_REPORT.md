# Graph Report - java  (2026-10-18)

## Corpus Check
- 265 files · ~199,987 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 6087 nodes · 16719 edges · 243 communities (114 shown, 129 thin omitted)
- Extraction: 95% EXTRACTED · 5% INFERRED · 0% AMBIGUOUS · INFERRED: 831 edges (avg confidence: 0.85)
- Token cost: 0 input · 0 output

## Community Hubs (Navigation)
- VideoGenerator & Utils
- Account Helpers
- Activity Intents
- MainActivity Bindings
- Cast & Audio Focus
- ViewBinding Dialogs
- AniList API
- Activity Options
- RecyclerView Adapters
- Download Click Handling
- Chromecast Setup
- Offline Playback Helpers
- MAL API
- TV Settings Chips
- Plugin Manager Activity
- Compose Theming
- Simkl Auth
- Prerelease UI
- DownloadQueueService
- CS3IPlayer Core
- Result Phone UI
- Fetch Buttons
- Home Fragment
- Home Parent Adapter
- Compose Text Fields
- GeneratorPlayer Core
- Trakt Sync
- Download Button Setup
- SyncViewModel
- Settings Compose
- Trakt Activity
- FixLayout Utilities
- TestViewModel
- AccountManager
- Library Transforms
- Trakt Search Results
- HomeViewModel
- Player Drawables
- Kitsu Interceptors
- Kitsu Episodes
- Downloads List Store
- Subtitle Resources
- Download Cache Metadata
- Settings Providers
- BottomSheet Fragments
- Backup Utils
- AbstractPlayerFragment
- Quality Profiles
- DownloadFileGenerator
- Extractors Factory
- PlayerSubtitleHelper
- Searchable Settings
- BaseFetchButton
- Preview Generator Math
- Theme Account Select
- Search Fragment
- FCast Discovery
- AniSkip Auth
- Download Deletion
- Resume Watching Store
- Playback State Handling
- PlayerGeneratorViewModel
- Coil Image Loader
- Media Notification
- IPlayer Interface
- QuickSearch Fragment
- Result Swipe Binding
- GeneratorPlayer Events
- Dynamic Range Compressor
- Download Adapter
- Repository Manager
- Extensions Fragment
- Offline Video Source
- Preference Fragments
- CustomSubripParser
- Embedded Subtitles Fetch
- Filler Episode Check
- Easter Egg
- CloudStreamApp
- Player Gesture Helper
- API Repository
- Palette Activity
- Torrent Serialization
- PluginsViewModel
- Biometric Utils
- Test Adapter
- Subtitle Style Fragment
- Torrent Models
- App Language
- Gesture Aspect Layouts
- Repository Data
- DoH DNS Providers
- CloudStreamPackage
- AniList Library
- Download Fragment
- Subtitle Parser Sync
- Lifecycle Observers
- Bottom Dialogs
- Subtitle Selection
- Auth Repos
- Account Screen
- Home Child Adapter
- Result Parent Adapter
- LibraryViewModel
- Account Adapter
- Library Fragment
- Progress Views
- Annotated Text Compose
- Custom Subtitle Decoder
- FullScreenPlayer Layout Animation
- ReloadViewModel
- Priority Adapters
- Quality Profile Dialog
- Share Utils
- OpenSubtitles
- Track Formats
- Plugin Adapter
- SubtitlesFragment
- APIHolder Test
- APK Updater
- Skip API Models
- ResultFragment
- ResultFragmentTV
- SkipAPI
- Cloudflare Killer
- Subtitle Enumerations
- PiP Helper
- WebView Fragment
- Cue Points
- Subscription WorkManager
- ViewPagerAdapter
- Search Config
- IPreviewGenerator
- Test Compose Screen
- SearchResponse Helpers
- Package Installer
- Chromecast Subtitles
- Subtitle Alignment
- Scrubbing
- SearchViewModel
- Subtitle Fonts
- Custom RecyclerViews
- Bitmap Drawables
- FCast Actions
- CommonActivity PiP
- TV Back Press
- DownloadedPlayerActivity
- Source Profiles
- Linear List Layout
- Search History
- GitHub Releases
- Cast Item Binding
- DownloadUtils
- Extension Loader
- Download JSON
- Subtitle Offset
- LinkGenerator
- FlowLayout
- Mini Controller
- Audio Sink
- FCast Session
- Video Link State
- GitHub API
- APK Installer
- Downloader Impl
- Backup WorkManager
- Service Stubs
- Loading Poster Adapter
- CSPlayer Events
- Animation Callbacks
- Percentage Crop Image
- VideoDownloadService
- Cast Session Manager
- CSPlayer Loading
- LazyStream Downloader
- URI Serializer
- CenterZoom Layout
- AniSkip Intervals
- EBML Processor
- Search Suggestion API
- IntroDB Skip
- RepoLinkGenerator
- LiveManager
- DdosGuard Killer
- NonFinal Adapters
- Live Preview TimeBar
- Setup Extensions
- ConsistentLiveData
- Rounded Spans
- Simkl Cache
- Forced Encoding
- Episode Overlay
- IGenerator
- DynamicString
- IntroDB Skip Models
- Cutout Overlay
- BasePlugin
- Voting API
- LocalList
- Account Auth Activity
- VideoGenerator Activity
- ExtractorLinkGenerator
- Select Adaptor
- Search Suggestion Adapter
- Settings Account Compose
- Searchable Settings Compose
- Vector2
- Auth Account Select
- Account Spacing
- Preview Images
- SSL Trust Manager
- Sync Search
- Download Type
- OutlineSpan
- ACRA Application
- Progress Bar Animation
- Profile Settings
- Animation Events
- TextUtil
- Linear Recycler
- Focus Direction
- Tab Selection
- IntDef Flags
- IDisposable
- Install Progress
- Load Popup
- Touch Actions

## God Nodes (most connected - your core abstractions)
1. `ViewHolderState` - 124 edges
2. `txt()` - 112 edges
3. `DataStoreHelper` - 107 edges
4. `ResultEpisode` - 104 edges
5. `AniListApi` - 95 edges
6. `CS3IPlayer` - 87 edges
7. `ResultViewModel2` - 84 edges
8. `AuthData` - 77 edges
9. `SyncAPI` - 76 edges
10. `GeneratorPlayer` - 76 edges

## Surprising Connections (you probably didn't know these)
- `VideoClickActionHolder` --calls--> `AlwaysAskAction`  [INFERRED]
  com/lagradost/cloudstream3/actions/VideoClickAction.kt → com/lagradost/cloudstream3/actions/AlwaysAskAction.kt
- `buildDefaultClient()` --calls--> `Cache`  [INFERRED]
  com/lagradost/cloudstream3/network/RequestsHelper.kt → com/lagradost/cloudstream3/ui/player/RepoLinkGenerator.kt
- `AccountManager` --calls--> `PlainAuthRepo`  [INFERRED]
  com/lagradost/cloudstream3/syncproviders/AccountManager.kt → com/lagradost/cloudstream3/syncproviders/AuthRepo.kt
- `AccountManager` --calls--> `SubtitleRepo`  [INFERRED]
  com/lagradost/cloudstream3/syncproviders/AccountManager.kt → com/lagradost/cloudstream3/syncproviders/SubtitleRepo.kt
- `AccountManager` --calls--> `SyncRepo`  [INFERRED]
  com/lagradost/cloudstream3/syncproviders/AccountManager.kt → com/lagradost/cloudstream3/syncproviders/SyncRepo.kt

## Import Cycles
- None detected.

## Communities (243 total, 129 thin omitted)

### Community 0 - "VideoGenerator & Utils"
Cohesion: 0.03
Nodes (25): RepoLinkGenerator, EpisodeIndexer, EpisodeRange, EpisodeSortType, DATE_NEWEST, DATE_OLDEST, NUMBER_ASC, NUMBER_DESC (+17 more)

### Community 1 - "Account Helpers"
Cohesion: 0.05
Nodes (15): AccountHelper, AccountViewModel, VideoWatchState, None, Watched, Account, BookmarkedData, DataStoreHelper (+7 more)

### Community 2 - "Activity Intents"
Cohesion: 0.05
Nodes (18): makeTempM3U8Intent(), OpenInAppAction, updateDurationAndPosition(), BiglyBTPackage, LibreTorrentPackage, MpvKtPackage, MpvKtPreviewPackage, MpvExPackage (+10 more)

### Community 3 - "MainActivity Bindings"
Cohesion: 0.04
Nodes (3): FocusTarget, MainActivity, TvFocus

### Community 6 - "AniList API"
Cohesion: 0.11
Nodes (66): AniListApi, AniListAvatar, AniListData, AniListFavoritesMediaConnection, AniListFavourites, AniListRoot, AniListTitleHolder, AniListUser (+58 more)

### Community 7 - "Activity Options"
Cohesion: 0.06
Nodes (11): AlwaysAskAction, Aria2Package, CopyClipboardAction, JustPlayerPackage, PlayInBrowserAction, PlayMirrorAction, VideoGenerator, VideoClickAction (+3 more)

### Community 8 - "RecyclerView Adapters"
Cohesion: 0.06
Nodes (6): BaseAdapter, clear(), ViewHolderState, HomeScrollAdapter, ImageAdapter, RepoAdapter

### Community 9 - "Download Click Handling"
Cohesion: 0.07
Nodes (9): DownloadActionType, Pause, Resume, Stop, DownloadMetaData, VideoDownloadManager, CreateNotificationMetadata, DownloadedFileInfo (+1 more)

### Community 10 - "Chromecast Setup"
Cohesion: 0.05
Nodes (9): ControllerActivity, getItemIndex(), loadMirror(), MetadataHolder, SelectSourceController, SkipNextEpisodeController, SkipTimeController, CastHelper (+1 more)

### Community 11 - "Offline Playback Helpers"
Cohesion: 0.05
Nodes (4): SettingGeneralPreview(), SettingsGeneralScreen, SettingsPlayerScreen, SettingsUpdatesScreen

### Community 12 - "MAL API"
Cohesion: 0.10
Nodes (37): AlternativeTitles, Broadcast, Data, Genres, ListStatus, MainPicture, MalAnime, MALApi (+29 more)

### Community 14 - "TV Settings Chips"
Cohesion: 0.06
Nodes (4): BasePreferenceFragmentCompat, SettingsProviders, SettingsUpdates, SetupFragmentProviderLanguage

### Community 15 - "Plugin Manager Activity"
Cohesion: 0.10
Nodes (3): OnlinePluginData, PluginData, PluginManager

### Community 16 - "Compose Theming"
Cohesion: 0.05
Nodes (3): SaveChromeCaptionStyle, ChromecastSubtitlesScreen, SubtitlesScreen

### Community 17 - "Simkl Auth"
Cohesion: 0.07
Nodes (7): AuthAPI, AuthLoginPage, AuthLoginResponse, AuthPinData, AuthToken, AuthUser, LoginInfo

### Community 20 - "CS3IPlayer Core"
Cohesion: 0.06
Nodes (7): CS3IPlayer, DefaultRenderersFactory, AudioTrack, CurrentTracks, TextTrack, Track, VideoTrack

### Community 21 - "Result Phone UI"
Cohesion: 0.06
Nodes (4): setRecycledViewPool(), ResultFragmentPhone, PanelStateListener, getChooseFolderLauncher()

### Community 22 - "Fetch Buttons"
Cohesion: 0.07
Nodes (5): PieFetchButton, DownloadAdapterItem, DownloadQueueAdapter, DragAndDropTouchHelper, DragAndDropTouchHelperCallback

### Community 23 - "Home Fragment"
Cohesion: 0.07
Nodes (5): HomeFragment, OnScrollListener, OnScrollListener, ArrayAdapter, EmptyEvent

### Community 24 - "Home Parent Adapter"
Cohesion: 0.07
Nodes (4): HeaderViewHolder, HomeParentItemAdapterPreview, showAccountEditBox(), HomeScrollTransformer

### Community 27 - "Trakt Sync"
Cohesion: 0.09
Nodes (10): AbstractSyncStatus, LibraryItem, LibraryList, LibraryMetadata, Page, SyncAPI, SyncResult, SyncSearchResult (+2 more)

### Community 28 - "Download Button Setup"
Cohesion: 0.06
Nodes (4): DownloadButtonSetup, DownloadAdapterQueue, DownloadQueueViewModel, DownloadFileGenerator

### Community 29 - "SyncViewModel"
Cohesion: 0.10
Nodes (9): CurrentSynced, SyncViewModel, Anilist, Mal, MalSyncPage, ProviderPage, SyncPage, SyncPages (+1 more)

### Community 30 - "Settings Compose"
Cohesion: 0.07
Nodes (4): Preview(), SettingsFragmentScreen, NestedScrollConnection, SettingsNavigation

### Community 31 - "Trakt Activity"
Cohesion: 0.18
Nodes (25): Account, ActivitiesResponse, CacheFreshness, Episode, EpisodeMetadata, EpisodesCacheEntry, HistoryMediaObject, HistoryRequest (+17 more)

### Community 33 - "TestViewModel"
Cohesion: 0.10
Nodes (14): Cancel, CancelSingle, ClearSingle, DerivedTestState, FilterTestResults, ImmutableTestResult, Init, Search (+6 more)

### Community 34 - "AccountManager"
Cohesion: 0.08
Nodes (11): AccountManager, AuthData, BackupAPI, AniListStatusType, Completed, Dropped, None, Paused (+3 more)

### Community 35 - "Library Transforms"
Cohesion: 0.07
Nodes (5): LibraryScrollTransformer, PageAdapter, SearchAdapter, SearchClickCallback, SearchResultBuilder

### Community 36 - "Trakt Search Results"
Cohesion: 0.09
Nodes (12): AllItemsResponse, Metadata, ShowMetadata, SimklApi, SimklListStatusType, Completed, Dropped, None (+4 more)

### Community 39 - "Kitsu Interceptors"
Cohesion: 0.07
Nodes (4): FallbackInterceptor, LogcatBinaryParser, LogcatItem, toHumanReadable()

### Community 40 - "Kitsu Episodes"
Cohesion: 0.18
Nodes (22): Data, Description, Episodes, Kitsu, KitsuAnimeAttributes, KitsuAnimeData, KitsuApi, KitsuLinks (+14 more)

### Community 41 - "Downloads List Store"
Cohesion: 0.08
Nodes (14): CheckDuplicateData, getOptions(), getTitle(), LibraryListType, BOOKMARKS, FAVORITES, SUBSCRIPTIONS, WatchType (+6 more)

### Community 42 - "Subtitle Resources"
Cohesion: 0.12
Nodes (9): SingleSubtitleResource, SubtitleResource, SubtitleEntity, SubtitleSearch, Addic7ed, SubtitleAPI, SavedResourceResponse, SavedSearchResponse (+1 more)

### Community 43 - "Download Cache Metadata"
Cohesion: 0.13
Nodes (13): DownloadCached, DownloadedFileInfoResult, DownloadEpisodeCached, DownloadEpisodeMetadata, DownloadHeaderCached, DownloadItem, DownloadObjects, DownloadQueueItem (+5 more)

### Community 44 - "Settings Providers"
Cohesion: 0.10
Nodes (4): CustomSite, nameNextToFlagEmoji(), SettingsGeneral, BatteryOptimizationChecker

### Community 45 - "BottomSheet Fragments"
Cohesion: 0.15
Nodes (7): BaseBottomSheetDialogFragment, BaseDialogFragment, BaseFragment, BaseFragmentHelper, Bind, BindingCreator, Inflate

### Community 47 - "Backup Utils"
Cohesion: 0.11
Nodes (3): BackupFile, BackupUtils, BackupVars

### Community 48 - "AbstractPlayerFragment"
Cohesion: 0.07
Nodes (5): AbstractPlayerFragment, PlayerResize, Fill, Fit, Zoom

### Community 49 - "Quality Profiles"
Cohesion: 0.11
Nodes (8): ProfilesAdapter, QualityDataHelper, QualityProfile, QualityProfileType, Data, Download, None, WiFi

### Community 50 - "DownloadFileGenerator"
Cohesion: 0.12
Nodes (4): ExtractorSubtitleLink, DownloadFileManagement, EpisodeDownloadInstance, SubtitleUtils

### Community 52 - "PlayerSubtitleHelper"
Cohesion: 0.10
Nodes (10): PlayerSubtitleHelper, SubtitleData, SubtitleOrigin, DOWNLOADED_FILE, EMBEDDED_IN_VIDEO, URL, SubtitleStatus, IS_ACTIVE (+2 more)

### Community 53 - "Searchable Settings"
Cohesion: 0.10
Nodes (12): SettingsAccount2, SettingsFragment2, SettingsGeneral2, SettingsPlayer2, SettingsProviders2, SettingsUI2, SettingsUpdates2, TestFragment2 (+4 more)

### Community 54 - "BaseFetchButton"
Cohesion: 0.10
Nodes (3): BaseFetchButton, DownloadMetadata, DownloadButton

### Community 55 - "Preview Generator Math"
Cohesion: 0.11
Nodes (5): image(), ImageParams, M3u8PreviewGenerator, Mp4PreviewGenerator, rescale()

### Community 56 - "Theme Account Select"
Cohesion: 0.08
Nodes (9): AccountAction, AccountState, AccountViewModel2, DeleteAccount, EditAccount, LoginWithAccount, NewAccount, SaveAccount (+1 more)

### Community 58 - "FCast Discovery"
Cohesion: 0.10
Nodes (6): DefaultDiscoveryListener, ResolveListener, ServiceInfoCallback, DefaultRegistrationListener, FcastManager, PublicDeviceInfo

### Community 59 - "AniSkip Auth"
Cohesion: 0.17
Nodes (17): PlainAuthRepo, Account, AnimeSkip, AnimeSkipAuth, ApiData, ApiRoot, Data, Episode (+9 more)

### Community 60 - "Download Deletion"
Cohesion: 0.15
Nodes (3): DeleteData, DownloadStats, DownloadViewModel

### Community 61 - "Resume Watching Store"
Cohesion: 0.11
Nodes (3): DataStore, Editor, PreferenceDelegate

### Community 62 - "Playback State Handling"
Cohesion: 0.16
Nodes (21): Listener, Listener, DownloadEvent, EmbeddedSubtitlesFetchedEvent, EpisodeSeekEvent, PauseEvent, PlayerAttachedEvent, PlayerEvent (+13 more)

### Community 63 - "PlayerGeneratorViewModel"
Cohesion: 0.10
Nodes (3): GeneratorState, PlayerGeneratorViewModel, VideoLive

### Community 65 - "Media Notification"
Cohesion: 0.10
Nodes (5): CustomActionReceiver, MediaDescriptionAdapter, Player, LiveHelper, Listener

### Community 67 - "QuickSearch Fragment"
Cohesion: 0.09
Nodes (3): QuickSearchFragment, OnQueryTextListener, OnScrollListener

### Community 72 - "Download Adapter"
Cohesion: 0.12
Nodes (7): Child, DiffCallback, DownloadAdapter, DownloadClickEvent, DownloadHeaderClickEvent, Header, VisualDownloadCached

### Community 73 - "Repository Manager"
Cohesion: 0.13
Nodes (4): RepositoryManager, ExtensionsViewModel, PluginStats, RepositoryData

### Community 74 - "Extensions Fragment"
Cohesion: 0.09
Nodes (5): ExtensionsFragment, OnActionExpandListener, OnQueryTextListener, PluginsFragment, OnQueryTextListener

### Community 75 - "Offline Video Source"
Cohesion: 0.16
Nodes (4): Listener, DrmMetadata, MediaItemSlice, ErrorEvent

### Community 76 - "Preference Fragments"
Cohesion: 0.11
Nodes (4): SettingsFragment, SettingsPlayer, SettingsUI, TestFragment

### Community 80 - "Filler Episode Check"
Cohesion: 0.13
Nodes (6): CombinedMedia, Database, FillerEpisodeCheck, MappingRoot, Season, Show

### Community 82 - "Easter Egg"
Cohesion: 0.13
Nodes (3): EasterEggMonkeFragment, AnimatorListenerAdapter, AnimatorListenerAdapter

### Community 87 - "Torrent Serialization"
Cohesion: 0.08
Nodes (23): Opcode, None, Pause, Ping, Play, PlaybackError, PlaybackUpdate, Pong (+15 more)

### Community 89 - "Biometric Utils"
Cohesion: 0.11
Nodes (3): BiometricAuthenticator, AuthenticationCallback, BiometricCallback

### Community 92 - "Torrent Models"
Cohesion: 0.17
Nodes (4): Torrent, TorrentFileStat, TorrentRequest, TorrentStatus

### Community 93 - "App Language"
Cohesion: 0.13
Nodes (4): getCurrentLocale(), SetupFragmentLanguage, SetupFragmentLayout, SetupFragmentMedia

### Community 95 - "Repository Data"
Cohesion: 0.14
Nodes (4): PluginWrapper, Repository, SitePlugin, CountifyResult

### Community 96 - "DoH DNS Providers"
Cohesion: 0.14
Nodes (10): addAdGuardDns(), addCanadianShieldDns(), addCloudFlareDns(), addDnsSbDns(), addDNSWatchDns(), addGenericDns(), addGoogleDns(), addQuad9Dns() (+2 more)

### Community 97 - "CloudStreamPackage"
Cohesion: 0.14
Nodes (3): CloudStreamPackage, MinimalSubtitleLink, MinimalVideoLink

### Community 98 - "AniList Library"
Cohesion: 0.09
Nodes (15): KitsuStatusType, Completed, Dropped, None, OnHold, PlanToWatch, Watching, SyncWatchType (+7 more)

### Community 99 - "Download Fragment"
Cohesion: 0.15
Nodes (3): DownloadFragment, BasicLink, LinkGenerator

### Community 101 - "Lifecycle Observers"
Cohesion: 0.15
Nodes (4): observe(), observeNullable(), DownloadChildFragment, DownloadQueueFragment

### Community 103 - "Subtitle Selection"
Cohesion: 0.16
Nodes (5): OnQueryTextListener, LogcatDialog(), LogcatItem(), CountDownTimer, txt()

### Community 105 - "Account Screen"
Cohesion: 0.23
Nodes (9): AccountScreen, Preview(), WaitForDialogType, DeleteAccount, Login, NewPassword, NewProfilePicture, NewProfileUrl (+1 more)

### Community 106 - "Home Child Adapter"
Cohesion: 0.14
Nodes (3): HomeChildItemAdapter, HomeScrollViewHolderState, ResumeItemAdapter

### Community 107 - "Result Parent Adapter"
Cohesion: 0.14
Nodes (4): LoadClickCallback, ParentItemAdapter, OnScrollListener, ParentItemHolder

### Community 108 - "LibraryViewModel"
Cohesion: 0.12
Nodes (11): LibraryViewModel, ListSorting, AlphabeticalA, AlphabeticalZ, Query, RatingHigh, RatingLow, ReleaseDateNew (+3 more)

### Community 111 - "Library Fragment"
Cohesion: 0.13
Nodes (9): LibraryOpener, LibraryOpenerType, Browser, Default, None, Provider, Search, MenuSearchView (+1 more)

### Community 112 - "Progress Views"
Cohesion: 0.14
Nodes (5): TestState, None, Running, Stopped, TestView

### Community 113 - "Annotated Text Compose"
Cohesion: 0.13
Nodes (3): MainActivityScreen, parseMarkdown(), textMarkDown()

### Community 117 - "Priority Adapters"
Cohesion: 0.13
Nodes (6): NoStateAdapter, PriorityAdapter, SourcePriority, AccountAdapter, AccountClickCallback, LogcatAdapter

### Community 118 - "Quality Profile Dialog"
Cohesion: 0.14
Nodes (3): LinkSource, QualityProfileDialog, SourcePriorityDialog

### Community 120 - "OpenSubtitles"
Cohesion: 0.23
Nodes (9): AuthLoginRequirement, OAuthToken, OpenSubtitlesApi, ResultAttributes, ResultData, ResultDownloadLink, ResultFeatureDetails, ResultFiles (+1 more)

### Community 122 - "Plugin Adapter"
Cohesion: 0.12
Nodes (3): PluginAdapter, RepositoryViewHolderState, PluginDetailsFragment

### Community 124 - "APIHolder Test"
Cohesion: 0.17
Nodes (6): ProviderFilter, All, Failed, Passed, TestProgress, TestViewModel

### Community 126 - "Skip API Models"
Cohesion: 0.18
Nodes (4): Builder, SimklEpisodeConstructor, SimklScoreBuilder, SimklSyncStatus

### Community 127 - "ResultFragment"
Cohesion: 0.13
Nodes (6): buildResultEpisode(), getDisplayPosition(), getWatchProgress(), ResultFragment, StoredData, AutoResume

### Community 129 - "SkipAPI"
Cohesion: 0.14
Nodes (11): SkipAPI, SkipStamp, SkipType, Credits, Ending, Intro, MixedEnding, MixedOpening (+3 more)

### Community 132 - "Subtitle Enumerations"
Cohesion: 0.27
Nodes (10): AbstractSubtitleEntities, ApiKeyResponse, ApiResponse, OAuthTokenResponse, Result, SubDlApi, Subtitle, SubtitleOAuthEntity (+2 more)

### Community 134 - "WebView Fragment"
Cohesion: 0.17
Nodes (3): RepoApi, WebviewFragment, WebViewClient

### Community 137 - "ViewPagerAdapter"
Cohesion: 0.18
Nodes (4): getSafeParcelable(), ViewpagerAdapter, OnFlingListener, ViewpagerAdapterViewHolderState

### Community 139 - "IPreviewGenerator"
Cohesion: 0.16
Nodes (3): IPreviewGenerator, NoPreviewGenerator, PreviewGenerator

### Community 141 - "SearchResponse Helpers"
Cohesion: 0.36
Nodes (6): Log, TestingUtils, TestResult, TestResultList, TestResultLoad, TestResultProvider

### Community 142 - "Package Installer"
Cohesion: 0.19
Nodes (5): PackageInstallerStatusReceiver, VideoDownloadRestartReceiver, BroadcastReceiver, getSafeParcelableExtra(), registerBroadcastReceiver()

### Community 147 - "Subtitle Fonts"
Cohesion: 0.12
Nodes (16): SubtitleFont, Comic, Consola, Custom, Futura, Google, Gotham, Lucida (+8 more)

### Community 148 - "Custom RecyclerViews"
Cohesion: 0.24
Nodes (3): AutofitRecyclerView, GrdLayoutManager, MaxRecyclerView

### Community 149 - "Bitmap Drawables"
Cohesion: 0.16
Nodes (5): Drawable, drawableToBitmap(), getImageFromDrawable(), Image, UiImage

### Community 152 - "TV Back Press"
Cohesion: 0.17
Nodes (3): BackPressedCallbackHelper, OnBackPressedCallback, CallbackHelper

### Community 156 - "Search History"
Cohesion: 0.23
Nodes (3): SearchHistoryAdaptor, SearchHistoryCallback, SearchHistoryItem

### Community 157 - "GitHub Releases"
Cohesion: 0.30
Nodes (6): GithubAsset, GithubObject, GithubRelease, GithubTag, InAppUpdater, Update

### Community 162 - "Download JSON"
Cohesion: 0.32
Nodes (8): DownloadRoot, ItemRoot, Results, SearchRoot, SubSourceApi, Subtitle, Subtitles, Users

### Community 164 - "LinkGenerator"
Cohesion: 0.17
Nodes (4): ExtractorUri, MinimalLinkGenerator, callback(), transformResult()

### Community 169 - "Video Link State"
Cohesion: 0.34
Nodes (3): DisplayLink, VideoState, VideoSkipStamp

### Community 170 - "GitHub API"
Cohesion: 0.33
Nodes (5): GithubAsset, GithubObject, GithubRelease, GithubReleases, GithubTag

### Community 176 - "CSPlayer Events"
Cohesion: 0.15
Nodes (12): CSPlayerEvent, NextEpisode, Pause, Play, PlayAsAudio, PlayPauseToggle, PrevEpisode, Restart (+4 more)

### Community 181 - "CSPlayer Loading"
Cohesion: 0.17
Nodes (5): CSPlayerLoading, IsBuffering, IsEnded, IsPaused, IsPlaying

### Community 186 - "AniSkip Intervals"
Cohesion: 0.38
Nodes (4): AniSkip, AniSkipInterval, AniSkipResponse, Stamp

### Community 189 - "Search Suggestion API"
Cohesion: 0.35
Nodes (3): SearchSuggestionApi, TmdbSearchItem, TmdbSearchResult

### Community 190 - "IntroDB Skip"
Cohesion: 0.31
Nodes (3): Root, Stamp, TheIntroDBSkip

### Community 201 - "Simkl Cache"
Cohesion: 0.36
Nodes (4): CacheTimes, OneMonth, ThirtyMinutes, SimklCache

### Community 206 - "IntroDB Skip Models"
Cohesion: 0.44
Nodes (3): IntroDbResponse, IntroDbSkip, Segment

### Community 226 - "Download Type"
Cohesion: 0.29
Nodes (7): DownloadType, IsDone, IsDownloading, IsFailed, IsPaused, IsPending, IsStopped

### Community 230 - "Profile Settings"
Cohesion: 0.40
Nodes (3): HideErrorSources, HideNegativeSources, ProfileSettings

### Community 234 - "Focus Direction"
Cohesion: 0.40
Nodes (5): FocusDirection, Down, End, Start, Up

### Community 238 - "Install Progress"
Cohesion: 0.40
Nodes (5): InstallProgressStatus, Downloading, Failed, Installing, Preparing

### Community 240 - "Touch Actions"
Cohesion: 0.50
Nodes (4): TouchAction, Brightness, Time, Volume

## Knowledge Gaps
- **170 isolated node(s):** `Start`, `End`, `Up`, `Down`, `None` (+165 more)
  These have ≤1 connection - possible missing edges. (Counts symbols only; 1197 node(s) total have ≤1 connection when file, concept and rationale nodes are included.)
- **129 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `txt()` connect `Subtitle Selection` to `ResultFragmentTV`, `Account Helpers`, `Activity Intents`, `MainActivity Bindings`, `VideoGenerator & Utils`, `ViewBinding Dialogs`, `SkipAPI`, `Activity Options`, `Subscription WorkManager`, `Download Click Handling`, `Search Config`, `MAL API`, `Test Compose Screen`, `TV Settings Chips`, `Plugin Manager Activity`, `Prerelease UI`, `Result Phone UI`, `FCast Actions`, `GeneratorPlayer Core`, `Trakt Sync`, `Settings Compose`, `DownloadUtils`, `AccountManager`, `Subtitle Offset`, `Trakt Search Results`, `Kitsu Interceptors`, `Downloads List Store`, `Video Link State`, `BottomSheet Fragments`, `Account Select UI`, `Quality Profiles`, `DownloadFileGenerator`, `GeneratorPlayer Events`, `Repository Manager`, `Preference Fragments`, `DynamicString`, `LocalList`, `PluginsViewModel`, `CloudStreamPackage`, `Lifecycle Observers`, `Auth Repos`, `TextUtil`, `FullScreenPlayer Motion`, `Library Fragment`, `ReloadViewModel`, `Quality Profile Dialog`, `Share Utils`, `Plugin Adapter`?**
  _High betweenness centrality (0.076) - this node is a cross-community bridge._
- **Are the 3 inferred relationships involving `txt()` (e.g. with `.backup()` and `.addPrograms()`) actually correct?**
  _`txt()` has 3 INFERRED edges - model-reasoned connections that need verification._
- **What connects `Start`, `End`, `Up` to the rest of the system?**
  _170 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `VideoGenerator & Utils` be split into smaller, more focused modules?**
  _Cohesion score 0.03137254901960784 - nodes in this community are weakly interconnected._
- **Why does `GeneratorPlayer` connect `GeneratorPlayer Core` to `ResultFragmentTV`, `Media Notification`, `VideoGenerator & Utils`, `Download Fragment`, `MainActivity Bindings`, `LinkGenerator`, `GeneratorPlayer Events`, `Activity Options`, `Subtitle Selection`, `Forced Encoding`, `Episode Overlay`, `SyncViewModel`, `FullScreenPlayer Layout Animation`, `VideoGenerator Activity`, `PlayerSubtitleHelper`, `Download Button Setup`, `PlayerGeneratorViewModel`, `ResultFragment`?**
  _High betweenness centrality (0.043) - this node is a cross-community bridge._
- **Should `Account Helpers` be split into smaller, more focused modules?**
  _Cohesion score 0.05039193729003359 - nodes in this community are weakly interconnected._
- **Why does `ResultFragmentPhone` connect `Result Phone UI` to `VideoGenerator & Utils`, `IPlayer Interface`, `Result Swipe Binding`, `Player Drawables`, `Subtitle Selection`, `Settings Providers`, `BottomSheet Fragments`, `Embedded Subtitles Fetch`, `CS3IPlayer Core`, `ReloadViewModel`, `SyncViewModel`?**
  _High betweenness centrality (0.038) - this node is a cross-community bridge._