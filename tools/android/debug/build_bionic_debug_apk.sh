#!/usr/bin/env bash
# Build the debug APK with the Bionic runtime payload (NDK/Bionic native path, no rootfs/X11).
# 与 tools/android/debug/build_debug_apk.sh 对应，但 runtime 是 Bionic 版：
#   - stage_bionic_runtime.sh 产出 out/android-debug/assets/runtime.payload
#   - 跳过 glibc 专属校验（libfxdisplay/libfxio、glibc runtime-manifest），
#     用 Bionic 的 validate（见下）。
set -euo pipefail
script_dir=$(cd -- "$(dirname -- "$0")" && pwd)
cd -- "$script_dir/../../.."

# The launcher's own JNI code (frame receiving, patch compilation). Built here rather than by Gradle
# so the .so are never stale; see tools/android/build_jni_libs.sh. They are build outputs, not
# sources, so Gradle does not track them as jniLibs inputs: stamp them afterwards to make a
# rebuild of the APK happen even when nothing else changed.
bash tools/android/build_jni_libs.sh
touch tools/android/bridges/"$(uname -m)"/*.so 2>/dev/null || true
touch tools/android/bridges/*/*.so 2>/dev/null || true

GOW3_BIONIC=1 bash tools/android/debug/stage_debug_runtime.sh

# Steam sign-in libraries（与 glibc 调试构建一致）。
[[ -f .local-deps/android/steam-libs/jars.txt ]] || bash tools/android/inputs/fetch_steam_libs.sh

GOW3_BIONIC=1 bash tools/android/apk/build_kotlin_launcher.sh

version=$(sed -n 's/.*versionName *= *"\([^"]*\)".*/\1/p' android-launcher/app/build.gradle.kts)
mkdir -p out/debug
apk_src=android-launcher/app/build/outputs/apk/debug/app-arm64-v8a-debug.apk
[[ -f $apk_src ]] || { echo "build_bionic_debug_apk: gradle produced no APK" >&2; exit 1; }
# Freshness: the packaged APK must be newer than the staged payload. Without this a previous
# build's APK survives a failed rebuild and still passes every check below, so the reported
# artifact does not come from the current tree.
payload=out/android-debug/assets/runtime.payload
if [[ ! $apk_src -nt $payload ]]; then
    echo "build_bionic_debug_apk: $apk_src is older than the staged payload; the build did not package the current runtime" >&2
    exit 1
fi
cp "$apk_src" "out/debug/God of War III-bionic-debug-$version.apk"
apk="out/debug/God of War III-bionic-debug-$version.apk"
sdk=${GOW3_ANDROID_SDK:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}
bt="$sdk/build-tools/${GOW3_BUILD_TOOLS:-35.0.0}"
"$bt/apksigner" verify "$apk"
"$bt/aapt" dump badging "$apk" | grep -F com.gow3.android.bionic >/dev/null
# Bionic：校验 payload 是 Bionic runtime 且 APK 带 libgow3present.so；glibc bridge 校验跳过。
# 摘要比对证明包里的 payload 就是本次 staged 的那一份，而不是上次构建残留的。
python3 - "$apk" "$payload" <<'PY'
import hashlib, pathlib, sys, zipfile
apk, payload = sys.argv[1], sys.argv[2]
with zipfile.ZipFile(apk) as z:
    names = set(z.namelist())
    for name in ('assets/runtime.payload', 'assets/runtime.sha256',
                 'lib/arm64-v8a/libgow3present.so', 'lib/arm64-v8a/libgow3prepare.so'):
        assert name in names, 'missing %s' % name
    packed = z.read('assets/runtime.payload')
    declared = z.read('assets/runtime.sha256').decode().split()[0]
current = pathlib.Path(payload).read_bytes()
assert hashlib.sha256(packed).hexdigest() == hashlib.sha256(current).hexdigest(), \
    'APK payload does not match the staged runtime payload'
assert declared == hashlib.sha256(current).hexdigest(), 'assets/runtime.sha256 disagrees with the payload'
print('Bionic debug APK payload/bridge present; payload matches the staged runtime (%d bytes)' % len(packed))
PY
echo "Bionic debug APK: out/debug/God of War III-bionic-debug-$version.apk"
