# MuseMeta (MyMusicPlayer) — AGENTS.md

## Build System

| Component | Version |
|---|---|
| Gradle | 9.4.1 |
| AGP | 9.2.1 |
| Kotlin | 2.2.10 |
| KSP | 2.3.6 (decoupled KSP2) |
| Koin | 4.0.3 |
| Room | 2.7.1 |
| Media3 | 1.5.1 |
| compileSdk/targetSdk | 35 |
| minSdk | 29 |
| JDK | 26 (only JDK available on Windows) |

**Key constraint**: JDK 26 requires AGP 9.x + Gradle 9.x + Kotlin 2.2.x. Downgrading is NOT possible without a different JDK.

## Build & Install

```bash
# Clean + build debug APK
./gradlew clean assembleDebug

# Build only
./gradlew assembleDebug

# Install (package: com.mymusicplayer.musemeta)
adb uninstall com.mymusicplayer.musemeta
adb install app/build/outputs/apk/debug/app-debug.apk

# Build a signed release AAB (for Play Store or sideload sharing)
./gradlew bundleRelease

# Unit tests (JVM only — see Testing)
./gradlew test

# Build release for non-debug performance load
./gradlew assembleRelease
adb install app/build/outputs/apk/release/app-release.apk
```

**Gradle properties**: `org.gradle.jvmargs` includes `-Djava.version=21` and `--add-opens java.base/java.lang=ALL-UNNAMED` — required for JDK 26.

**Release signing is NOT configured.** `buildTypes.release` has no `signingConfig`. `assembleRelease` produces an *unsigned* APK that won't install. To produce an installable release APK for local sharing, temporarily add `signingConfig = signingConfigs.getByName("debug")` to the release block, build `assembleRelease`, then revert. Do NOT commit that line. For real distribution, generate a proper upload keystore.

## Debug-build performance tax (verified gotcha)

A debug build (`assembleDebug` / Run "app") is **visibly less smooth** than release — Compose enables extra recomposition checks in debuggable builds and ART does not optimize. This is a build-type artifact, NOT a code bug.

**Never use debug Run as the smoothness acceptance test.** Verify UI perf with:
- Android Studio "Run app as profileable", OR
- a release build (`assembleRelease` with temp debug signing) installed on device.

If it's smooth there, the code is fine. This fooled a full debugging session once — the "jank" was the debug tax all along.

## Why Koin (not Hilt)

Hilt needs KSP/KAPT annotation processing. Neither works with this toolchain:
- **KSP**: Hilt 2.59.2 has no `KspHiltAndroidAppProcessor` → base classes never generated
- **KAPT**: `kotlin-kapt` conflicts with AGP 9.x built-in Kotlin support

**Solution**: Koin 4.0.3 (pure Kotlin DI, no annotation processors). Services use `KoinComponent` + `by inject()`. ViewModels resolve via `koinViewModel()` from `koin-androidx-compose`.

## Architecture

```
com.mymusicplayer
├── MusicPlayerApp.kt           Application, MultiDex, startKoin { }
├── MainActivity.kt             Single activity, permission request + auto-scan
├── MainScreen.kt               Scaffold + MiniPlayerBar + NavGraph
├── data/
│   ├── audio/                  MusicPlayerController (ExoPlayer), AudioFocusManager
│   ├── db/
│   │   ├── entity/             7 entities (Track, Album, Artist, TrackArtist, Playlist, PlaylistEntry, EqualizerPreset)
│   │   ├── dao/                4 DAOs (Track, Album, Artist, Playlist)
│   │   ├── converter/          Room TypeConverters
│   │   └── AppDatabase.kt      Room DB (version 1, exportSchema=true)
│   ├── preferences/            SettingsDataStore (DataStore Preferences)
│   └── scanner/                MediaStoreScanner, FileSystemScanner, MetadataParser, ScanRepository
├── di/
│   ├── AppModule.kt            Koin module: repos, controllers, VMs
│   └── DatabaseModule.kt       Koin module: Room DB + 4 DAOs
├── domain/
│   ├── model/                  Track, Album, Artist, Playlist, EqualizerPreset
│   ├── repository/             MusicRepository (interface + impl)
│   └── usecase/                SmartSortUseCase
├── service/
│   ├── MusicService.kt         Media3 MediaSessionService + KoinComponent
│   └── ScanService.kt          Foreground service (dataSync) + KoinComponent
└── ui/
    ├── components/             AlphabetIndexBar, EqualizerView, MarqueeText, MiniPlayerBar,
    │                           PlaylistSelectorSheet, SleepTimerDialog, TrackActionsSheet
    ├── navigation/             Routes.kt (sealed class), NavGraph.kt (NavHost)
    ├── screens/
    │   ├── home/               HomeScreen, FavoritesScreen, RecentlyPlayedScreen,
    │   │                       PlaylistsScreen, PlaylistDetailScreen, HomeViewModel
    │   ├── player/             PlayerScreen + PlayerViewModel
    │   ├── search/             SearchScreen + SearchViewModel
    │   ├── settings/           SettingsScreen + SettingsViewModel
    │   ├── scan/               ScanScreen + ScanViewModel
    │   ├── directory_picker/   DirectoryPickerScreen + DirectoryPickerViewModel
    │   ├── albums/             (empty — not yet implemented)
    │   ├── artists/            (empty — not yet implemented)
    │   ├── playlists/          (empty — not yet implemented)
    │   ├── metadata/           (empty — not yet implemented)
    │   ├── tracks/             (empty — not yet implemented)
    │   ├── library/            (empty — not yet implemented)
    │   └── organize/           (empty — not yet implemented)
    └── theme/                  Color, Theme (Material3 dark), Type
```

## Navigation

10 routes in `Screen` sealed class: `Home`, `Search`, `NowPlaying`, `Settings`, `Scan`, `DirectoryPicker`, `Favorites`, `RecentlyPlayed`, `Playlists`, `PlaylistDetail` (with `{playlistId}` arg).

Start: `Home`. No bottom nav — `HomeScreen` has internal tabs. `MiniPlayerBar` shows on all screens except `NowPlaying`.

## DI Setup (Koin)

`MusicPlayerApp.kt` calls `startKoin { androidContext(this); modules(appModule, databaseModule) }`.

`AppModule.kt` provides: `MusicRepository`, `MusicPlayerController`, `AudioFocusManager`, scanners (`MediaStoreScanner`, `FileSystemScanner`, `MetadataParser`, `ScanRepository`), `SettingsDataStore`, and 6 ViewModels. `DatabaseModule.kt` provides Room DB + 4 DAOs.

Services use `KoinComponent` + `by inject()` (no constructor injection for Android services).

## Permissions Flow

1. Android 13+: request `READ_MEDIA_AUDIO`. Android 10-12: `READ_EXTERNAL_STORAGE`
2. On grant → auto-start `ScanService` with `ACTION_START_SCAN`
3. `POST_NOTIFICATIONS` requested as nice-to-have on TIRAMISU+
4. `FOREGROUND_SERVICE_DATA_SYNC` (API 34+) for scan, `FOREGROUND_SERVICE_MEDIA_PLAYBACK` for music

Both foreground service types are correctly declared on their `<service>` elements in the manifest, with matching permission declarations. No `MANAGE_EXTERNAL_STORAGE` is used or requested (scoped storage only).

## Scan System

- **Hybrid**: MediaStore query (fast, has embedded metadata) + filesystem scan (API 29 only, supplement)
- Parallel parsing: 4 concurrent parsers (`PARALLEL_PARSERS` = 4)
- Album art extraction: jAudiotagger first, `MediaMetadataRetriever` fallback → cached to `cacheDir/album_art/{albumId}.jpg`
- Album dedup: merges by lowercase title, keeps highest ID
- Artist parsing handles: `feat.`, `ft.`, `&`, `/`, `,`, `;`, `and` — deduplicates

## Player System

- **Media3 ExoPlayer** via `MusicPlayerController` singleton
- `PlaybackMode` enum: `SHUFFLE` / `LIST` / `SINGLE` — cycled via notification button
- `PlaybackMode` is persisted across restarts via `SettingsDataStore` (`PLAYBACK_MODE` key); restore happens in `MusicPlayerController.initialize()`
- Favourite toggle via notification custom command
- Notification has custom layout with mode cycle + favourite buttons, dynamic album art
- `MusicService` extends `MediaSessionService` with custom `MediaSession.Callback` for custom commands
- **Threading**: ExoPlayer mutations (`shuffleModeEnabled`, `repeatMode`) MUST run on the main thread. The restore block in `initialize()` wraps them in `withContext(Dispatchers.Main)` — a previous crash ("Player is accessed on the wrong thread") came from doing this on `Dispatchers.Default`.

## Testing

- JUnit 5 (Jupiter) + MockK + Turbine + coroutines-test in `libs.versions.toml`
- 2 test files under `app/src/test/`:
  - `MetadataParserTest.kt` — pure unit tests for `parseArtists()` (JVM, runs under `./gradlew test`)
  - `TrackDaoTest.kt` — Room in-memory DB, **AndroidJUnit4 instrumentation test** — does NOT run under `./gradlew test`; needs `./gradlew connectedAndroidTest` + a device/emulator
- No androidTest/ files
- Known pre-existing failure: `PlayerViewModelQueueTest` fails on an unmodified tree (unrelated to feature work). Do not treat it as a regression you introduced.

## ProGuard / R8

- **Debug**: `isMinifyEnabled = false`
- **Release**: `isMinifyEnabled = true`, uses `proguard-android-optimize.txt` + `proguard-rules.pro`
- jAudiotagger: `-keep class org.jaudiotagger.** { *; }` + dontwarn AWT/Swing classes
- Room entities: `-keep class com.mymusicplayer.data.db.entity.** { *; }`
- Kotlinx Serialization: `-keepclassmembers class kotlinx.serialization.json.** { *; }`
- No Hilt-related rules remain (the project never shipped Hilt config).

## MultiDex

Enabled in `defaultConfig` (`multiDexEnabled = true`) with `multiDexKeepProguard = file("multidex-keep.pro")`. `MusicPlayerApp.attachBaseContext` calls `MultiDex.install(this)`.

## Key Files

- `app/build.gradle.kts` — Plugin + dependency config (build types, signing)
- `gradle/libs.versions.toml` — Version catalog
- `app/proguard-rules.pro` — ProGuard keep rules
- `app/multidex-keep.pro` — Primary dex keep rules
- `app/src/main/AndroidManifest.xml` — Permissions + services + foreground types
- `app/src/main/java/com/mymusicplayer/di/AppModule.kt` — All Koin bindings
- `app/src/main/java/com/mymusicplayer/data/audio/MusicPlayerController.kt` — Player + persistence + threading
- `.sisyphus/plans/android-music-player.md` — Build debugging history

## Key Dependencies

- **Compose BOM** 2024.12.01 + Material3 + Navigation Compose 2.8.5
- **Koin** 4.0.3 (android + compose)
- **Room** 2.7.1 via KSP
- **Media3** 1.5.1 (exoplayer + session + ui)
- **AndroidX Media** 1.7.0 (for MediaStyle notification compat)
- **Coil** 2.7.0 (image loading in Compose)
- **Kotlinx Serialization** 1.7.3
- **jAudiotagger** 3.0.1 (audio metadata read/write)
- **Multidex** 2.0.1
- **DataStore Preferences** 1.1.2
