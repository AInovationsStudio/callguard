#!/usr/bin/env bash
# Run the CallGuard deterministic local gate inside the reproducible container:
# formatting check, Android lint, unit tests, and the manifest permission audit.
# Pass --instrumentation to additionally run Compose/service instrumentation
# tests (connectedDebugAndroidTest) against a connected device or emulator.
# Per the implementation plan's Final verification step, instrumentation tests
# run "on an API 26 emulator and a current API emulator when available" — this
# container ships no emulator, so --instrumentation reports that absence
# clearly and exits 0 rather than failing the deterministic gate.
set -euo pipefail

RUN_INSTRUMENTATION=0
for arg in "$@"; do
    case "$arg" in
        --instrumentation) RUN_INSTRUMENTATION=1 ;;
        *) echo "error: unknown argument '$arg'" >&2; exit 64 ;;
    esac
done

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

# Runs a command inside the same container/user/mounts as run_gradle, without
# the gradlew entrypoint — used to probe `adb devices` before deciding whether
# connectedDebugAndroidTest has anything to run against.
run_in_container() {
    "$ENGINE" run --rm \
        -v "$ROOT:/workspace:Z" \
        -v "${GRADLE_VOL}" \
        -e ANDROID_HOME=/android-sdk \
        -e ANDROID_SDK_ROOT=/android-sdk \
        -w /workspace \
        "${USERNS_ARGS[@]}" \
        --user developer \
        "$IMAGE_TAG" \
        "$@"
}

echo "[container-test] formatting (spotlessCheck)..."
run_gradle spotlessCheck

echo "[container-test] lint (lintDebug)..."
run_gradle lintDebug

echo "[container-test] unit tests (testDebugUnitTest)..."
run_gradle testDebugUnitTest

echo "[container-test] manifest audit..."
bash scripts/manifest-audit.sh

if [[ "$RUN_INSTRUMENTATION" -eq 1 ]]; then
    echo "[container-test] checking for a connected device/emulator..."
    DEVICE_COUNT="$(run_in_container bash -c 'adb start-server >/dev/null 2>&1; adb devices | tail -n +2 | grep -c device$' || true)"
    if [[ "${DEVICE_COUNT:-0}" -eq 0 ]]; then
        echo "[container-test] no device/emulator attached to this container: skipping instrumentation tests." >&2
        echo "[container-test] this image ships no emulator; run against a real device/emulator to exercise RuleWizardTest." >&2
    else
        echo "[container-test] instrumentation tests (connectedDebugAndroidTest)..."
        run_gradle connectedDebugAndroidTest
    fi
fi

echo "[container-test] OK: formatting, lint, unit tests, manifest audit all passed."
