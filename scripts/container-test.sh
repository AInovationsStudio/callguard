#!/usr/bin/env bash
# Run the CallGuard deterministic local gate inside the reproducible container:
# formatting check, Android lint, unit tests, and the manifest permission audit.
set -euo pipefail

cd "$(dirname "$0")/.."
ROOT="$PWD"

IMAGE_TAG="callguard-android:dev"
GRADLE_VOLUME="callguard-gradle-cache"

ENGINE="${CONTAINER_ENGINE:-}"
if [[ -z "$ENGINE" ]]; then
    if command -v podman >/dev/null 2>&1; then ENGINE="podman"; \
    elif command -v docker >/dev/null 2>&1; then ENGINE="docker"; \
    else echo "error: no container engine (podman/docker) found" >&2; exit 127; fi
fi

case "$ENGINE" in
    podman)
        USERNS_ARGS=(--userns=keep-id)
        GRADLE_VOL="${GRADLE_VOLUME}:/home/developer/.gradle:U,Z"
        ;;
    docker)
        USERNS_ARGS=()
        GRADLE_VOL="${GRADLE_VOLUME}:/home/developer/.gradle:Z"
        echo "[container-test] note: docker is best-effort; podman is the tested engine." >&2
        ;;
    *)
        echo "error: unsupported container engine '$ENGINE' (set CONTAINER_ENGINE to podman or docker)" >&2
        exit 127
        ;;
esac

if ! "$ENGINE" image inspect "$IMAGE_TAG" >/dev/null 2>&1; then
    echo "[container-test] building image $IMAGE_TAG (one-time)..."
    "$ENGINE" build -t "$IMAGE_TAG" -f Containerfile .
fi

run_gradle() {
    "$ENGINE" run --rm \
        -v "$ROOT:/workspace:Z" \
        -v "${GRADLE_VOL}" \
        -e ANDROID_HOME=/android-sdk \
        -e ANDROID_SDK_ROOT=/android-sdk \
        -w /workspace \
        "${USERNS_ARGS[@]}" \
        --user developer \
        "$IMAGE_TAG" \
        ./gradlew --no-daemon --dependency-verification strict "$@"
}

echo "[container-test] formatting (spotlessCheck)..."
run_gradle spotlessCheck

echo "[container-test] lint (lintDebug)..."
run_gradle lintDebug

echo "[container-test] unit tests (testDebugUnitTest)..."
run_gradle testDebugUnitTest

echo "[container-test] manifest audit..."
bash scripts/manifest-audit.sh

echo "[container-test] OK: formatting, lint, unit tests, manifest audit all passed."
