# Store release checklist

Version **1.6 (7)** is prepared in Gradle. The package ID remains
`com.example.circleplayer` as requested; verify that it is available in each
store before creating the permanent app records.

## Signing and artifacts

Never publish an APK/AAB signed with Android's debug key. Create a private
upload/release keystore and keep both it and its passwords outside Git:

1. Create a long-lived RSA key. `keytool` prompts for passwords interactively;
   keep them in a password manager and make an offline backup of the keystore:

   ```bash
   keytool -genkeypair -v \
     -keystore "$HOME/.android/circleplayer-release.jks" \
     -alias circleplayer -keyalg RSA -keysize 4096 -validity 10000
   ```

2. Copy `keystore.properties.example` to the ignored `keystore.properties`.
3. Set `storeFile`, `storePassword`, `keyAlias`, and `keyPassword`, or provide
   the equivalent `RELEASE_STORE_FILE`, `RELEASE_STORE_PASSWORD`,
   `RELEASE_KEY_ALIAS`, and `RELEASE_KEY_PASSWORD` environment variables.
4. Build `./gradlew :app:bundleRelease :app:assembleRelease`.

The published v1.5 APK is signed by the local Android debug key. Switching to a
new release key means Android will require users with v1.5 installed to
uninstall it before installing this v1.6 build.

Without all four signing values, Gradle intentionally creates unsigned release
artifacts. Use the same signing identity for APK distribution across stores so
those installations can update one another. Google Play App Signing and
RuStore's AAB signing have separate console setup steps; retain the upload key
and the store signing certificates securely.

## Google Play

- The Play policy from 31 August 2026 requires new apps and updates to target
  API 36 or higher; Gradle now targets API 36.
- Upload the signed AAB from `app/build/outputs/bundle/release/`.
- Complete the Play Console app record, content rating, target-audience,
  advertising, and Data safety forms. The app has no ads, analytics, account,
  `INTERNET` permission, or developer-side data collection; it does request
  local audio access, notifications, foreground playback, and vibration. The
  merged manifest includes `ACCESS_NETWORK_STATE` from a dependency but no
  Internet access or upload endpoint.
- Add the privacy policy URL after the repository is pushed:
  `https://github.com/KrechkoVsevolod201/CirclePlayer/blob/master/PRIVACY_POLICY.md`.
- Prepare a 512x512 opaque store icon and a 1024x500 feature graphic. The
  existing phone screenshots are in `screenshots/`; select at least two for
  the Play listing and check the current Console size/aspect-ratio limits.
- Fill English and Russian listing text from `fastlane/metadata/android/`.
- Complete any closed-test requirement shown for the Play developer account.

The current Play submission target requirement is based on the official policy:
https://support.google.com/googleplay/android-developer/answer/11926878

## RuStore

- Upload a signed APK or configure the AAB signing certificate in RuStore
  Console before uploading the AAB.
- Upload at least three phone screenshots. The existing portrait screenshots
  are 1080x2400 PNG files (under 3 MB); landscape screenshots are also available.
- Prepare a 512x512 PNG/JPG icon with an opaque background, app name, category,
  age rating, up-to-80-character short description, full description, and
  version notes.
- Provide at least one developer contact (email, VK group, or website) and
  declare the app's requested permissions/data access in the console.
- When using AAB, upload the signing certificates separately. Keep package,
  versionCode, and certificate consistent with the app's previous version.

Official upload requirements:
https://www.rustore.ru/help/en/developers/publishing-and-verifying-apps/app-publication

## F-Droid

- Source is licensed under MIT and contains localized listing text.
- Submit `fdroid/com.example.circleplayer.yml` to the `metadata/` directory of
  the official `fdroiddata` repository in a packaging merge request.
- The metadata draft must point to the full Git commit hash of the v1.6 source
  tag. F-Droid builds and signs its own APK; its signature is separate from
  Google Play/RuStore signing.
- Verify the Gradle build on F-Droid's build server and resolve any dependency
  or reproducibility findings reported during review.

Metadata reference:
https://f-droid.org/docs/Build_Metadata_Reference

## Store media

Existing screenshots:

- Portrait: `screenshots/01_player_dark.png` through
  `screenshots/06_screensaver.png` (1080x2400).
- Landscape: `screenshots/07_landscape_player.png` and
  `screenshots/08_landscape_list.png` (2400x1080).
- App launcher artwork source: `app/src/main/res/drawable/ic_launcher_foreground.xml`.

The 512x512 store icon and 1024x500 Google Play feature graphic still need to
be exported as store-ready PNG/JPG assets from the approved artwork.
