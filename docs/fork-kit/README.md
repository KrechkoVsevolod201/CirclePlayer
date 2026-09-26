# CirclePlayer fork kit

This folder is the public, self-contained starting point for understanding and forking CirclePlayer.

- [Project context](CONTEXT.md) — stack, modules, runtime ownership and important platform constraints.
- [Architecture decisions](ARCHITECTURE_DECISIONS.md) — why the player, UI, storage, audio effects and themes are structured as they are.
- [Development skills](skills/README.md) — Android Kotlin, Media3 playback and Compose workflows.
- [Release notes](../releases/v1.6.md) — changes in the current release.

## Build a local APK

From the repository root:

```bash
./gradlew :app:assembleDebug
./gradlew :app:assembleRelease
./gradlew test
```

Before publishing a fork, choose a unique `applicationId`/namespace, increment the version, and configure a signing key that belongs to the fork owner. Keep signing credentials out of Git. Release signing uses ignored `keystore.properties` or `RELEASE_*` environment variables; without credentials, release artifacts are unsigned.

The skills in this folder are a portable copy for people browsing the project on GitHub. OpenCode's canonical project skills are in [`.opencode/skills`](../../.opencode/skills).
