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

## Publish and verify

1. For a new stable release, increase `version.properties` on main and wait for CI to pass. Open **Actions → Android build and stable release → Run workflow**, select main, and enable **publish**.
2. Confirm the intended version is the latest stable release. Codes are derived from the version components, so `v0.1.0` and `v0.1.1` use 1000 and 1001. The APK filenames include their release tags.
3. Download both published APKs on a computer. Run `apksigner verify --verbose --print-certs` on each and confirm both have one signer with the permanent certificate's SHA-256 fingerprint. The publish job verifies the built APK's package, version and certificate, then compares the staged asset with the signed APK before publishing. The workflow stores no GitHub Actions artifacts. If publishing stops with a draft, inspect the draft and tag before retrying.
4. On the Android device, download and install the APK from the `v0.1.0` release. With the phone offline, open the app and check that it shows installed version 0.1.0 and handles a failed update check.
5. Reconnect and tap **Check for Updates**. Verify that it detects `v0.1.1`, displays download progress, and hands the APK to Android. Allow **Install unknown apps** for Android Image Editor when prompted, then approve the update. Leave v0.1.0 installed throughout.
6. Reopen the app. Verify installed version **0.1.1**, the **Updater test build** label, and the up-to-date message. Record the device model, Android version and result before starting editing features.

## Continue in ChatGPT

In a new regular ChatGPT conversation, connect GitHub and reference `cosmerearchives-creator/Android-Image-Editor`. Ask it to read this README and inspect main and the latest release before opening a pull request. Keep the application ID, release asset naming, version mapping, and permanent signer stable. Keep private signing material in the owner's backup and GitHub environment.
