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
| compileSdk/targetSdk | 35 |
| minSdk | 29 |
| JDK | 26 (only JDK available on Windows) |

**Key constraint**: JDK 26 requires AGP 9.x + Gradle 9.x + Kotlin 2.2.x. Downgrading these is NOT possible without a different JDK.

## CRITICAL: Why Hilt was replaced with Koin

Hilt (`@HiltAndroidApp`, `@AndroidEntryPoint`) requires either KSP or KAPT to generate base classes. Neither works with this toolchain:

- **KSP**: Does NOT include `KspHiltAndroidAppProcessor` in Hilt 2.59.2 → `Hilt_MusicPlayerApp.java` never generated
- **KAPT**: `kotlin-kapt` plugin conflicts with AGP 9.x built-in Kotlin

**Solution**: Switched to **Koin** (pure Kotlin DI, no annotation processors). This avoids all annotation processing issues.

## Architecture

```
com.mymusicplayer
├── MusicPlayerApp.kt           startKoin { } entry point
├── MainActivity.kt             single-activity, no DI annotations
├── MainScreen.kt               Scaffold + bottom nav + NavHost
├── data/
│   ├── audio/                  MusicPlayerController, AudioFocusManager
│   ├── db/                     Room: AppDatabase, DAOs, Entities
│   ├── preferences/            SettingsDataStore (DataStore)
│   └── scanner/                MediaStoreScanner, MetadataParser, ScanRepository
├── di/
│   ├── AppModule.kt            Koin module (repos, VMs, factories)
│   └── DatabaseModule.kt       Koin module (Room DB + DAOs)
├── domain/
│   ├── model/                  Domain models (Track, Album, Artist, Playlist, etc.)
│   ├── repository/             MusicRepository interface + impl
│   └── usecase/                SmartSortUseCase
├── service/
│   ├── MusicService.kt         Media3 media playback service
│   └── ScanService.kt         Foreground service for media scan (KoinComponent)
└── ui/
    ├── components/             EqualizerView, SleepTimerDialog
    ├── navigation/             BottomNavBar, NavGraph, Routes
    ├── screens/
    │   ├── albums/             AlbumList, AlbumDetail + VM (koinViewModel)
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
- `gradle/libs.versions.toml` — Version catalog
- `app/src/main/AndroidManifest.xml` — `.MusicPlayerApp` entry
- `app/src/main/java/com/mymusicplayer/MusicPlayerApp.kt` — Application with Koin init
- `app/src/main/java/com/mymusicplayer/di/AppModule.kt` — All Koin module definitions
- `app/src/main/java/com/mymusicplayer/di/DatabaseModule.kt` — Room Koin module
- `app/proguard-rules.pro` — ProGuard rules (jAudiotagger, Room entities)
- `build.gradle.kts` — Root build (plugin declarations only)
- `local.properties` — SDK path
- `.sisyphus/plans/android-music-player.md` — Build debugging history

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
dexdump /tmp/c.dex | grep "Class descriptor" | grep -i "musicplayer"
```

## Dex Splitting Note

With AGP 9.x and `minSdk = 29`, D8 may produce multiple dex files even with `multiDexEnabled = false`. Total method count (~35K) is well under 64K limit. The Honor device (`HRY-LX1T`) classloader may fail to find classes in secondary dex files.

## Dependency Highlights

- **UI**: Jetpack Compose + Material3 + Navigation Compose
- **DI**: Koin 4.0.3 (no annotation processors)
- **Database**: Room 2.7.1 (via KSP)
- **Media**: Media3 ExoPlayer + Session
- **Audio metadata**: jAudiotagger 3.0.1
- **Image loading**: Coil
- **Navigation**: Compose Navigation with bottom nav (4 tabs)

## ProGuard Notes

- `-keep class org.jaudiotagger.** { *; }`
- `-dontwarn java.awt.**`, `javax.imageio.**`, `javax.swing.**` (jAudiotagger)
- R8 debug minification avoided (kept `isMinifyEnabled = false` for debug)
