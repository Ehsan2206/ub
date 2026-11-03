#!/usr/bin/env bash
# Keep the emulator alive until screenshots are copied, including on test failure.
gradle --no-daemon connectedDebugAndroidTest
result=$?
"$ANDROID_HOME/platform-tools/adb" pull /sdcard/Android/data/ir.hesabyar.finance/files ../screenshots || true
exit "$result"
