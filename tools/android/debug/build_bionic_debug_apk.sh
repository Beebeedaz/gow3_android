#!/usr/bin/env bash
# Build the debug APK with the Bionic runtime payload (NDK/Bionic native path, no rootfs/X11).
# 与 tools/android/debug/build_debug_apk.sh 对应，但 runtime 是 Bionic 版：
#   - stage_bionic_runtime.sh 产出 out/android-debug/assets/runtime.payload
#   - 只打包 Bionic 自己的 JNI 库（libgow3prepare/libgow3present）与 Bionic runtime payload，
#     用 Bionic 的 validate（见下）。
set -euo pipefail
script_dir=$(cd -- "$(dirname -- "$0")" && pwd)
cd -- "$script_dir/../../.."

# The launcher's own JNI code（帧接收、补丁编译）由 Gradle externalNativeBuild 在
# assembleDebug 中直接从 app/src/main/cpp 产出，无需（也无法）在此先行构建 .so。
GOW3_BIONIC=1 bash tools/android/debug/stage_debug_runtime.sh

# Steam sign-in libraries（与 glibc 调试构建一致）。
[[ -f .local-deps/android/steam-libs/jars.txt ]] || bash tools/android/inputs/fetch_steam_libs.sh

GOW3_BIONIC=1 bash tools/android/apk/build_kotlin_launcher.sh

version=$(sed -n 's/.*versionName *= *"\([^"]*\)".*/\1/p' android-launcher/app/build.gradle.kts)
mkdir -p out/debug
apk_src=android-launcher/app/build/outputs/apk/debug/app-arm64-v8a-debug.apk
[[ -f $apk_src ]] || { echo "build_bionic_debug_apk: gradle produced no APK" >&2; exit 1; }
# Freshness：不再比较 mtime。JNI 与 assets 都由 Gradle 内容敏感的任务追踪
# （externalNativeBuild 源码、assets.srcDir 文件内容），up-to-date 的 APK 必然已包含
# 当前输入；真正的「包里 payload 是不是这份」由下方 python 摘要比对逐字节证明。
# 构建失败时 set -e 直接退出，不存在旧 APK 蒙混过关的路径。
payload=out/android-debug/assets/runtime.payload
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
