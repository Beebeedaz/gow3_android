#!/usr/bin/env bash
set -euo pipefail

cd -- "$(dirname -- "$0")/../../.."
root=$PWD
project=$root/android-launcher
apk=$project/app/build/outputs/apk/debug/app-arm64-v8a-debug.apk
aapt=${GOW3_AAPT:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}/build-tools/35.0.0/aapt}

[[ -x $project/gradlew ]] || { echo "build_kotlin_launcher: missing Gradle wrapper" >&2; exit 1; }
[[ -f $root/out/android-debug/assets/runtime.payload ]] || {
    echo "build_kotlin_launcher: missing debug runtime payload; build the runtime first" >&2
    exit 1
}
[[ -f $root/tools/android/bridges/arm64-v8a/libfxdisplay.so ]] || {
    echo "build_kotlin_launcher: missing libfxdisplay.so" >&2
    exit 1
}
[[ -f $root/tools/android/bridges/arm64-v8a/libfxio.so ]] || {
    echo "build_kotlin_launcher: missing libfxio.so" >&2
    exit 1
}

# The jniLibs (tools/android/bridges) are build outputs, so Gradle cannot tell they changed by
# looking at the tree - they are not sources it tracks. Merge them explicitly every time, or a
# rebuilt .so would never reach the APK.
# --rerun-tasks on the packaging chain: assembleDebug is otherwise UP-TO-DATE and leaves the APK
# file older than the payload it should contain, which the Bionic build's freshness check catches.
"$project/gradlew" -p "$project" :app:mergeDebugJniLibFolders :app:assembleDebug --rerun-tasks
[[ -f $apk ]] || { echo "build_kotlin_launcher: APK was not generated" >&2; exit 1; }
if [[ ${GOW3_BIONIC:-0} == 1 ]]; then
    # Bionic：glibc bridge（libfxdisplay/libfxio）与 glibc runtime-manifest 校验不适用；
    # APK 校验由 build_bionic_debug_apk.sh 的 Bionic 分支完成。
    echo "Built $apk (Bionic)"
    exit 0
fi
[[ -x $aapt ]] || { echo "build_kotlin_launcher: missing aapt: $aapt" >&2; exit 1; }
python3 tools/android/apk/validate_release_apk.py "$apk" \
    --aapt "$aapt" --allow-debuggable \
    --manifest tools/android/debug/runtime-manifest.json
echo "Built $apk"
