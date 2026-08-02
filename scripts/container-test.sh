#!/usr/bin/env bash
# Run the CallGuard deterministic local gate inside the reproducible container:
# formatting check, Android lint, unit tests, and the manifest permission audit.
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
    echo "[container-test] building image $IMAGE_TAG (one-time)..."
    "$ENGINE" build -t "$IMAGE_TAG" -f Containerfile .
fi

echo "[container-test] formatting (spotlessCheck)..."
"$ENGINE" run --rm \
    -v "$ROOT:/workspace:Z" \
    -v "${GRADLE_VOLUME}:/home/developer/.gradle:U,Z" \
    -e ANDROID_HOME=/android-sdk \
    -e ANDROID_SDK_ROOT=/android-sdk \
    -w /workspace \
    --userns=keep-id \
    --user developer \
    "$IMAGE_TAG" \
    ./gradlew --no-daemon --dependency-verification strict spotlessCheck

echo "[container-test] lint (lintDebug)..."
"$ENGINE" run --rm \
    -v "$ROOT:/workspace:Z" \
    -v "${GRADLE_VOLUME}:/home/developer/.gradle:U,Z" \
    -e ANDROID_HOME=/android-sdk \
    -e ANDROID_SDK_ROOT=/android-sdk \
    -w /workspace \
    --userns=keep-id \
    --user developer \
    "$IMAGE_TAG" \
    ./gradlew --no-daemon --dependency-verification strict lintDebug

echo "[container-test] unit tests (testDebugUnitTest)..."
"$ENGINE" run --rm \
    -v "$ROOT:/workspace:Z" \
    -v "${GRADLE_VOLUME}:/home/developer/.gradle:U,Z" \
    -e ANDROID_HOME=/android-sdk \
    -e ANDROID_SDK_ROOT=/android-sdk \
    -w /workspace \
    --userns=keep-id \
    --user developer \
    "$IMAGE_TAG" \
    ./gradlew --no-daemon --dependency-verification strict testDebugUnitTest

echo "[container-test] manifest audit..."
bash scripts/manifest-audit.sh

echo "[container-test] OK: formatting, lint, unit tests, manifest audit all passed."
