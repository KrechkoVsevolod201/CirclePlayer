# Architecture decisions

This document records the main boundaries a fork should preserve or deliberately replace.

## ADR 1 — One playback owner

`PlaybackService` owns ExoPlayer and MediaSession. `MainActivity` connects through `MediaController`; Compose sends commands to that controller. Android notifications, headset buttons and the app therefore control the same playback state. Do not create an Activity-owned second player for UI features.

## ADR 2 — Compose owns presentation state

`MusicPlayerApp` manages screen navigation and preferences, while screen-level composables receive state and callbacks. Orientation-specific layout and wheel interaction are in `PlayerComposables.kt`. Keep MediaStore calls off the main thread and keep long-running work inside keyed Compose effects or lifecycle-owned scopes.

## ADR 3 — MediaStore for tracks, SAF for user-selected files

MediaStore supplies local audio content URIs and album artwork. SAF is used for folder selection and import/export. Persistable grants should be retained for user-selected documents. Folder filtering still relies on `MediaStore.Audio.Media.DATA`, so a provider that does not expose raw paths may require a URI-based redesign.

## ADR 4 — Realtime effects are AudioProcessors

`EffectsRenderersFactory` installs the effect processors on the service player's `DefaultAudioSink`. A shared master gate enables/disables processing without replacing the player. Processors support PCM 16-bit and run on a realtime audio path: avoid blocking I/O, locks, and avoidable allocations there; preserve input buffer position/limit and flush history correctly.

## ADR 5 — PlayerPalette is the visual contract

The player-specific UI reads `LocalPlayerPalette`; Material 3 colors are derived from the same active palette. A theme preset contains both light and dark palettes. User presets are serialized as JSON in app preferences and imported/exported as JSON documents.

## ADR 6 — Automatic layout fit is the baseline

The root screen scales to the available safe area. Optional element multipliers are layered on top and saved separately for portrait and landscape. Disabling custom scaling restores the automatic baseline without deleting saved values.

## ADR 7 — App language is an app preference

Russian/English UI strings are selected through Compose's app-language state rather than relying solely on device locale. New interface text should be provided in both languages; track title/artist metadata stays untouched.

## Fork checklist

- Update `applicationId`, namespace, app label and release version as appropriate.
- Use a fork-owned release signing key; never commit private signing material.
- Keep the single-player MediaSession ownership model unless intentionally changing all notification/headset paths.
- Validate API 25+ permissions, orientation behavior, empty libraries, external SAF providers and background playback.
- Run the build and tests listed in [`CONTEXT.md`](CONTEXT.md), and update these decisions if the architecture changes.
