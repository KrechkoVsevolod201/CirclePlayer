---
name: jetpack-compose-mobile-ui
description: Use when implementing or changing Jetpack Compose screens, gestures, state, navigation/back handling, theming, accessibility, or responsive Android layouts and previews.
---

# Jetpack Compose mobile UI

Use this skill for Compose UI and interaction work. Prioritize predictable state flow, lifecycle-aware effects, accessibility, and layouts that work across orientations and sizes.

## Implementation guidance

1. Read the surrounding Composables and theme before changing layout. Reuse the project's spacing, typography, palette, shapes, and callback conventions.
2. Keep UI state observable. Hoist state when multiple components need it; pass values and event callbacks to reusable UI instead of hiding application behavior in presentation components.
3. Key `LaunchedEffect`, `DisposableEffect`, and `pointerInput` to the values that define their lifetime. Cancel work through Compose scopes/effects; do not create unmanaged coroutine scopes inside gesture callbacks.
4. Use stable keys in lazy lists. Cover empty/loading/error states, long text, touch target sizes, semantics/content descriptions, and system back behavior.
5. Validate portrait and landscape layouts, narrow and large screens, light/dark palettes, and system bars/insets where relevant. Keep important controls reachable and avoid fixed-size assumptions when constraints can be used.
6. Use Material 3 controls and project palette tokens where suitable. Do not introduce another design system or replace the existing visual language without a product request.
7. Verify UI changes with `./gradlew :app:assembleDebug`; use Compose/UI instrumentation or a device/emulator for gesture, orientation, accessibility, and visual behavior when available.

## CirclePlayer-specific facts

- Most app UI and navigation are in `app/src/main/java/com/example/circleplayer/PlayerComposables.kt`.
- `MusicPlayerApp` currently owns screen-level state; `NowPlayingScreen`, `ClickWheel`, `TrackRow`, `EffectsMenu`, `SettingsScreen`, and `VinylScreensaver` make up the main UI.
- Orientation-specific player composition and rotation logic lives in `NowPlayingScreen`/`UprightContainer`. Preserve the physical click-wheel orientation and keep the landscape display readable.
- Use `LocalPlayerPalette` from `ui/theme/Theme.kt` for custom player surfaces; theme selection is passed down from `MainActivity` and persisted in `SharedPreferences`.
- Back handling dismisses screens/overlays in order (vinyl, settings, effects, list) before allowing system navigation.
- Wheel and vinyl gestures are core interactions; keep tactile response, selection, playback seeking, and animation state coherent.

## Review checklist

- Is state owned at the right level and effects disposed when their key changes?
- Are all controls accessible, clickable at a practical size, and labeled for assistive technology?
- Does content remain usable with long track names, an empty library, orientation changes, and different screen dimensions?
- Are gesture coroutines lifecycle-bound and pointer handlers keyed correctly?
- Does the UI remain consistent in both themes and include appropriate visible feedback?
