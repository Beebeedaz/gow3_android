#!/usr/bin/env bash
# Stage the debug runtime payload for the Gradle debug source set
# (android-launcher/app/build.gradle.kts: out/android-debug/assets).
#
# Bionic only: the payload is the Bionic runtime tree (gow3-probe, its libraries, run-bionic.sh and
# the config files), with no glibc rootfs behind it. Called by build_bionic_debug_apk.sh, and by the
# Gradle preDebugBuild hook (Android Studio), which does not pass GOW3_BIONIC - hence the
# auto-detection on the built runtime.
#
# Idempotent: rebuilds the payload only when an input is missing or changed.
set -euo pipefail
script_dir=$(cd -- "$(dirname -- "$0")" && pwd)
cd -- "$script_dir/../../.."

if [[ ${GOW3_BIONIC:-0} == 1 || -x out/bionic/bin/gow3-probe ]]; then
    exec bash "$script_dir/stage_bionic_runtime.sh"
fi

echo "stage_debug_runtime: no Bionic runtime at out/bionic/bin/gow3-probe." >&2
echo "  Build it first (tools/android/build_bionic.sh), or the Bionic debug APK" >&2
echo "  cannot be assembled." >&2
exit 1
