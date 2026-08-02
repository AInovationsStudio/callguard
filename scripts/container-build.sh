#!/usr/bin/env bash
# Build the CallGuard debug APK entirely inside the reproducible container.
# No host Java, Gradle, or Android SDK is required.
set -euo pipefail

cd "$(dirname "$0")/.."
ROOT="$PWD"

IMAGE_TAG="callguard-android:dev"
GRADLE_VOLUME="callguard-gradle-cache"
ENGINE="${CONTAINER_ENGINE:-podman}"
if ! command -v "$ENGINE" >/dev/null 2>&1; then
    if command -v podman >/dev/null 2>&1; then ENGINE="podman"; \
    elif command -v docker >/dev/null 2>&1; then ENGINE="docker"; \
    else echo "error: no container engine (podman/docker) found" >&2; exit 127; fi
fi

if ! "$ENGINE" image inspect "$IMAGE_TAG" >/dev/null 2>&1; then
    echo "[container-build] building image $IMAGE_TAG (one-time)..."
    "$ENGINE" build -t "$IMAGE_TAG" -f Containerfile .
fi

mkdir -p app/build/outputs/apk/debug

"$ENGINE" run --rm \
    -v "$ROOT:/workspace:Z" \
    -v "${GRADLE_VOLUME}:/home/developer/.gradle:U,Z" \
    -e ANDROID_HOME=/android-sdk \
    -e ANDROID_SDK_ROOT=/android-sdk \
    -w /workspace \
    --userns=keep-id \
    --user developer \
    "$IMAGE_TAG" \
    ./gradlew --no-daemon --dependency-verification strict --stacktrace assembleDebug

APK="app/build/outputs/apk/debug/app-debug.apk"
if [[ ! -f "$APK" ]]; then
    echo "error: expected APK not found at $APK" >&2
    exit 1
fi
echo "[container-build] OK: $APK"
