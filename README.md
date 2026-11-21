# Android Image Editor

This native Java Android application contains the updater milestone. It supports Android 9 and later and uses the application ID `com.cosmerearchives.androidimageeditor`.

GitHub's public latest stable release endpoint is checked at launch and through **Check for Updates**. The app downloads the versioned APK, checks its byte count and SHA-256 digest, then checks its package name, version and signing certificate before passing it to Android's installer. Android asks the user to approve installation. The app uses the same package ID and signing certificate to update in place.

## Build and test

Use JDK 17 or 21, Gradle 8.9, and Android SDK 35. Open the repository in Android Studio or run `gradle :app:assembleRelease` for an unsigned APK. GitHub Actions builds and tests each main push and pull request. The version tests cover numeric ordering and reject prerelease tags. The GitHub build script validates the official Gradle distribution checksum.

## Configure the permanent signing identity

The original release key was provisioned in the maintainer’s Windows profile and is configured in GitHub’s `stable-release` environment. Recover the original key from the maintainer’s backup when moving to a new computer. The commands below document initial provisioning for a new signing identity. Generating a new identity for this application would break updates for existing installations.

```sh
keytool -genkeypair -storetype PKCS12 -keystore android-image-editor.p12 \
  -alias android-image-editor -keyalg RSA -keysize 3072 -validity 10000 \
  -dname 'CN=Android Image Editor'
keytool -list -v -keystore android-image-editor.p12 -alias android-image-editor
```

Back up the encrypted PKCS#12 file in two owner-controlled locations and save its password in the owner's password manager. Check that a backup opens and reports the same SHA-256 certificate fingerprint. GitHub Actions secrets cannot serve as a recoverable key backup. Keep the key and password out of commits, chat, logs, and release assets.

In the repository's **Settings → Environments**, create `stable-release` and restrict deployment to the `main` branch. Configure its two secrets and the certificate fingerprint variable. The key can be uploaded without putting its encoded contents in a command argument or file. Run these commands from a trusted Bash terminal and paste the password into the GitHub CLI's interactive prompt.

```sh
repo=cosmerearchives-creator/Android-Image-Editor
openssl base64 -A -in android-image-editor.p12 |
  gh secret set ANDROID_KEYSTORE_BASE64 --env stable-release --repo "$repo"
gh secret set ANDROID_KEYSTORE_PASSWORD --env stable-release --repo "$repo"
gh variable set ANDROID_SIGNING_CERT_SHA256 --env stable-release --repo "$repo" \
  --body '<public SHA-256 fingerprint from keytool>'
```

## v0.15.9 Adjustable fly-throughs and ZIP gallery import

- Quest-native Gallery Settings now control fly-through frequency (Off or every 12–120 seconds) and fly-through transparency (10–100%), alongside existing spacing/density/multiplier/radius controls.
- The sphere shader applies the selected interval and opacity to **moving** photos, leaving stationary photos unchanged. Settings are saved to the gallery preferences and picked up when reopening the sphere.
- The gallery menu includes **IMPORT ZIP** using Android's system document picker. PNG, JPEG, and WebP files inside the archive join the existing content-addressed working gallery and can be viewed by selecting **VIEW SPHERE**. ZIP entries do not write arbitrary paths; content is decoded with individual-image, total-data and count limits. Existing gallery items remain unless Reset Gallery was used first. Once the archive is imported, the app builds and opens the sphere automatically.
- ZIP import currently supports still images, not playable video files. Video looping remains a separate planned feature.
- CI checks verify ZIP importer wiring and adjustable shader inputs.

## v0.15.8 Gallery controls, reset, and photo fly-through

- Bulk Import now uses the Android system multi-select document picker rather than a custom AlertDialog hidden behind the OpenXR layer.
- Gallery Settings opens an in-headset VR control screen, with spacing, density, repeat multiplier, and radius adjustments. Changes apply when reopening the sphere.
- Reset Gallery is a two-press confirmation directly in the VR gallery. It clears imported working-library media and generated gallery atlases but preserves original device media, independent editor projects, exported files, and separately saved gallery snapshots.
- The photo sphere periodically animates one randomly selected existing gallery tile from the sphere toward and through the gallery center, using a stereo-consistent 3D plane intersection. Initial cadence is once per 24 seconds.
- Automated source checks cover picker wiring, native VR settings, reset confirmation, and fly-through shader behavior.

Video import/playback and looping are not implemented in this release and remain future work; photo fly-through is implemented first.

## v0.15.7 Randomized sphere fill

The sphere layout settings now include a 1×–8× gallery multiplier. It repeats imported photos **visually**, shuffles all displayed tiles into deterministic randomized positions, and uses the same positions for color and depth atlases. Original files and editable projects are not duplicated. To keep Quest texture work bounded without interrupting the user, the multiplier adds up to 96 visual tiles (never dropping original images); the old over-16-images warning and confirmation control are removed. A new source contract check runs during CI.

This increment does not yet add native video playback, looping clips, fly-through animation, or gallery reset; those remain separate functionality work.

## v0.15.6 Photo sphere — no themes

The sphere viewer now displays the user's gallery images around the observer against a subtle, stationary starfield. All theme selection and active museum/club/tiki/fire/dungeon/woods scenery rendering have been removed. Photo atlas gaps reveal the distant stars; opaque pixels remain unchanged by the background. Wall viewing, zoom, radius, layout, large-gallery warning, bulk conversion/export and saved-cylinder access remain available.

The old imported meshes and generated scene assets are legacy code/data and are no longer loaded by the sphere viewer. A later housekeeping change can delete them without involving the visual feature. GitHub Actions verifies the photo-only sphere source contract with `scripts/verify-photo-sphere.py`. Release publishing now uses the GitHub release ID to verify draft APK assets before making the release public. The v0.15.6 publishing workflow also distinguishes abandoned drafts from published releases and bypasses the v0.15.5 draft left by the failed publish attempts. The JNI regression checks also confirm that legacy environment draw methods remain available for compatibility but are not called by the photo sphere. No local PC or Desktop Commander is needed for building.

## Publish and verify

The release job runs only when **Run workflow → publish** is enabled on `main`, or when a push to `main` has a commit message beginning with `release:`. Ordinary pushes validate the build but intentionally skip publishing. The release job builds, signs, verifies, and publishes the APK for the version currently recorded in `version.properties`; do not re-run a build-only workflow expecting it to publish.

1. For a new stable release, increase `version.properties` on main and wait for CI to pass. Open **Actions → Android build and stable release → Run workflow**, select main, and enable **publish**.
2. Confirm the intended version is the latest stable release. Codes are derived from the version components, so `v0.1.0` and `v0.1.1` use 1000 and 1001. The APK filenames include their release tags.
3. Download both published APKs on a computer. Run `apksigner verify --verbose --print-certs` on each and confirm both have one signer with the permanent certificate's SHA-256 fingerprint. The publish job verifies the built APK's package, version and certificate, then compares the staged asset with the signed APK before publishing. The workflow stores no GitHub Actions artifacts. If publishing stops with a draft, inspect the draft and tag before retrying.
4. On the Android device, download and install the APK from the `v0.1.0` release. With the phone offline, open the app and check that it shows installed version 0.1.0 and handles a failed update check.
5. Reconnect and tap **Check for Updates**. Verify that it detects `v0.1.1`, displays download progress, and hands the APK to Android. Allow **Install unknown apps** for Android Image Editor when prompted, then approve the update. Leave v0.1.0 installed throughout.
6. Reopen the app. Verify installed version **0.1.1**, the **Updater test build** label, and the up-to-date message. Record the device model, Android version and result before starting editing features.

## Continue in ChatGPT

In a new regular ChatGPT conversation, connect GitHub and reference `cosmerearchives-creator/Android-Image-Editor`. Ask it to read this README and inspect main and the latest release before opening a pull request. Keep the application ID, release asset naming, version mapping, and permanent signer stable. Keep private signing material in the owner's backup and GitHub environment.
