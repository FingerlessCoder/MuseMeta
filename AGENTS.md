# MuseMeta (MyMusicPlayer) — AGENTS.md

## Build System (CURRENT STATE — still broken)

| Component | Version |
|---|---|
| Gradle | 9.4.1 |
| AGP | 9.2.1 |
| Kotlin | 2.2.10 |
| KSP | 2.3.6 (decoupled KSP2) |
| Hilt | 2.59.2 |
| Room | 2.7.1 |
| compileSdk/targetSdk | 35 |
| minSdk | 29 |
| JDK | 26 (only JDK available on Windows) |

**Key constraint**: JDK 26 requires AGP 9.x + Gradle 9.x + Kotlin 2.2.x. Downgrading these is NOT possible without a different JDK.

## Known Crash (ClassNotFoundException on Honor device)

The app crashes on launch with:
```
java.lang.ClassNotFoundException: Didn't find class "com.mymusicplayer.MusicPlayerApp"
```

**Root cause (triple bind)**:
1. `@HiltAndroidApp` on `MusicPlayerApp` requires `Hilt_MusicPlayerApp` base class to be generated
2. Hilt's KSP support (2.59.2) does NOT include `KspHiltAndroidAppProcessor` — the generated `.java` file is never created
3. AGP 9.x built-in Kotlin is incompatible with KAPT plugin: `"The 'org.jetbrains.kotlin.kapt' plugin is not compatible with built-in Kotlin support"` — so switching to KAPT requires `android.builtInKotlin=false`

**What was tried (none worked)**:
- `ksp(libs.hilt.compiler)` → `Hilt_MusicPlayerApp.java` never generated
- `kapt(libs.hilt.compiler)` + `kotlin-kapt` plugin → rejected by AGP 9.x built-in Kotlin
- R8 minification (isMinifyEnabled=true) → R8 stripped Hilt classes because they weren't in classpath
- `multiDexEnabled = false` → D8 still splits into 20 dex files (AGP 9.x behavior)
- `multiDexKeepProguard` via old/new DSL → not respected by D8
- `variant.dexing` API → `@Incubating`, not exposed in AGP 9.2.1

**NOT yet tried**:
- `android.builtInKotlin=false` + `kotlin-android` plugin + `kotlin-kapt` plugin — would allow KAPT for Hilt, but requires reverting AGP 9.x new DSL
- Manually writing `Hilt_MusicPlayerApp.java` as a source file (workaround)

## Hilt + KSP vs KAPT

- **KSP**: Works for `@AndroidEntryPoint`, `@HiltViewModel`, DI factories, aggregated deps. Does NOT generate `Hilt_MusicPlayerApp.java` (`KspHiltAndroidAppProcessor` missing from hilt-compiler jar).
- **KAPT**: Works for everything including `@HiltAndroidApp`. But `kotlin-kapt` plugin conflicts with AGP 9.x built-in Kotlin.
- **Current config**: KSP for Room + (attempted) KAPT for Hilt. Not working due to built-in Kotlin issue.

## Architecture

```
com.mymusicplayer
├── MusicPlayerApp.kt           @HiltAndroidApp Application
├── MainActivity.kt             @AndroidEntryPoint single-activity
├── MainScreen.kt               Scaffold + bottom nav + NavHost
├── data/
│   ├── audio/                  MusicPlayerController, AudioFocusManager
│   ├── db/                     Room: AppDatabase, DAOs, Entities
│   ├── preferences/            SettingsDataStore (DataStore)
│   └── scanner/                MediaStoreScanner, MetadataParser, ScanRepository
├── di/                         AppModule, DatabaseModule (Hilt)
├── domain/
│   ├── model/                  Domain models (Track, Album, Artist, Playlist, etc.)
│   ├── repository/             MusicRepository interface + impl
│   └── usecase/                SmartSortUseCase
├── service/
│   ├── MusicService.kt         Media3 media playback service
│   └── ScanService.kt         Foreground service for media scan
└── ui/
    ├── components/             EqualizerView, SleepTimerDialog
    ├── navigation/             BottomNavBar, NavGraph, Routes
    ├── screens/
    │   ├── albums/             AlbumList, AlbumDetail + VM
    │   ├── artists/            ArtistList, ArtistDetail + VM
    │   ├── metadata/           MetadataEditorDialog + VM
    │   ├── player/             PlayerScreen + VM
    │   ├── playlists/          PlaylistList, PlaylistDetail + VM
    │   ├── settings/           SettingsScreen + VM
    │   └── tracks/             TrackListScreen + VM
    └── theme/                  Color, Theme, Type (Material3)
```

## Key Files

- `app/build.gradle.kts` — All plugin and dependency config
- `gradle/libs.versions.toml` — Version catalog (VERSION TOML)
- `app/src/main/AndroidManifest.xml` — `.MusicPlayerApp` entry (resolves via `namespace`)
- `app/src/main/java/com/mymusicplayer/MusicPlayerApp.kt` — Application class with `@HiltAndroidApp`
- `app/proguard-rules.pro` — ProGuard rules (keep Hilt classes, jAudiotagger, Room entities)
- `build.gradle.kts` — Root build (plugin declarations only)
- `local.properties` — SDK path
- `.sisyphus/plans/android-music-player.md` — Previous session plan

## Build Commands

```bash
# Clean + build debug APK
./gradlew clean assembleDebug

# Just build
./gradlew assembleDebug

# Install on device
adb uninstall com.mymusicplayer.musemeta
adb install app/build/outputs/apk/debug/app-debug.apk

# Check dex contents inside APK
unzip -p app/build/outputs/apk/debug/app-debug.apk classes.dex > /tmp/c.dex
dexdump /tmp/c.dex | grep "Class descriptor" | grep -i "musicplayer\|hilt_"
```

## Dex Splitting Issue

With AGP 9.x and `minSdk = 29`, D8 produces **20 dex files** even with `multiDexEnabled = false`. Total method count is only 35,363 (well under 64K). D8 splits based on internal heuristics, not method count.

**Total methods across all dex files**: ~35,363
**Primary dex methods**: ~17,831
**Dex count**: 20 files (`classes.dex` through `classes20.dex`)

The Honor device (`HRY-LX1T`) classloader fails to resolve classes from secondary dex files. Only `classes.dex` is scanned.

## Dependency Highlights

- **UI**: Jetpack Compose + Material3 + Navigation Compose
- **DI**: Hilt (via KSP/KAPT, currently broken for `@HiltAndroidApp`)
- **Database**: Room (via KSP)
- **Media**: Media3 ExoPlayer + Session
- **Audio metadata**: jAudiotagger 3.0.1 (adds AWT desktop class references — must be kept/dontwarn'd in ProGuard)
- **Image loading**: Coil
- **Navigation**: Compose Navigation with bottom nav (4 tabs: Tracks, Albums, Artists, Playlists)

## ProGuard Notes

- Must keep `com.mymusicplayer.Hilt_*` classes (generated by Hilt)
- Must keep `org.jaudiotagger.**` (all of it)
- Must `-dontwarn java.awt.**`, `javax.imageio.**`, `javax.swing.**` (jAudiotagger desktop deps)
- R8 for debug is problematic (Hilt generated classes may be stripped)
