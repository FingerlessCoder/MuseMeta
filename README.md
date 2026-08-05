# MuseMeta — MyMusicPlayer

A modern, Material3-powered Android music player built with Jetpack Compose, Koin DI, Room, and Media3 ExoPlayer.

![Android](https://img.shields.io/badge/Platform-Android%2010+-blue)
![Kotlin](https://img.shields.io/badge/Kotlin-2.2-blue)
![Compose](https://img.shields.io/badge/Compose-BOM%202024.12-green)
![Room](https://img.shields.io/badge/Room-2.7.1-red)

## Features

- **Full library management** — scan MediaStore + filesystem, track metadata, album art extraction, artist parsing
- **Rich player UI** — Material3 dark theme, now-playing screen with full controls, mini-player bar across all screens
- **Smart playback modes** — Shuffle, List (sequential), Single; mode persists across app restarts
- **Favorites & playlists** — favourite toggle via notification, create/manage playlists
- **Search** — search tracks by title, artist, album
- **Settings** — customise preferences via DataStore Preferences
- **Background playback** — Media3 MediaSessionService with foreground service
- **Sleep timer** — built-in sleep timer dialog
- **Equalizer** — graphic EQ panel with preset support
- **Alphabet index bar** — quick navigation within lists

## Architecture

```
com.mymusicplayer
├── MusicPlayerApp.kt           Application entry, Koin setup
├── MainActivity.kt             Permissions + auto-scan trigger
├── MainScreen.kt               Scaffold + NavGraph
├── data/
│   ├── audio/                  MusicPlayerController (ExoPlayer), AudioFocusManager
│   ├── db/                     Room DB: 7 entities, 4 DAOs, TypeConverters
│   ├── preferences/            SettingsDataStore
│   └── scanner/                MediaStoreScanner, FileSystemScanner, MetadataParser
├── di/                         Koin modules (AppModule + DatabaseModule)
├── domain/
│   ├── model/                  Track, Album, Artist, Playlist, EqualizerPreset
│   ├── repository/             MusicRepository interface + impl
│   └── usecase/                SmartSortUseCase
├── service/                    MusicService (MediaSession), ScanService (foreground)
└── ui/
    ├── components/             Reusable composables (MiniPlayerBar, EqualizerView, etc.)
    ├── navigation/             Routes.kt + NavGraph.kt
    ├── screens/                10 screen types (home, player, search, settings, scan, etc.)
    └── theme/                  Material3 dark theme
```

## Tech Stack

| Component | Version |
|---|---|
| Kotlin | 2.2.10 |
| Jetpack Compose BOM | 2024.12.01 |
| Material3 | — |
| Navigation Compose | 2.8.5 |
| Koin | 4.0.3 |
| Room | 2.7.1 (KSP) |
| Media3 | 1.5.1 |
| DataStore Preferences | 1.1.2 |
| Coil | 2.7.0 |
| jAudiotagger | 3.0.1 |

## Build & Run

```bash
# Build debug APK
./gradlew assembleDebug

# Install on device
adb install app/build/outputs/apk/debug/app-debug.apk

# Run tests (JVM unit tests only)
./gradlew test
```

### Debug Performance Note

Debug builds are **visibly less smooth** than release due to Compose debug checks and ART not optimising. Verify UI performance with a release build or Android Studio profile mode — never use debug Run as the smoothness acceptance test.

## Testing

- `MetadataParserTest.kt` — pure unit tests (JUnit 5 + MockK)
- `TrackDaoTest.kt` — Room in-memory DB, instrumentation test (`connectedAndroidTest`)
- `PlayerViewModelQueueTest.kt` — known pre-existing failure, do not treat as regression

## Permissions

| Permission | Purpose |
|---|---|
| `READ_MEDIA_AUDIO` (API 33+) | Read audio files |
| `READ_EXTERNAL_STORAGE` (API 10-32) | Legacy audio access |
| `FOREGROUND_SERVICE_DATA_SYNC` (API 34+) | Background scan |
| `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | Background music playback |

No `MANAGE_EXTERNAL_STORAGE` is used.

## License

This project is licensed under the AGENTS.md terms.