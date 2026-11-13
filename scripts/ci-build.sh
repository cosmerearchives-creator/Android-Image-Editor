#!/usr/bin/env bash
set -euo pipefail

export JAVA_HOME="${JAVA_HOME_17_X64:-$JAVA_HOME}"
export PATH="$JAVA_HOME/bin:$PATH"

work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
curl -fsSL --retry 3 https://services.gradle.org/distributions/gradle-8.9-bin.zip -o "$work/gradle.zip"
echo "d725d707bfabd4dfdc958c624003b3c80accc03f7037b5122c4b1d0ef15cecab  $work/gradle.zip" | sha256sum -c -
unzip -q "$work/gradle.zip" -d "$work"
javac -d "$work/tests" app/src/main/java/com/cosmerearchives/androidimageeditor/StableVersion.java tests/StableVersionTest.java
java -cp "$work/tests" com.cosmerearchives.androidimageeditor.StableVersionTest
if [[ ! -f "$ANDROID_HOME/platforms/android-35/android.jar" ||
      ! -x "$ANDROID_HOME/build-tools/35.0.0/apksigner" ]]; then
  echo "::error::Install Android SDK Platform 35 and Build Tools 35.0.0."
  exit 1
fi
"$work/gradle-8.9/bin/gradle" --no-daemon --console=plain --stacktrace :app:assembleRelease
