#!/usr/bin/env bash
# Build the CallGuard debug APK entirely inside the pinned, integrity-checked container.
# No host Java, Gradle, or Android SDK is required.
set -euo pipefail

cd "$(dirname "$0")/.."
ROOT="$PWD"
# shellcheck source=container-lib.sh
source "$(dirname "$0")/container-lib.sh"

IMAGE_TAG="callguard-android:api34-emu-v2"
GRADLE_VOLUME="callguard-gradle-cache"

ENGINE="${CONTAINER_ENGINE:-}"
if [[ -z "$ENGINE" ]]; then
    if command -v podman >/dev/null 2>&1; then ENGINE="podman"; \
    elif command -v docker >/dev/null 2>&1; then ENGINE="docker"; \
    else echo "error: no container engine (podman/docker) found" >&2; exit 127; fi
fi

case "$ENGINE" in
    podman)
        # Rootless podman maps container uids to host subuids; keep-id maps the
        # host user (uid 1000) into the container so the bind-mounted /workspace
        # is writable by the non-root developer user. :U chowns the cache
        # volume to that user.
        USERNS_ARGS=(--userns=keep-id)
        GRADLE_VOL="${GRADLE_VOLUME}:/home/developer/.gradle:U,Z"
        ;;
    docker)
        # Rootful docker maps container uid 1000 to host uid 1000 directly, so
        # the bind mount is writable without keep-id. :U is a podman-only volume
        # flag; the cache volume is made writable via the image's mount point.
        USERNS_ARGS=()
        GRADLE_VOL="${GRADLE_VOLUME}:/home/developer/.gradle:Z"
        echo "[container-build] note: docker is best-effort; podman is the tested engine." >&2
        ;;
    *)
        echo "error: unsupported container engine '$ENGINE' (set CONTAINER_ENGINE to podman or docker)" >&2
        exit 127
        ;;
esac

if ! "$ENGINE" image inspect "$IMAGE_TAG" >/dev/null 2>&1; then
    echo "[container-build] building image $IMAGE_TAG (one-time)..."
    "$ENGINE" build -t "$IMAGE_TAG" -f Containerfile .
fi

container_prepare_workspace "$ROOT" "$ENGINE"

mkdir -p app/build/outputs/apk/debug

"$ENGINE" run --rm \
    -v "$ROOT:/workspace:Z" \
    -v "${GRADLE_VOL}" \
    -e ANDROID_HOME=/android-sdk \
    -e ANDROID_SDK_ROOT=/android-sdk \
    -e HOME=/home/developer \
    -e GRADLE_USER_HOME=/home/developer/.gradle \
    -w /workspace \
    "${USERNS_ARGS[@]}" \
    --user developer \
    "$IMAGE_TAG" \
    ./gradlew --no-daemon --dependency-verification strict --stacktrace \
        "${CONTAINER_GRADLE_CACHE_ARGS[@]}" assembleDebug

APK="app/build/outputs/apk/debug/app-debug.apk"
if [[ ! -f "$APK" ]]; then
    echo "error: expected APK not found at $APK" >&2
    exit 1
fi
echo "[container-build] OK: $APK"
