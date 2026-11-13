#!/usr/bin/env bash
set -euo pipefail
umask 077
: "${KEYSTORE_B64:?Configure ANDROID_KEYSTORE_BASE64 in stable-release}"
: "${KEYSTORE_PASSWORD:?Configure ANDROID_KEYSTORE_PASSWORD in stable-release}"
: "${EXPECTED_CERT:?Configure ANDROID_SIGNING_CERT_SHA256 in stable-release}"

work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
printf '%s' "$KEYSTORE_B64" | base64 --decode > "$work/signing.p12"
unset KEYSTORE_B64

tools="$ANDROID_HOME/build-tools/35.0.0"
version="$(sed -n 's/^versionName=//p' version.properties)"
IFS=. read -r major minor patch <<< "$version"
code=$((major * 1000000 + minor * 1000 + patch))
apk="android-image-editor-v$version.apk"
"$tools/zipalign" -f -P 16 4 app/build/outputs/apk/release/app-release-unsigned.apk "$work/aligned.apk"
"$tools/apksigner" sign --ks "$work/signing.p12" --ks-type PKCS12 --ks-key-alias android-image-editor \
  --ks-pass env:KEYSTORE_PASSWORD --out "$apk" "$work/aligned.apk"
unset KEYSTORE_PASSWORD

verification="$("$tools/apksigner" verify --verbose --print-certs "$apk")"
printf '%s\n' "$verification"
actual="$(sed -n 's/^Signer #1 certificate SHA-256 digest: //p' <<< "$verification")"
expected="${EXPECTED_CERT//:/}"
if [[ ! "$actual" =~ ^[[:xdigit:]]{64}$ || "${actual,,}" != "${expected,,}" ]] ||
   ! grep -Fxq 'Number of signers: 1' <<< "$verification"; then
  echo "::error::The APK signer does not match the permanent release certificate."
  exit 1
fi
metadata="$("$tools/aapt" dump badging "$apk")"
grep -Fq "package: name='com.cosmerearchives.androidimageeditor' versionCode='$code' versionName='$version'" <<< "$metadata"
sha256sum "$apk"
