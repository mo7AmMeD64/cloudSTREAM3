# Graph Report - KodaStream  (2026-10-17)

## Corpus Check
- 230 files · ~123,381 words
- Verdict: corpus is large enough that graph structure adds value.
- Unclassified: 21 file(s) not represented in the graph (top: .xml 12, (none) 3, .properties 2)

## Summary
- 3599 nodes · 9594 edges · 141 communities (95 shown, 46 thin omitted)
- Extraction: 98% EXTRACTED · 2% INFERRED · 0% AMBIGUOUS · INFERRED: 206 edges (avg confidence: 0.85)
- Token cost: 1,200 input · 700 output

## Community Hubs (Navigation)
- JS Interpreter Tests
- Android Activity Integration
- Extractor Serialization Models
- Compose Screens & Dialogs
- HLS Playlist Parsing
- NineAnime Crypto
- CloudStream App Runtime
- App Navigation & Motion
- Kotlin Test Assertions
- Serializer Transform Tests
- Coroutines Utilities
- EmbedGram Extractor
- Download Foreground Service
- AES Crypto Extractors
- Shared UI Components
- StringUtils & Base64
- Library Favorites Store
- Color Palette System
- CloudMailRu & GamoVideo
- JsUnpacker Tokens
- Levenshtein Distance
- TMDb Provider
- StreamWish & Ksoup
- Search Field & Events
- Fastream & Filegram
- Trakt Cast Models
- JS Interpreter Core
- StreamSB Extractors
- Download Locations (SAF)
- Local Data Stores
- Extensions Browser
- Player Transport Controls
- FlyFile & Player Switch
- Engine Gateway
- Rhino JS Engine
- FileMoon Extractors
- AsianEmbed Helpers
- Episode Queue
- Download Preferences
- KMP Platform Context
- CineMM Extractors
- CommonActivity Runtime
- Extension Store Persistence
- JS Parser Nodes
- Details Screen
- History Store
- Download Migration
- Coroutine Architecture Extensions
- LoadResponse Serialization
- JS Parser Matcher
- JS Interpreter Runtime
- Episode Date Tests
- Trakt API Models
- Dood Extractors
- ByseSX AES Helpers
- MVVM Safe Calls
- Extensions Screen
- Custom JSON Serializers
- Vidara & Misc Extractors
- GDrivePlayer Extractors
- XStreamCDN & Fembed
- Kotlinx JSON Model
- JsHunter & Rhino
- GDMirrorBot Helpers
- Generated Serializer Tests
- Voe Extractors
- TvType Catalog
- Plugin API
- Blogger Extractor
- Pull-to-Refresh Component
- Skeleton Loading Components
- Cda Extractor
- DataStore Persistence
- TMDb Cross-Provider
- Subtitle Style Store
- Up4Stream Extractor
- Sobreatsesuyp Extractor
- Odnoklassniki Extractor
- APIHolder Registry
- StreamPlay Captcha Extractor
- StreamTape Extractors
- SyncRedirector Meta-Provider
- AppUtils JSON
- Gofile Extractor
- Dailymotion Extractors
- HDStreamable Extractors
- JWPlayer Sites
- Nullable Serializers
- Web Platform Targets
- WcoHelper Extractor
- MixDrop Extractors
- Filesim Extractors
- FireStream Extractors
- OkHttp Interceptors
- StringUtils URL Codec
- ContentX Extractors
- Linkbox Extractor
- YouTube Extractor
- Coroutines Dispatchers
- JS Lexer
- Download Options Card
- Tantifilm Extractor
- Vicloud Extractor
- VideoSeyred Extractor
- Vidoza Extractor
- DRM Extractor Links
- CI Workflow
- AceFile Extractor
- HxFile Extractor
- MailRu Extractor
- TauVideo Extractor
- Uqload Extractor
- Logging (android)
- Logging (jvm)
- HDPlayerSystem Extractor
- YouTube Variants
- AutoDownloadMode
- Search Response Models
- Logging (common)
- Logging (web)
- Download Actions
- OkHttp SSL Workarounds
- ShowStatus Metadata
- PixelDrain Extractor
- VPN Status Helpers
- M3U8 Manifest

## God Nodes (most connected - your core abstractions)
1. `JsInterpreterTest` - 404 edges
2. `ExtractorApi` - 181 edges
3. `newExtractorLink()` - 123 edges
4. `ExtractorLink` - 122 edges
5. `SubtitleFile` - 86 edges
6. `Node` - 63 edges
7. `logError()` - 58 edges
8. `SerializersTest` - 57 edges
9. `TT` - 53 edges
10. `StringUtilsTest` - 53 edges

## Surprising Connections (you probably didn't know these)
- `episodesOf()` --references--> `DubStatus`  [EXTRACTED]
  app/src/main/java/com/kodastream/app/ui/details/DetailsScreen.kt → library/src/commonMain/kotlin/com/lagradost/cloudstream3/MainAPI.kt
- `Ready` --references--> `ExtractorLink`  [EXTRACTED]
  app/src/main/java/com/kodastream/app/ui/details/DownloadOptionsSheet.kt → library/src/commonMain/kotlin/com/lagradost/cloudstream3/utils/ExtractorApi.kt
- `HomeViewModel` --references--> `MainAPI`  [EXTRACTED]
  app/src/main/java/com/kodastream/app/ui/home/HomeScreen.kt → library/src/commonMain/kotlin/com/lagradost/cloudstream3/MainAPI.kt
- `HomeShelves()` --references--> `MainAPI`  [EXTRACTED]
  app/src/main/java/com/kodastream/app/ui/home/HomeScreen.kt → library/src/commonMain/kotlin/com/lagradost/cloudstream3/MainAPI.kt
- `PlayerViewModel` --references--> `MainAPI`  [EXTRACTED]
  app/src/main/java/com/kodastream/app/ui/player/PlayerScreen.kt → library/src/commonMain/kotlin/com/lagradost/cloudstream3/MainAPI.kt

## Import Cycles
- None detected.

## Hyperedges (group relationships)
- **CI Release Pipeline** — _github_workflows_build_release_apk_build, _github_workflows_build_apk_artifact_upload, _github_workflows_build_github_release_publishing [EXTRACTED 1.00]

## Communities (141 total, 46 thin omitted)

### Community 2 - "Android Activity Integration"
Cohesion: 0.04
Nodes (10): Failed, Loading, SheetState, applySubtitleStyle(), buildMediaSource(), subtitleMimeType(), GroupTitle(), Swatch() (+2 more)

### Community 3 - "Extractor Serialization Models"
Cohesion: 0.05
Nodes (19): HDMomPlayer, Track, AesData, AesHelper, CryptoJS, GogoHelper, GogoJsonData, GogoSource (+11 more)

### Community 5 - "HLS Playlist Parsing"
Cohesion: 0.07
Nodes (16): C, DrmInitData, Format, HlsMultivariantPlaylist, HlsPlaylistParser, MimeTypes, Mp4aObjectType, Mp4Box (+8 more)

### Community 7 - "CloudStream App Runtime"
Cohesion: 0.05
Nodes (7): CloudStreamApp, CloudflareKiller, DdosGuardKiller, buildDefaultClient(), getHeaders(), initClient(), AppDebug

### Community 8 - "App Navigation & Motion"
Cohesion: 0.05
Nodes (15): AppNavGraph(), AppRoot(), FloatingNavPill(), FloatingSideBar(), navContainerColor(), navContentColor(), navigateTopLevel(), navItemPadding() (+7 more)

### Community 9 - "Kotlin Test Assertions"
Cohesion: 0.06
Nodes (8): splitUrlParameters(), LazyHlsDownloadData, M3u8Helper2, M3u8Stream, TsLink, ArchComponentExtTest, SplitUrlParametersTest, M3u8HelperTest

### Community 11 - "Coroutines Utilities"
Cohesion: 0.04
Nodes (20): AssignExpr, BreakSignal, BreakStmt, ContinueSignal, ContinueStmt, ForInStmt, ForStmt, IfStmt (+12 more)

### Community 12 - "EmbedGram Extractor"
Cohesion: 0.08
Nodes (12): Embedgram, GUpload, VideoInfo, Krakenfiles, VkExtractor, ResponseSource, YourUpload, ErrorLoadingException (+4 more)

### Community 13 - "Download Foreground Service"
Cohesion: 0.07
Nodes (7): DownloadService, NotEnoughSpaceException, Segment, DownloadRow(), DownloadsList(), stateLabel(), TitleCard()

### Community 14 - "AES Crypto Extractors"
Cohesion: 0.08
Nodes (18): EmturbovidExtractor, Evoload, Evoload1, GenericM3U8, Maxstream, Moviehab, MoviehabNet, PlayerVoxzer (+10 more)

### Community 15 - "Shared UI Components"
Cohesion: 0.09
Nodes (15): EmptyBox(), ErrorBox(), LoadingBox(), PosterCard(), PosterPlaceholder(), RemoteImage(), SectionHeader(), DetailsScreen() (+7 more)

### Community 16 - "StringUtils & Base64"
Cohesion: 0.05
Nodes (10): AudioFile, capitalizeString(), capitalizeStringNullable(), fixUrl(), fixUrlNull(), imdbUrlToId(), imdbUrlToIdNullable(), ProvidersInfoJson (+2 more)

### Community 17 - "Library Favorites Store"
Cohesion: 0.09
Nodes (19): LibraryItem, LibraryStore, WatchedEntry, WatchedStore, BackButton(), DetailsContent(), DetailsState, directDataOf() (+11 more)

### Community 18 - "Color Palette System"
Cohesion: 0.07
Nodes (24): AppPalette, buildPaletteColorScheme(), contrastInk(), findPalette(), monochromeRoles(), PaletteRoles, buildSeedColorScheme(), PaletteStyle (+16 more)

### Community 19 - "CloudMailRu & GamoVideo"
Cohesion: 0.06
Nodes (22): CloudMailRu, GamoVideo, GoodstreamExtractor, HubuCloud, Mediafire, Mvidoo, SBPlay, Userscloud (+14 more)

### Community 20 - "JsUnpacker Tokens"
Cohesion: 0.04
Nodes (49): TT, AMP, AND, CARET, COLON, COMMA, DOT, EOF (+41 more)

### Community 21 - "Levenshtein Distance"
Cohesion: 0.07
Nodes (19): EditOp, editOpsFromCostMatrix(), EditType, DELETE, EQUAL, INSERT, KEEP, REPLACE (+11 more)

### Community 22 - "TMDb Provider"
Cohesion: 0.08
Nodes (9): HomePageResponse, mainPageOf(), MainPageRequest, newHomePageResponse(), ProviderType, DirectProvider, MetaProvider, MyDramaListInterceptor (+1 more)

### Community 23 - "StreamWish & Ksoup"
Cohesion: 0.10
Nodes (34): Asnwish, Awish, CdnwishCom, Doodporn, Dwish, Ewish, FlaswishCom, Hgcloudto (+26 more)

### Community 24 - "Search Field & Events"
Cohesion: 0.06
Nodes (8): AppEvents, SearchEmptyState(), SearchField(), LibraryScreen(), LibrarySection, DOWNLOADS, FAVORITES, WATCHLIST

### Community 25 - "Fastream & Filegram"
Cohesion: 0.09
Nodes (15): Fastream, Filegram, JwPlayerHelper, Source, Track, Jeniusplay, ResponseSource, LuluStream (+7 more)

### Community 26 - "Trakt Cast Models"
Cohesion: 0.13
Nodes (18): Cast, Credits, Crew, Data, Genre, Images, LinkData, Media (+10 more)

### Community 27 - "JS Interpreter Core"
Cohesion: 0.15
Nodes (14): digitValue(), joinElement(), JsFunction, JsInterpreter, JsList, JsObject, looseEq(), NativeFn (+6 more)

### Community 28 - "StreamSB Extractors"
Cohesion: 0.10
Nodes (32): Keephealth, Lvturbo, Main, Sbasian, Sbface, Sbflix, SBfull, Sblona (+24 more)

### Community 29 - "Download Locations (SAF)"
Cohesion: 0.18
Nodes (4): Download, DownloadLocations, DownloadStore, DownloadsSection()

### Community 31 - "Extensions Browser"
Cohesion: 0.10
Nodes (11): BrowseState, ExtensionsViewModel, Failed, Idle, Loading, Ready, Repository, RepositoryData (+3 more)

### Community 32 - "Player Transport Controls"
Cohesion: 0.06
Nodes (3): ForwardingPlayer, AtomicList, AtomicMutableList

### Community 33 - "FlyFile & Player Switch"
Cohesion: 0.08
Nodes (18): Ready, Flyfile, StreamInfo, Playmate, StreamInfo, CsstOnline, DsstOnline, FsstOnline (+10 more)

### Community 34 - "Engine Gateway"
Cohesion: 0.10
Nodes (9): Engine, readableMessage(), JsonResponse, Result, Slmaxed, Streamlare, MainAPI, mainPage() (+1 more)

### Community 36 - "FileMoon Extractors"
Cohesion: 0.08
Nodes (11): FileMoon, FileMoonIn, FileMoonSx, FilemoonV2, Sendvid, Streamhub, Supervideo, Vtbe (+3 more)

### Community 37 - "AsianEmbed Helpers"
Cohesion: 0.09
Nodes (11): AsianEmbedHelper, VstreamhubHelper, Hexload, StreamResponse, StreamResult, HubCloud, RapidVid, SibNet (+3 more)

### Community 38 - "Episode Queue"
Cohesion: 0.09
Nodes (8): EpisodeQueue, EpisodeQueues, QueueEpisode, PlayerViewModel, DubStatus, Dubbed, None, Subbed

### Community 39 - "Download Preferences"
Cohesion: 0.19
Nodes (4): AppPrefs, PlayerScreen(), Listener, SettingsScreen()

### Community 40 - "KMP Platform Context"
Cohesion: 0.09
Nodes (6): getContext(), toRequest(), toWebResourceResponse(), WebViewResolver, WebViewClient, safe()

### Community 41 - "CineMM Extractors"
Cohesion: 0.11
Nodes (26): Auvexiug, Cavanhabg, CineMMRedirect, Dhcplay, Dumbalag, Guxhag, Habetar, Haxloppd (+18 more)

### Community 42 - "CommonActivity Runtime"
Cohesion: 0.09
Nodes (6): CommonActivity, runOnMainThreadNative(), unshortenLinkSafe(), ShortLink, ShortUrl, runOnMainThreadNative()

### Community 43 - "Extension Store Persistence"
Cohesion: 0.20
Nodes (3): ExtensionStore, PluginManager, InstalledPlugin

### Community 44 - "JS Parser Nodes"
Cohesion: 0.17
Nodes (12): ArrayLit, BinExpr, BoolLit, CondExpr, ExprStmt, FuncExpr, Ident, Node (+4 more)

### Community 46 - "History Store"
Cohesion: 0.16
Nodes (8): HistoryEntry, HistoryStore, DetailsArgs, NavArgs, PlayerArgs, HistoryCard(), HistoryGrid(), HistoryScreen()

### Community 47 - "Download Migration"
Cohesion: 0.10
Nodes (7): DownloadMigration, AppBootstrap, MainActivity, ThemeMode, DARK, LIGHT, SYSTEM

### Community 48 - "Coroutine Architecture Extensions"
Cohesion: 0.12
Nodes (16): debugAssert(), DebugException, debugWarning(), Failure, getAllMessages(), getStackTracePretty(), launchSafe(), Loading (+8 more)

### Community 49 - "LoadResponse Serialization"
Cohesion: 0.29
Nodes (21): TmdbCastMember, TmdbContentRating, TmdbContentRatings, TmdbCredits, TmdbEpisode, TmdbGenre, TmdbIds, TmdbLink (+13 more)

### Community 50 - "JS Parser Matcher"
Cohesion: 0.24
Nodes (7): BlockStmt, CallExpr, MemberExpr, NewExpr, StrLit, UnaryExpr, VarDecl

### Community 53 - "Trakt API Models"
Cohesion: 0.23
Nodes (13): Airs, Cast, Data, Ids, Images, LinkData, MediaDetails, MediaSummary (+5 more)

### Community 54 - "Dood Extractors"
Cohesion: 0.15
Nodes (22): D0000d, D000dCom, DoodCxExtractor, DoodLaExtractor, DoodLiExtractor, DoodPmExtractor, DoodShExtractor, DoodSoExtractor (+14 more)

### Community 55 - "ByseSX AES Helpers"
Cohesion: 0.22
Nodes (11): ByseBuho, ByseQekaho, ByseSX, ByseVepoin, Bysezejataos, DecryptKeys, DetailsRoot, Playback (+3 more)

### Community 56 - "MVVM Safe Calls"
Cohesion: 0.11
Nodes (8): logError(), normalSafeApiCall(), suspendSafeApiCall(), amapIndexed(), argamap(), cmap(), JsHunter, Unbase

### Community 57 - "Extensions Screen"
Cohesion: 0.13
Nodes (5): BrowseTab(), ExtensionsScreen(), InstalledTab(), PluginCard(), PluginIcon()

### Community 59 - "Vidara & Misc Extractors"
Cohesion: 0.20
Nodes (19): Antarcticadocs, Handfacesnap, Namefacesnap, Nameitweb, Odysseusa, StreamUpFileInfo, StreamUpSubtitle, Thebesthostertv (+11 more)

### Community 60 - "GDrivePlayer Extractors"
Cohesion: 0.16
Nodes (13): DatabaseGdrive, DatabaseGdrive2, Gdriveplayer, Gdriveplayerapi, Gdriveplayerapp, Gdriveplayerbiz, Gdriveplayerco, Gdriveplayerfun (+5 more)

### Community 61 - "XStreamCDN & Fembed"
Cohesion: 0.21
Nodes (17): Captions, Cdnplayer, DBfilm, FeHD, FEmbed, Fembed9hd, FEnet, Fplayer (+9 more)

### Community 62 - "Kotlinx JSON Model"
Cohesion: 0.15
Nodes (3): JsonTransformSerializer, NonEmptySerializer, WriteOnlySerializer

### Community 64 - "GDMirrorBot Helpers"
Cohesion: 0.20
Nodes (8): EmbedData, EmbedHelper, FileSlug, GDMirrorbot, Techinmind, Vidsonic, Vixeo, base64Decode()

### Community 65 - "Generated Serializer Tests"
Cohesion: 0.44
Nodes (14): AllTransformsData, EmptyArrayData, EmptyStringData, FloatIntData, FloatLongData, MultiWriteOnly, NonEmptyData, NullableFieldsData (+6 more)

### Community 66 - "Voe Extractors"
Cohesion: 0.19
Nodes (10): MetaGnathTuggers, NathanFromSubject, Simpulumlamerop, Tubeless, Urochsunloath, Voe, Voe1, Voe2 (+2 more)

### Community 67 - "TvType Catalog"
Cohesion: 0.10
Nodes (19): TvType, Anime, AnimeMovie, AsianDrama, Audio, AudioBook, Cartoon, CustomMedia (+11 more)

### Community 68 - "Plugin API"
Cohesion: 0.15
Nodes (3): Plugin, BasePlugin, Manifest

### Community 69 - "Blogger Extractor"
Cohesion: 0.16
Nodes (6): Blogger, ResponseSource, PlayLtXyz, ResponseData, Sources, Uservideo

### Community 71 - "Skeleton Loading Components"
Cohesion: 0.29
Nodes (7): PosterCardSkeleton(), PosterShelfSkeleton(), rememberSkeletonAlpha(), SkeletonBox(), SkeletonList(), SkeletonTextLine(), ThumbRowSkeleton()

### Community 72 - "Cda Extractor"
Cohesion: 0.21
Nodes (4): Cda, PlayerData, VideoPlayerData, InternetArchive

### Community 75 - "Subtitle Style Store"
Cohesion: 0.33
Nodes (6): SubtitleStyle, SubtitleStyleStore, ChoiceRow(), SourcesSheet(), SubtitleStyleSheet(), SwatchRow()

### Community 76 - "Up4Stream Extractor"
Cohesion: 0.17
Nodes (6): Up4FunTop, Up4Stream, AesHelper, Server1uns, VidStack, fixUrl()

### Community 77 - "Sobreatsesuyp Extractor"
Cohesion: 0.21
Nodes (4): Sobreatsesuyp, SobreatsesuypVideoData, TRsTX, TrstxVideoData

### Community 78 - "Odnoklassniki Extractor"
Cohesion: 0.19
Nodes (7): Odnoklassniki, OkRuVideo, StreamSilk, Vidmoly, Vidmolybiz, Vidmolyme, Vidmolyto

### Community 80 - "StreamPlay Captcha Extractor"
Cohesion: 0.22
Nodes (5): Source, Streamplay, VinovoFileResp, VinovoSi, VinovoTo

### Community 81 - "StreamTape Extractors"
Cohesion: 0.23
Nodes (7): ShaveTape, StreamTape, StreamTapeNet, StreamTapeXyz, Watchadsontape, Userload, evalJs()

### Community 82 - "SyncRedirector Meta-Provider"
Cohesion: 0.15
Nodes (10): SyncRedirector, safeAsync(), SyncIdName, Anilist, Imdb, Kitsu, LocalList, MyAnimeList (+2 more)

### Community 85 - "Gofile Extractor"
Cohesion: 0.38
Nodes (6): AccountData, AccountResponse, Gofile, GofileData, GofileFile, GofileResponse

### Community 87 - "Dailymotion Extractors"
Cohesion: 0.38
Nodes (6): Dailymotion, Geodailymotion, Metadata, Quality, SubtitleData, SubtitlesWrapper

### Community 88 - "HDStreamable Extractors"
Cohesion: 0.44
Nodes (7): HDStreamAble, PeaceMakerst, PeaceResponse, Teve2ApiResponse, Teve2Link, Teve2Media, VideoSource

### Community 89 - "JWPlayer Sites"
Cohesion: 0.30
Nodes (10): BgwpCC, BigwarpArt, BigwarpIO, DesuArcg, DesuDrive, DesuOdchan, DesuOdvip, JWPlayer (+2 more)

### Community 92 - "Web Platform Targets"
Cohesion: 0.38
Nodes (6): buildOptions(), getCurrentLocale(), IntlDisplayNames, localizedLanguageName(), makeDisplayNames(), navigatorLanguage()

### Community 93 - "WcoHelper Extractor"
Cohesion: 0.36
Nodes (3): ExternalKeys, NewExternalKeys, WcoHelper

### Community 94 - "MixDrop Extractors"
Cohesion: 0.33
Nodes (9): Mdy, MixDrop, MixDropAg, MixDropBz, MixDropCh, MixDropPs, MixDropSi, MixDropTo (+1 more)

### Community 95 - "Filesim Extractors"
Cohesion: 0.38
Nodes (9): Ahvsh, Filesim, Guccihide, Movhide, Moviesm4u, Multimoviesshg, StreamhideCom, StreamhideTo (+1 more)

### Community 96 - "FireStream Extractors"
Cohesion: 0.33
Nodes (3): Firestream, FirestreamSite, VideoResponse

### Community 101 - "ContentX Extractors"
Cohesion: 0.36
Nodes (7): ContentX, FourCX, FourPichive, FourPlayRu, Hotlinger, Pichive, PlayRu

### Community 102 - "Linkbox Extractor"
Cohesion: 0.64
Nodes (5): Data, ItemInfo, Linkbox, Resolutions, Responses

### Community 104 - "Coroutines Dispatchers"
Cohesion: 0.39
Nodes (5): ioSafe(), ioWorkSafe(), main(), runOnMainThreadNative(), WorkerThread

### Community 106 - "Download Options Card"
Cohesion: 0.29
Nodes (5): DownloadOptionsSheet(), DownloadTarget, RadioRow(), Season, Single

### Community 107 - "Tantifilm Extractor"
Cohesion: 0.50
Nodes (3): Tantifilm, TantifilmData, TantifilmJsonData

### Community 108 - "Vicloud Extractor"
Cohesion: 0.50
Nodes (3): Responses, Sources, Vicloud

### Community 109 - "VideoSeyred Extractor"
Cohesion: 0.64
Nodes (4): VideoSeyred, VideoSeyredSource, VSSource, VSTrack

### Community 110 - "Vidoza Extractor"
Cohesion: 0.39
Nodes (3): Videzz, Vidoza, VinovoVideoData

### Community 112 - "CI Workflow"
Cohesion: 0.29
Nodes (5): APK Artifact Upload, Build APK Workflow, Failure Summary Report, GitHub Release Publishing, Release APK Build (assembleRelease)

### Community 115 - "HxFile Extractor"
Cohesion: 0.52
Nodes (6): Aico, Hxfile, KotakAnimeid, Neonime7n, Neonime8n, Yufiles

### Community 116 - "MailRu Extractor"
Cohesion: 0.62
Nodes (3): MailRu, MailRuData, MailRuVideoData

### Community 117 - "TauVideo Extractor"
Cohesion: 0.62
Nodes (3): TauVideo, TauVideoData, TauVideoUrls

### Community 118 - "Uqload Extractor"
Cohesion: 0.48
Nodes (5): Uqload, Uqload1, Uqload2, Uqloadbz, Uqloadcx

### Community 123 - "YouTube Variants"
Cohesion: 0.53
Nodes (4): YoutubeExtractor, YoutubeMobileExtractor, YoutubeNoCookieExtractor, YoutubeShortLinkExtractor

### Community 124 - "AutoDownloadMode"
Cohesion: 0.33
Nodes (5): AutoDownloadMode, All, Disable, FilterByLang, NsfwOnly

### Community 128 - "Download Actions"
Cohesion: 0.40
Nodes (5): DownloadAction, CANCEL, PAUSE, RESUME, RETRY

### Community 129 - "OkHttp SSL Workarounds"
Cohesion: 0.60
Nodes (4): OkRuHTTP, OkRuHTTPMobile, OkRuSSL, OkRuSSLMobile

### Community 130 - "ShowStatus Metadata"
Cohesion: 0.40
Nodes (3): ShowStatus, Completed, Ongoing

### Community 133 - "VPN Status Helpers"
Cohesion: 0.50
Nodes (4): VPNStatus, MightBeNeeded, None, Torrent

## Knowledge Gaps
- **129 isolated node(s):** `Loading`, `Single`, `Idle`, `Loading`, `Failed` (+124 more)
  These have ≤1 connection - possible missing edges or undocumented components. (Counts symbols only; 572 node(s) total have ≤1 connection when file, concept and rationale nodes are included.)
- **46 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `logError()` connect `MVVM Safe Calls` to `Coroutines Utilities`, `EmbedGram Extractor`, `StringUtils & Base64`, `CloudMailRu & GamoVideo`, `TMDb Provider`, `StreamWish & Ksoup`, `JS Interpreter Core`, `Local Data Stores`, `Extensions Browser`, `Engine Gateway`, `AsianEmbed Helpers`, `KMP Platform Context`, `CommonActivity Runtime`, `Extension Store Persistence`, `Download Migration`, `Coroutine Architecture Extensions`, `Cda Extractor`, `DataStore Persistence`, `TMDb Cross-Provider`, `APIHolder Registry`, `SyncRedirector Meta-Provider`, `AppUtils JSON`, `MyDramaList Provider`, `Plugin Manager`, `OkHttp Interceptors`, `Coroutines Dispatchers`?**
  _High betweenness centrality (0.141) - this node is a cross-community bridge._
- **Are the 24 inferred relationships involving `newExtractorLink()` (e.g. with `.getUrl()` and `.getUrl()`) actually correct?**
  _`newExtractorLink()` has 24 INFERRED edges - model-reasoned connections that need verification._
- **What connects `Loading`, `Single`, `Idle` to the rest of the system?**
  _129 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `JS Interpreter Tests` be split into smaller, more focused modules?**
  _Cohesion score 0.02020097379053144 - nodes in this community are weakly interconnected._
- **Why does `ExtractorLink` connect `AES Crypto Extractors` to `Android Activity Integration`, `Extractor Serialization Models`, `Kotlin Test Assertions`, `EmbedGram Extractor`, `Download Foreground Service`, `StringUtils & Base64`, `CloudMailRu & GamoVideo`, `StreamWish & Ksoup`, `Fastream & Filegram`, `StreamSB Extractors`, `FlyFile & Player Switch`, `FileMoon Extractors`, `AsianEmbed Helpers`, `Episode Queue`, `CineMM Extractors`, `Details Screen`, `Dood Extractors`, `ByseSX AES Helpers`, `Vidara & Misc Extractors`, `XStreamCDN & Fembed`, `GDMirrorBot Helpers`, `Voe Extractors`, `Blogger Extractor`, `Cda Extractor`, `TMDb Cross-Provider`, `Subtitle Style Store`, `Up4Stream Extractor`, `Odnoklassniki Extractor`, `StreamPlay Captcha Extractor`, `StreamTape Extractors`, `JWPlayer Sites`, `FireStream Extractors`, `Linkbox Extractor`, `YouTube Extractor`, `Tantifilm Extractor`, `Vicloud Extractor`, `Vidoza Extractor`, `DRM Extractor Links`, `YouTube Variants`?**
  _High betweenness centrality (0.102) - this node is a cross-community bridge._
- **Should `JS Expressions & AST` be split into smaller, more focused modules?**
  _Cohesion score 0.014084507042253521 - nodes in this community are weakly interconnected._
- **Why does `JsInterpreterTest` connect `JS Interpreter Tests` to `JS Expressions & AST`, `Rhino JS Engine`, `Coroutines Utilities`, `JS Interpreter Runtime`, `JS Math Tests`?**
  _High betweenness centrality (0.086) - this node is a cross-community bridge._