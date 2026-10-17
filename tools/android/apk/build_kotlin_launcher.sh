#!/usr/bin/env bash
# Assemble the debug APK with the Gradle launcher.
#
# Bionic only: the launcher's JNI libraries come from tools/android/build_jni_libs.sh (run by the
# caller), the runtime payload from stage_bionic_runtime.sh. The glibc/proot runtime, its rootfs and
# the fexdroid X11 bridges are gone, so this no longer validates against a glibc runtime manifest -
# build_bionic_debug_apk.sh checks the Bionic payload and the JNI bridges instead.
set -euo pipefail

cd -- "$(dirname -- "$0")/../../.."
root=$PWD
project=$root/android-launcher
apk=$project/app/build/outputs/apk/debug/app-arm64-v8a-debug.apk

[[ -x $project/gradlew ]] || { echo "build_kotlin_launcher: missing Gradle wrapper" >&2; exit 1; }
[[ -f $root/out/android-debug/assets/runtime.payload ]] || {
    echo "build_kotlin_launcher: missing debug runtime payload; build the runtime first" >&2
    exit 1
}
# The JNI libraries are build outputs, so Gradle cannot tell they changed by looking at the tree -
# they are not sources it tracks. Merge them explicitly every time, or a rebuilt .so would never
# reach the APK. --rerun-tasks keeps assembleDebug from being UP-TO-DATE, which would leave the APK
# file older than the payload it should contain and trip the caller's freshness check.
"$project/gradlew" -p "$project" :app:mergeDebugJniLibFolders :app:assembleDebug --rerun-tasks
[[ -f $apk ]] || { echo "build_kotlin_launcher: APK was not generated" >&2; exit 1; }
echo "Built $apk (Bionic)"
