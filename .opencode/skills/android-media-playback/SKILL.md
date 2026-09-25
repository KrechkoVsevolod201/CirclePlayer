---
name: android-media-playback
description: Use when changing Android audio/video playback, Media3 or ExoPlayer, MediaSessionService, notifications, playback controls, audio focus, or PCM AudioProcessor/DSP code.
---

# Android Media3 playback and audio processing

Use this skill for Media3 player behavior, background media sessions, audio processing, and playback UI synchronization.

## Workflow

1. Trace player creation, ownership, listeners, release points, service connections, and state propagation before changing playback behavior. Identify one authoritative player for each playback flow.
2. Check the project's Media3 version and `@UnstableApi` use. Keep API calls compatible with the pinned version rather than copying examples from newer releases blindly.
3. Preserve playback state across track switches, effects changes, and lifecycle transitions where expected: media item, position, playWhenReady, repeat/shuffle, errors, and audio focus.
4. For background playback, verify service registration, `MediaSession`/controller connection, notification channel, foreground service permissions/type, task-removal behavior, and Android version requirements. A service declaration alone does not wire a UI player to that service.
5. For `AudioProcessor`, keep `ByteBuffer` positions/limits correct, validate supported encodings/channel counts, avoid blocking/allocation-heavy work in the audio callback, and reset/flush internal state correctly. Bound samples to the output format and consider malformed/partial frames.
6. Test normal playback, pause/resume, track end/repeat/shuffle, seek boundaries, effect switching, interruption, denied notification permission, and cleanup as applicable.
7. Run `./gradlew :app:assembleDebug` plus meaningful unit/device checks for the affected playback behavior.

## CirclePlayer-specific facts

- Media3 dependencies are pinned to `1.4.1` in `app/build.gradle.kts` (currently declared inline).
- `MainActivity` creates an ExoPlayer and gives it to `MusicPlayerApp`; that Compose tree changes/replaces the player when effects are toggled and manages player listeners and release.
- `service/PlaybackService.kt` creates its own ExoPlayer and MediaSession. It is declared in the manifest but is not currently connected to the Activity's player. Do not conflate those playback states.
- `audio/EffectsRenderersFactory.kt` injects `EffectsManager.getAudioProcessors()` into a `DefaultAudioSink`.
- Four processors are in `audio/`: `WowFlutterProcessor`, `VolumeDetonationProcessor`, `ChorusProcessor`, and `VintageNoiseProcessor`. They expect PCM 16-bit and expose volatile enable/parameter values.
- The selected track list comes from `MusicRepository` as `content://` MediaStore URIs; preserve URI-based playback.
- Manifest declares media playback foreground-service permissions and a notification permission requested on Android 13+.

## Safety and correctness checks

- Do not release a player still owned by another component; make ownership explicit before refactoring.
- Avoid doing UI work from the player/audio callback thread. Marshal player state to Compose through stable observable state/listeners.
- Ensure processors preserve input transparently while disabled and clear delay/history buffers when flushing/resetting.
- Validate seek positions against known duration and handle unknown duration without crashing.
- Review behavior with headphones/Bluetooth and background/foreground transitions when relevant.
