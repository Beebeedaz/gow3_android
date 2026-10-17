#!/usr/bin/env bash
# Build the launcher's own native libraries: the JNI code in app/src/main/jni.
#
# These are part of the APK, not of the game runtime: Gow3Present receives the Bionic game's frames,
# Gow3Prepare compiles the game's patches. They are built here, with the NDK, like everything else
# native in this repository (gpu/, cpu/, src/), and land in tools/android/bridges/<abi>/, which
# build.gradle.kts adds as a jniLibs source dir. Gradle's externalNativeBuild is deliberately not
# used: the .so are checked in, so the APK still assembles without an NDK installed.
#
# usage: build_jni_libs.sh [--abi arm64-v8a]
set -euo pipefail
cd -- "$(dirname -- "$0")/../.."

abi=arm64-v8a
[[ ${1:-} == --abi ]] && abi=$2

ndk=${GOW3_NDK:-}
if [[ -z $ndk || ! -f "$ndk/build/cmake/android.toolchain.cmake" ]]; then
    for candidate in \
        "${ANDROID_HOME:-}/ndk/29.0.14206865" \
        "${ANDROID_SDK_ROOT:-}/ndk/29.0.14206865" \
        "$HOME/Library/Android/sdk/ndk/29.0.14206865" \
        "$HOME/Android/Sdk/ndk/29.0.14206865"; do
        if [[ -f "$candidate/build/cmake/android.toolchain.cmake" ]]; then
            ndk=$candidate
            break
        fi
    done
fi
[[ -f $ndk/build/cmake/android.toolchain.cmake ]] || {
    echo "build_jni_libs: no NDK 29.0.14206865 found (set GOW3_NDK)" >&2
    exit 1
}

# The same API level the runtime is built for, so the app and the game agree on the platform.
api=${GOW3_ANDROID_API:-30}
bin=$ndk/toolchains/llvm/prebuilt/darwin-x86_64/bin
# C sources go through clang, C++ through clang++: clang++ would compile a .c as C++ (jni.h's
# NewStringUTF is not visible to C++ that way, among others).
clang=$bin/aarch64-linux-android$api-clang
clangxx=$bin/aarch64-linux-android$api-clang++
strip=$bin/llvm-strip
[[ -x $clang ]] || { echo "build_jni_libs: no $clang" >&2; exit 1; }

jni=android-launcher/app/src/main/jni
out=tools/android/bridges/$abi
# gow3present.c includes the wire format by name, the way it is built today.
includes=(-I "$jni/gow3present" -I "$jni/gow3prepare" -I "$jni/gow3link" -I tools/android/present)
mkdir -p "$out"
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT

# Each library: a name and its sources. C++ ones carry a -std the runtime also uses.
build() {
    local name=$1
    shift
    local sources=()
    local std=
    local compiler=$clang
    for source in "$@"; do
        sources+=("$jni/$source")
        if [[ $source == *.cpp ]]; then
            std=c++17
            compiler=$clangxx
        fi
    done
    # -llog: both call __android_log_print (platform liblog). -landroid: gow3present uses
    # AHardwareBuffer_* (libandroid). Without either, dlopen fails at load time with
    # "cannot locate symbol", before any of our code runs.
    local flags=(-O2 -fPIC -fvisibility=hidden -ffunction-sections -fdata-sections
                 -Wl,--gc-sections -Wl,-z,max-page-size=16384 -llog -landroid)
    if [[ -n $std ]]; then
        # Statically linked libc++: the launcher ships no libc++_shared.so, and pulling one in just
        # for these two small libraries would add a second copy of the runtime to the APK.
        flags+=(-static-libstdc++)
    fi
    [[ -n $std ]] && flags+=(-std=$std)
    echo "build_jni_libs: $name (${#sources[@]} sources)"
    "$compiler" "${flags[@]}" "${includes[@]}" "${sources[@]}" -shared -o "$tmp/$name.so"
    "$strip" --strip-unneeded -o "$out/lib$name.so" "$tmp/$name.so"
}

build gow3present gow3present/gow3present.c
build gow3prepare gow3prepare/gow3_prepare.cpp gow3link/gow3_link.cpp gow3prepare/gow3_prepare_jni.cpp

ls -la "$out/libgow3present.so" "$out/libgow3prepare.so"
echo "build_jni_libs: the APK picks these up from tools/android/bridges ($abi)"
