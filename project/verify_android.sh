#!/usr/bin/env bash
# Keep the emulator alive until screenshots are copied, including on test failure.
gradle --no-daemon connectedDebugAndroidTest
result=$?
mkdir -p ../screenshots
for name in home person accounts edit; do "$ANDROID_HOME/platform-tools/adb" pull "/sdcard/Download/hesabyar-$name.png" "../screenshots/$name.png" || true; done
exit "$result"
