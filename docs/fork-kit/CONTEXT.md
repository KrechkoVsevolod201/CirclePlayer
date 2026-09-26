# CirclePlayer project context

CirclePlayer is a local-music Android player styled after the iPod Classic. The UI is built with Kotlin and Jetpack Compose; playback uses AndroidX Media3.

## Stack

- Single Gradle module: `:app`.
- Android Gradle Plugin 8.13.0, Kotlin 2.0.21, Gradle 8.13.
- Compose BOM 2024.09.00, Material 3, Media3 1.4.1.
- `minSdk 25`, `targetSdk 35`, Java/Kotlin target 11.
- Package and application ID: `com.example.circleplayer`.

## Main source files

- `MainActivity.kt` connects to playback, requests permissions, launches SAF pickers and hosts app-level theme/language state.
- `PlayerComposables.kt` contains player screens, Click Wheel gestures, settings, theme/scale editors, progress and vinyl animations.
- `MusicRepository.kt` reads local tracks and folders from MediaStore.
- `AudioTrack.kt` contains track/folder models.
- `service/PlaybackService.kt` owns the authoritative ExoPlayer and MediaSession for app and Android media controls.
- `audio/` contains the Media3 renderer factory, effect manager and realtime PCM processors.
- `ui/theme/` defines player palettes, Material theme and JSON theme presets.

## Important behavior

- The service owns the single ExoPlayer; the Activity connects using a `MediaController`.
- Track browsing uses MediaStore URIs. Folder filtering currently uses `MediaStore.Audio.Media.DATA`; SAF providers may not expose a filesystem path.
- Click Wheel maps Menu, Play/Pause, confirm and track skip to hardware-style regions; vibration, click audio and user-provided click sounds are configurable.
- The display supports portrait and landscape layouts. Automatic fit remains available; optional per-element scale factors are stored independently for each orientation.
- Each theme preset stores light and dark `PlayerPalette` values. Built-in and user presets are persisted as JSON, and user presets can be imported/exported through SAF.
- Vinyl spin speed can be adjusted directly or synchronized proportionally to BPM metadata; tracks without a BPM tag use 120 BPM.
- UI language is stored in app preferences and can be switched between Russian and English.
- Four audio processors handle PCM 16-bit data on the audio thread. Avoid blocking work and unnecessary allocations in `queueInput`.

## Verification

```bash
./gradlew :app:assembleDebug
./gradlew :app:assembleRelease
./gradlew test
./gradlew connectedAndroidTest
```

The current release build is configured with the debug signing config. Fork maintainers should configure their own release signing before publishing under their identity.
