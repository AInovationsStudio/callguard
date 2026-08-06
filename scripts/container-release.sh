#!/usr/bin/env bash
# Build the unsigned CallGuard release APK entirely inside the pinned,
# integrity-checked container. No host Java, Gradle, or Android SDK is
# required. No signing keys are used or produced: the release build type has
# no configured signingConfig, so `assembleRelease` always yields an unsigned
# artifact suitable for identity/manifest verification, never for
# distribution as-is.
set -euo pipefail

cd "$(dirname "$0")/.."
ROOT="$PWD"

IMAGE_TAG="callguard-android:api34-emu-v2"
GRADLE_VOLUME="callguard-gradle-cache"
RELEASE_APK="app/build/outputs/apk/release/app-release-unsigned.apk"

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
        echo "[container-release] note: docker is best-effort; podman is the tested engine." >&2
        ;;
    *)
        echo "error: unsupported container engine '$ENGINE' (set CONTAINER_ENGINE to podman or docker)" >&2
        exit 127
        ;;
esac

if ! "$ENGINE" image inspect "$IMAGE_TAG" >/dev/null 2>&1; then
    echo "[container-release] building image $IMAGE_TAG (one-time)..."
    "$ENGINE" build -t "$IMAGE_TAG" -f Containerfile .
fi

# `clean` first so a stale APK from a previous build type/version can never
# be mistaken for the current release candidate's artifact. Verification runs
# in the same container invocation via verify-apk.sh --direct so the freshly
# built artifact is checked before success is reported, without spawning a
# nested container.
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
    bash -lc "./gradlew --no-daemon --dependency-verification strict --stacktrace clean assembleRelease && scripts/verify-apk.sh --direct \"$RELEASE_APK\""

if [[ ! -f "$RELEASE_APK" ]]; then
    echo "error: expected unsigned release APK not found at $RELEASE_APK" >&2
    exit 1
fi
echo "[container-release] OK: $RELEASE_APK (unsigned, verified)"
