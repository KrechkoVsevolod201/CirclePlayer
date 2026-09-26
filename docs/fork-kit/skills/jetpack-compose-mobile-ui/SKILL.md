---
name: jetpack-compose-mobile-ui
description: Use when implementing or changing Jetpack Compose screens, gestures, state, navigation/back handling, theming, accessibility, or responsive Android layouts and previews.
---

# Jetpack Compose mobile UI

Use this skill for Compose UI and interaction work. Prioritize predictable state flow, lifecycle-aware effects, accessibility, and layouts that work across orientations and sizes.

## Implementation guidance

1. Read the surrounding Composables and theme before changing layout. Reuse project spacing, typography, palette, shapes and callback conventions.
2. Keep UI state observable. Hoist state when multiple components need it; pass values and event callbacks to reusable UI instead of hiding application behavior in presentation components.
3. Key `LaunchedEffect`, `DisposableEffect`, and `pointerInput` to the values that define their lifetime. Cancel work through Compose scopes/effects; do not create unmanaged coroutine scopes inside gesture callbacks.
4. Use stable keys in lazy lists. Cover empty/loading/error states, long text, touch targets, semantics/content descriptions and system back behavior.
5. Validate portrait and landscape layouts, narrow and large screens, light/dark palettes and system bars/insets where relevant.
6. Use Material 3 controls and project palette tokens where suitable. Do not introduce another design system without a product request.
7. Verify UI changes with `./gradlew :app:assembleDebug`; use UI instrumentation or a device/emulator for gesture, orientation and visual behavior when available.

## CirclePlayer-specific facts

- Most app UI and navigation are in `app/src/main/java/com/example/circleplayer/PlayerComposables.kt`.
- `MusicPlayerApp` owns screen-level state. `NowPlayingScreen`, `ClickWheel`, `TrackRow`, `EffectsMenu`, `SettingsScreen` and `VinylScreensaver` make up the main UI.
- Orientation-specific composition and rotation logic lives in `NowPlayingScreen`/`UprightContainer`.
- Use `LocalPlayerPalette` from `ui/theme/Theme.kt` for player surfaces.
- Back handling dismisses overlays/settings before system navigation.
- Wheel and vinyl gestures are core interactions; keep tactile response, selection, seeking and animation coherent.
