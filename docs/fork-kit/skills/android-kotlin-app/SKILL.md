---
name: android-kotlin-app
description: Use when changing Android Kotlin apps, Gradle Kotlin DSL, AndroidManifest.xml, permissions, Activity lifecycle, storage, or app configuration; guides implementation and verification against the project's SDK and architecture.
---

# Android Kotlin application development

Use this skill for Android application features that involve platform APIs, app lifecycle, permissions, storage, manifest entries, or Gradle configuration.

## Workflow

1. Inspect the target module's `build.gradle.kts`, `gradle/libs.versions.toml`, manifest, neighboring Kotlin code, and any `context.md` or `AGENTS.md` instructions before editing.
2. Confirm `minSdk`, `targetSdk`, dependency versions, existing permission flow, and lifecycle owner. Implement against the versions already in use; do not upgrade dependencies as a side effect.
3. Handle API-level differences explicitly and request runtime permissions only when the feature needs them. Provide a useful denied/empty/error state rather than assuming permission is granted.
4. Keep Android framework work at the platform boundary and preserve existing app architecture. Avoid retaining Activity/context references beyond their lifecycle.
5. Add only the manifest permissions/components required for the feature. Check `exported`, service type, intent filters, and API-specific foreground-service requirements when touching components.
6. Follow existing Gradle Version Catalog conventions for dependency/plugin declarations. Do not commit machine-specific `local.properties`, build output, APKs, or IDE caches.
7. Run the narrowest meaningful checks, normally `./gradlew :app:assembleDebug` and relevant `./gradlew :app:test` or connected instrumented tests.

## CirclePlayer-specific facts

- Single module `:app`; Kotlin package/application ID `com.example.circleplayer`.
- `minSdk 25`, `targetSdk 35`, Compose UI, Media3 `1.4.1`.
- `MainActivity` owns permission prompts, folder-picker callback, preferences and connects to the service through a MediaController.
- `PlaybackService` owns the authoritative ExoPlayer and MediaSession used by Compose UI and Android media controls.
- `MusicRepository` queries MediaStore and currently depends on the `DATA` filesystem path when filtering a chosen folder. Do not assume SAF grants a usable raw path on all providers/devices.
- A subset of configuration changes is handled in `AndroidManifest.xml`; validate orientation changes on the actual flow.

## Quality checks

- Preserve saved state and resource cleanup across Activity recreation and process/lifecycle transitions.
- Consider API 25 through current target SDK behavior for permissions, notifications, storage and services.
- Check error, denied-permission, no-data and interrupted-operation paths.
- Review `git diff` to ensure only intended source/configuration files changed.
