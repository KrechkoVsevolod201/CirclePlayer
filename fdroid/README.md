# F-Droid submission draft

`com.example.circleplayer.yml` is a draft of the package metadata to submit as
`metadata/com.example.circleplayer.yml` in the official `fdroiddata` project.
The build entry pins the v1.6 source commit and builds the unsigned release APK
from source; F-Droid applies its own signing key.

Before opening the packaging request, check the current fdroiddata schema and
run the release build on a clean checkout of the pinned commit. The package ID
is intentionally kept as `com.example.circleplayer` per the maintainer's
decision and must be available in the target catalog.
