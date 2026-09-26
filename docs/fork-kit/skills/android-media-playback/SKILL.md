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
4. For background playback, verify service registration, `MediaSession`/controller connection, notification channel, foreground service permissions/type, task-removal behavior, and Android version requirements.
5. For `AudioProcessor`, keep `ByteBuffer` positions/limits correct, validate supported encodings/channel counts, avoid blocking/allocation-heavy work in the audio callback, and reset/flush internal state correctly.
6. Test normal playback, pause/resume, track end/repeat/shuffle, seek boundaries, effect switching, interruption, denied notification permission, and cleanup as applicable.
7. Run `./gradlew :app:assembleDebug` plus meaningful unit/device checks for the affected playback behavior.

## CirclePlayer-specific facts

- Media3 dependencies are pinned to `1.4.1` in `app/build.gradle.kts`.
- `service/PlaybackService.kt` owns the authoritative ExoPlayer and MediaSession.
- `MainActivity` connects with a `MediaController`; `MusicPlayerApp` issues commands to that same service player.
- `audio/EffectsRenderersFactory.kt` injects shared `EffectsManager` processors into the service player's `DefaultAudioSink`.
- Four processors in `audio/` support PCM 16-bit: Wow & Flutter, Volume Detonation, Chorus and Vintage Noise.
- Effects are always installed on the player; `EffectsManager.effectsEnabled` gates processing without replacing the MediaController.
- The selected track list comes from MediaStore as `content://` URIs; preserve URI-based playback.

## Correctness checks

- Do not release a player owned by another component.
- Avoid UI work from player/audio callback threads.
- Ensure processors preserve input transparently while disabled and clear delay/history buffers when flushing/resetting.
- Validate seek positions against duration and handle unknown duration.
- Review headset/Bluetooth and background/foreground behavior when relevant.
