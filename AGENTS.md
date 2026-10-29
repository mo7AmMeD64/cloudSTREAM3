# AGENTS.md — KodaStream / cloud3

Instructions for AI coding agents (Antigravity, Claude Code, Cursor, Aider, etc.)
working on this repository. Read this file completely before touching any code.

---

## 1. Project in one paragraph

KodaStream (app name: **cloud3**) is an Android app for browsing and watching
media through third‑party extensions. It combines Koda's Material 3 Expressive
Compose UI (`:app`) with a full CloudStream engine fork (`:library`) that loads
`.cs3` extension DEX files at runtime. Package roots:

- `com.kodastream.app.*` — app UI, stores, engine bridge, downloads, theming
- `com.lagradost.cloudstream3.*` — CloudStream engine (providers, extractors,
  plugin loader, ported player)

The player, dialogs, and several helpers under `com.lagradost.cloudstream3.ui.*`
are **ported verbatim-ish from CloudStream** and still use ViewBinding/RecyclerView.
Do not "modernise" them to Compose unless the task explicitly asks for it — the
ported player depends on their exact behaviour.

License: **GPL‑3.0**. Attribution to `mo7AmMeD64` and Nuvio must stay visible in
the repo, the About screen, and `NOTICE`.

---

## 2. Build & verify — always run these

```bash
# JDK 17 required. Android SDK: compile 37, target 36, min 26.
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug

# Optional: run engine tests
./gradlew :library:jvmTest

# Manual smoke test on emulator/device:
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.kodastream.app/.MainActivity
adb exec-out screencap -p > shot.png
```

- Never declare a task "done" if `assembleDebug` or `testDebugUnitTest` fails.
- For any UI change: attach a screenshot (or describe exactly what you saw).
- For any store/pref change: add or update a JVM unit test.

### 2.1 Verification without a device attached

Check with `adb devices`. If no device is listed:

- `./gradlew :app:assembleDebug :app:testDebugUnitTest` must pass — this is
  non-negotiable.
- For UI changes, provide a TEXT description of what changed and where:
  - Which composables were added/modified.
  - Which strings they render.
  - Parent → child hierarchy, e.g.:
    "New SwitchRow inside SettingsGroup('Playback'), placed after the
    keep-screen-on row, labeled 'Allow invalid SSL' with supporting text."
- Explicitly state: "Screenshot deferred — user must verify on device."
- NEVER claim a visual verification you did not perform.

---

## 3. Hard rules — do not break these

### 3.1 `:library` is an ABI surface

`.cs3` extensions link against `library/` classes at runtime
(`MainAPI`, `ExtractorApi`, `ExtractorLink`, `CloudStreamApp.getKey/setKey`,
`CommonActivity`, …). Changing public signatures, renaming, or deleting
anything under `library/src/commonMain` will crash user‑installed extensions.
→ **Do not touch `library/` in a task that does not explicitly mention it.**

### 3.2 Pinned dependency versions

Do **not** bump these without an explicit ticket and manual device testing:

| Library ↕▾ | Pinned version ↕▾ | Why ↕▾ |
|---|---|---|
| −`jackson-module-kotlin` | `2.13.1` | Newer versions require minSdk ≥ 26 (OK here) but break some extensions |
| −`rhino` | `1.8.1` | `1.9+` requires minSdk ≥ 26 and changes JS semantics for extensions |
| −`coil` | `3.3.0` (strictly) | Newer requires jvmTarget 11+; jvmTarget is 11 already but not tested |
| `conscrypt-android` | `2.5.2` | `2.5.3` crashes the process |
| `newpipeextractor` | `v0.26.3` | API changes break `YoutubeExtractor.jvmCommon.kt` |
⚙

Other versions are in `gradle/libs.versions.toml`. `axMaterial3 = 1.5.0-alpha29`
is fragile (expressive APIs) — do not bump without an explicit task.

### 3.2.1 Coil: two coexisting dependencies

Two Coil artifacts live on the classpath and serve different modules. Both are pinned.

| Artifact | Version | Used by | Purpose |
|---|---|---|---|
| `io.coil-kt.coil3:coil` (+ `coil-network-okhttp`) | `3.3.0` (strictly) | `:library` only | CloudStream engine, ported `ImageLoader.kt` |
| `io.coil-kt:coil-compose` (`axCoil`) | `2.7.0` | `:app` only | Compose `RemoteImage`, `AsyncImage` |

- New Compose UI → `axCoil` (Coil 2.7) only.
- Do not introduce `io.coil-kt.coil3:coil-compose` into `:app`.
- Do not migrate `:app` to Coil 3 unless a task explicitly says so.

### 3.3 Material 3 Expressive opt‑ins are required

Any composable that uses `MotionScheme`, `HorizontalFloatingToolbar`,
`LinearWavyProgressIndicator`, `LoadingIndicator`, `SegmentedListItem`,
`ButtonGroupDefaults`, `ToggleButton` grouped, `HorizontalCenteredHeroCarousel`,
or `MaterialExpressiveTheme` must be annotated:

```
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
```

The app already opts in globally for compile flags in `app/build.gradle.kts`,
but runtime `@OptIn` annotations on composables are still required by the
compiler.

### 3.4 Compose vs. XML themes

- Compose UI → `KodaStreamTheme` (`ui/theme/Theme.kt`)
- Ported player & dialogs (ViewBinding) → `Theme.KodaStream` / `Theme.AlertDialog`
/ `DialogFullscreenPlayer` in `res/values/themes.xml` + `cloudstream_styles.xml`

If a task touches a ported layout, verify it still renders under
`Theme.AlertDialogCustom` and with `?attr/textColor` colors.

### 3.5 Strings & localization

- Every user‑visible string lives in `app/src/main/res/values/strings.xml`.
- Every new string needs an Arabic translation in `values-ar/strings.xml`.
- Ported CloudStream strings are in `res/values/ported_string.xml` — do not
translate them unless the whole ported UI is being localized at once.

### 3.6 Permissions

Do not add new `<uses-permission>` entries without explicit approval from
the maintainer. Existing set is minimal on purpose
(`INTERNET`, `ACCESS_NETWORK_STATE`, `WAKE_LOCK`, `FOREGROUND_SERVICE`,
`FOREGROUND_SERVICE_DATA_SYNC`, `POST_NOTIFICATIONS`).

### 3.7 No new network or telemetry

No analytics, crash reporting, or remote config. No HTTP calls to
non‑provider endpoints (update checker uses only GitHub Releases,
already configured in `UpdateChecker.kt`).

### 3.8 License hygiene

Keep the `NOTICE` file, keep GPL headers on ported files, keep the About
screen attributions. New files that copy from another GPL project need a
header comment naming the origin and its license.

### 3.9 Settings source of truth

There are two sources for player-related preferences:

1. `PreferenceManager.getDefaultSharedPreferences(context)` — the ported
   CloudStream player reads from here (`speedup_key`, `double_tap_*`,
   `software_decoding_key`, `video_buffer_*`, `autoplay_next_key`, etc.).
2. `DataStoreHelper` (`playBackSpeed`, `resizeMode`) — CloudStream's own
   persistent store for a few player values.
3. `AppPrefs` — KodaStream's own `SharedPreferences` wrapper, used for
   app-level settings (`keep_screen_on`, `pause_history`, `download_*`).

Rules for any UI that exposes player settings:
- Read/write the SAME key the ported player reads. If the player uses
  `PreferenceManager` with resource string `X`, the UI must use the same.
- Do NOT redirect `AppPrefs` to delegate to `DataStoreHelper` in a UI task.
  Unifying the source of truth is a separate tracked architectural task.
- Do NOT delete `AppPrefs.playbackSpeed` or `AppPrefs.keepScreenOn` in a UI
  task, even if they appear unused. Removal is a separate task that includes
  verifying no extension reads them.

---

## 4. Architecture map

```
app/src/main/java/com/kodastream/app/
├── MainActivity.kt              AppCompatActivity host (Compose via setContent)
├── data/                        Stores & prefs — all backed by CloudStream DataStore JSON
│   ├── AppPrefs.kt              SharedPreferences wrapper for app settings
│   ├── LibraryStore.kt          Favorites + Watchlist (per‑provider keys)
│   ├── HistoryStore.kt          One entry per title, newest first
│   ├── WatchedStore.kt          Per‑episode watched keys
│   ├── DownloadStore.kt         Download records + DownloadLocations (SAF)
│   ├── SearchHistoryStore.kt    Recent queries
│   ├── SubtitleStyleStore.kt    ⚠ unused — see "Known issues"
│   ├── UpdateChecker.kt         GitHub Releases ETag poller
│   └── NavArgs.kt               In‑memory screen args bridge (Int -> args)
├── downloads/
│   ├── DownloadService.kt       Foreground service: HLS + direct, pause/resume
│   └── DownloadMigration.kt     One‑time move into per‑title folders
├── engine/
│   ├── Engine.kt                Guarded wrappers around extension calls
│   └── AppBootstrap.kt          Loads installed plugins once per process
├── ui/
│   ├── AppNav.kt                NavHost + bottom pill + side bar
│   ├── home/                    Home, expanded section, hero carousel/cinematic
│   │   └── hero/                Ported from Nuvio (see NOTICE)
│   ├── search/                  SearchViewModel + SearchScreen
│   ├── library/                 LibraryViewModel, LibraryScreen, DownloadsSection
│   ├── history/                 HistoryScreen
│   ├── details/                 DetailsScreen (big), DownloadOptionsSheet, ExternalDownload
│   ├── player/                  PlayerActivity — hosts ported GeneratorPlayer
│   ├── extensions/              ExtensionsScreen + RepositoryManager wiring
│   ├── settings/                SettingsScreen, SettingsComponents
│   ├── setup/                   SetupWizard (first run)
│   ├── sources/                 SourceSheet + SourceViewModel (provider selection)
│   ├── components/              Shared Compose (PosterCard, Skeleton, Motion, …)
│   ├── theme/                   KodaStreamTheme, palettes, PaletteStyle
│   └── update/                  UpdateDialog + UpdateViewModel
└── com/lagradost/cloudstream3/  Ported CloudStream layer (do not refactor to Compose)
    ├── plugins/                 PluginManager, RepositoryManager, ExtensionStore
    ├── network/                 CloudflareKiller, DdosGuardKiller, RequestsHelper
    ├── ui/player/               Original CloudStream player (Media3)
    └── ui/subtitles/            SubtitlesFragment, SubtitleStyleDialog

library/src/commonMain/kotlin/com/lagradost/cloudstream3/
├── MainAPI.kt                   Base classes, LoadResponse family, Score, Episode
├── extractors/                  ~120 ExtractorApi implementations
├── metaproviders/               TmdbProvider, TraktProvider, MyDramaList
├── plugins/                     BasePlugin, Manifest, CloudstreamPlugin
└── utils/                       ExtractorLink, M3u8Helper, JsUnpacker, DataStore, …
```

**Rule of thumb:** put new UI under `com.kodastream.app.ui.*` in Compose, keep
everything under `com.lagradost.cloudstream3.*` as‑is unless the task is
specifically about the ported layer.

---

## 5. Store pattern — follow it

Every store in `data/` follows the same shape:

```
object FooStore {
    private const val STORE_KEY = "foo"
    private const val MAX_ENTRIES = 200

    private val _entries = MutableStateFlow<List<Foo>?>(null)
    val entries: StateFlow<List<Foo>?> = _entries.asStateFlow()

    @Volatile private var loaded = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun current(context: Context): List<Foo> { … loads once … }

    fun upsert(context: Context, item: Foo) {
        val next = /* new list */
        _entries.value = next
        scope.launch { context.setKey(STORE_KEY, next) }   // off main thread
    }
}
```

- Reads/writes go through `com.lagradost.cloudstream3.utils.DataStore.getKey/setKey`
(CloudStream JSON, `rebuild_preference` SharedPreferences).
- Publishing to the `StateFlow` happens synchronously; serialization happens
on `Dispatchers.Default`.
- **Do not add a new store without following this pattern** and adding a JVM
test in `app/src/test/java/com/kodastream/app/data/`.

**Known hot spot (fix when touched):** `HistoryStore.updateProgress` and
`DownloadStore.update` re‑serialize the whole JSON array on every tick. If
a task touches either, consider a "dirty flag + debounced write" or moving
to Room.

---

## 6. Testing conventions

- JUnit 4, plain Kotlin, no Robolectric, no instrumentation for unit tests.
- Tests live next to the feature: `app/src/test/java/com/kodastream/app/<feature>/`.
- Pure functions used by the UI are extracted specifically to be testable
(see `HomeSourceResolverTest`, `SectionPagerTest`, `EpisodeBadgeTest`,
`ThemeContrastTest`).
- Do **not** add tests that need `Context` unless you also add a
`TestRule`/`MockContext` — the existing suite is pure.
- For ported `library/` code, use `library/src/commonTest` (Kotlin test).

Every PR / agent task should include one of:

- a new test in `app/src/test`, or
- a screenshot / log snippet in the task summary proving the change works.

---

## 7. Common tasks — recipes

### 7.1 Add a new screen

1. Create `ui/<feature>/<Feature>Screen.kt` with `@Composable fun <Feature>Screen(...)`
accepting `contentPadding: PaddingValues` (for nav bar insets) and callbacks.
2. Add a route in `Routes` + `AppNavGraph` in `ui/AppNav.kt`.
3. If it's a top‑level tab, add it to `topLevelDestinations` (icon + label).
4. Add strings to `values/strings.xml` and `values-ar/strings.xml`.
5. If it needs state, use `AndroidViewModel` when it touches `Context`,
otherwise plain `ViewModel` with `StateFlow`.
6. Take a screenshot on the emulator and attach it.

### 7.2 Add a new setting

1. Add a key in `data/AppPrefs.kt` (getter + setter, default value).
2. Add a `SwitchRow` / `NavigationRow` / `ChoiceRow` in
`ui/settings/SettingsScreen.kt` inside an appropriate `SettingsGroup`.
3. Wire it to the feature that reads it. **Do not leave it orphan.**
4. Add a test if the setting affects pure logic.

### 7.3 Fix a provider‑facing engine bug

- Change only files under `library/src/commonMain/...` and only if the task
is explicitly about the engine.
- Run `./gradlew :library:jvmTest` before and after.
- Verify no public API signatures changed (`git diff` + manual scan). If they
did, that is a **breaking change** and requires maintainer approval.

### 7.4 Add a new extractor to `library/extractors/`

- Copy the pattern of an existing extractor of the same type
(`ExtractorApi` subclass, `newExtractorLink`, `M3u8Helper.generateM3u8`
for HLS).
- Register it in the `extractorApis` list in `library/utils/ExtractorApi.kt`
(top of the list = highest priority).
- Add a JVM test if the extractor logic can be tested without network.

### 7.5 Touch the player

- Player code lives in `com.lagradost.cloudstream3.ui.player.*` and is
**ported**. Do not refactor its structure.
- Any change must be verified on a physical device with a real HLS + MP4
stream: play, seek, change subtitle, rotate, PiP, exit.
- The `CS3IPlayer` `ignoreSSL = true` default is a known security issue —
see `Known Issues`. A task that fixes it must be approved by the maintainer.

---

## 8. Code style observed in this repo

- Kotlin, 4‑space indent, no trailing commas in most files (mixed — match
surrounding file).
- Composables: PascalCase, one file per screen or component family.
- ViewModels: `<Feature>ViewModel`, `MutableStateFlow` + `asStateFlow()`.
- Stores: `object <Name>Store`, `_entries`/`entries`, `current(context)`,
`upsert/remove/clear`.
- Stores use `@Volatile private var loaded = false` for lazy load.
- Keys in CloudStream `DataStore` are `"$provider|$url"` for per‑title and
`"$provider|$url|$season|$episode"` for per‑episode.
- Comments explaining **why**, not what — the code already says what.
- English only in code and comments (strings are localised).

---

## 9. Known issues & TODO list

These are tracked here so agents do not re‑discover them and do not
"fix" them accidentally. Pick a task only when asked.

**Critical (security / data):**

- `DownloadLocations.freeBytes` ignores SAF tree → wrong free‑space check.
- `CS3IPlayer.ignoreSSL = true` default.
- `AppPrefs.playbackSpeed` and `AppPrefs.keepScreenOn` are set but not read
by the player (dead settings).
- `SubtitleStyleStore` is not connected to the ported `SubtitlesFragment`;
the player reads `SubtitlesFragment.SUBTITLE_KEY` directly.

**Correctness:**

- `DownloadService.downloadHls` picks the highest‑bandwidth variant without
requiring audio → silent videos for some sources.
- HLS `#EXT-X-MAP` (fMP4) is not handled by `downloadHls`.
- `downloadDirect` does not detect a `200` response after a `Range` request
→ corrupted file on resume against non‑RFC servers.
- `downloadWifiOnly` waits passively — no `NetworkCallback`/WorkManager to
resume when Wi‑Fi connects.
- Search has no pagination (`Engine.search` always page 1).
- `GeneratorPlayer.generators` and `NavArgs.players`/`details` are unbounded
in‑memory maps.

**Performance:**

- `HistoryStore.updateProgress` and `DownloadStore.update` re‑serialize
full lists on every tick (`PlayerActivity` every 5s; `DownloadService`
every ~900ms).

**UX / missing UI:**

- No screen to configure player settings (buffers, decoding, seek,
auto‑rotate, PiP, subtitle style, quality profiles, compressor) even
though all the preference keys exist.
- No "Continue Watching" shelf on Home despite `HistoryStore` having the
data.
- No export/import of user data (`allowBackup=false`).

**Cleaning:**

- Unused colors in `ui/theme/Color.kt` (`Purple80`, `Pink40`, …).
- `AppPrefs.playbackSpeed` / `AppPrefs.keepScreenOn` if not wired.

**Non‑goals (do not "fix"):**

- Migrating ported XML player/dialogs to Compose.
- Removing `usesCleartextTraffic="true"` — some providers need HTTP.
- Touching `library/` for style reasons.
- Adding analytics or crash reporting.

---

## 10. Prompt templates for Antigravity / agents

Copy‑paste these when starting a task. They set scope, constraints, and
verification requirements.

### 10.1 Fix a bug

```
Task: Fix <bug one-liner>.

Affected area: <file(s)>.
Constraints from AGENTS.md apply (no library ABI change, no dep bumps,
no new permissions).

Steps:
1. Read the file(s) and propose a 3–5 line plan as an Artifact BEFORE editing.
2. Make the minimal change. If the fix requires refactoring, ask first.
3. Add or extend a unit test under app/src/test if the logic is pure.
4. Run `./gradlew :app:assembleDebug :app:testDebugUnitTest`.
5. Report: diff summary, test output, and anything you deliberately did not touch.
```

### 10.2 Add a screen

```
Task: Add <Feature> screen.

Requirements:
- Compose + Material3 Expressive (opt-in ExperimentalMaterial3ExpressiveApi).
- Route in AppNav.kt, top-level if the task says so.
- Strings in values/strings.xml AND values-ar/strings.xml.
- ViewModel with StateFlow; follow the store pattern if it persists data.
- contentPadding for nav bar insets.
- Skeleton placeholders while loading (use components/Skeleton.kt).

Verification:
- assembleDebug + testDebugUnitTest pass.
- Screenshot attached (adb exec-out screencap).
```

### 10.3 Implement Player Settings (tracked task, high value)

```
Task: Create ui/settings/PlayerSettingsScreen.kt exposing existing
PreferenceManager keys without touching the ported player code.

Keys to surface (all already read by the player or MainAPI):
- speedup_key, double_tap_enabled_key, double_tap_seek_time_key,
  autoplay_next_key, auto_rotate_video_key, pip_enabled_key,
  software_decoding_key, video_buffer_size_key, video_buffer_disk_key,
  video_buffer_length_key, subtitles_encoding_key, preview_seekbar_key,
  preferred_audio_language, subtitles_style (SubtitlesFragment.SUBTITLE_KEY).

Rules:
- Read/write only via PreferenceManager.getDefaultSharedPreferences.
- No changes to com.lagradost.cloudstream3.ui.player or SubtitlesFragment.
- Add entry in SettingsScreen via NavigationRow.
- Strings bilingual.
- Verify with a screenshot and a unit test on default values.
```

### 10.4 Add Continue Watching shelf (tracked task)

```
Task: Add a "Continue Watching" LazyRow above the first Home shelf,
sourced from HistoryStore where !isComplete and positionMs > 0.

Rules:
- Do not modify HistoryStore API. Read via HistoryStore.entries flow.
- Reuse PosterCard with a wavy progress indicator underneath.
- Show nothing when the list is empty (no skeleton flash on empty).
- Add HomeShelfResumeTest covering the filter/sort as a pure function.
- Screenshot required.
```

---

## 11. What NOT to do

- ❌ Do not `git push` or open PRs; leave changes in the working tree.
- ❌ Do not run `git reset --hard`, `git clean`, `rm -rf`, or any destructive
git command without explicit instruction.
- ❌ Do not touch `gradle-wrapper.properties` or `libs.versions.toml` in
a bug‑fix task.
- ❌ Do not rename Kotlin files "for clarity" — file names are referenced
in tests and imports.
- ❌ Do not add `TODO()` or `// TODO` placeholders in shipped code; either
implement it or open a tracked item in this file.
- ❌ Do not add Turkish, German, or other language strings — only English +
Arabic in this repo.
- ❌ Do not convert ported ViewBinding code to Compose.
- ❌ Do not format untouched files (avoid large diffs).

---

## 12. License & attribution — non‑negotiable

KodaStream is released under **GNU GPL v3.0**. When contributing:

- Keep the `LICENSE` file untouched.
- Keep the `NOTICE` file (Nuvio attribution) untouched.
- Keep `mo7AmMeD64` mentioned in About and README.
- Any new code derived from another GPL project must:

- carry a header naming the source project and its license,
- be added to `NOTICE` with the file's new location.
- Do not remove or hide the GPL notices.

---

## 13. Support & escalation

If a task conflicts with any rule above, or if a fix requires changing
`library/` ABI, bumping a pinned dependency, or adding a permission:
**stop and ask** — do not improvise.

If tests fail for a reason unrelated to your change, note it in the report
and continue (do not "fix" unrelated failures).

If a change touches the player, download service, or engine, always list
exact manual test steps taken (device/emulator, stream URL type, results).

