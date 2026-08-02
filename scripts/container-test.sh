#!/usr/bin/env bash
# Run the CallGuard deterministic local gate inside the reproducible container:
# formatting check, Android lint, unit tests, and the manifest permission audit.
# Pass --instrumentation to boot the pinned API-34 AVD in the container and run
# Compose/service instrumentation tests against it. A missing or unbootable AVD
# is a hard failure; UI verification must never silently skip.
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
        USERNS_ARGS=(--userns=keep-id)
        GRADLE_VOL="${GRADLE_VOLUME}:/home/developer/.gradle:U,Z"
        DEVICE_ARGS=(--device /dev/kvm --group-add keep-groups)
        ;;
    docker)
        USERNS_ARGS=()
        GRADLE_VOL="${GRADLE_VOLUME}:/home/developer/.gradle:Z"
        DEVICE_ARGS=(--device /dev/kvm)
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
        -e HOME=/home/developer \
        -e GRADLE_USER_HOME=/home/developer/.gradle \
        -w /workspace \
        "${USERNS_ARGS[@]}" \
        "${DEVICE_ARGS[@]}" \
        --user developer \
        "$IMAGE_TAG" \
        ./gradlew --no-daemon --dependency-verification strict "$@"
}

# Runs a command inside the same container/user/mounts as run_gradle, with KVM
# available for the pinned emulator.
run_in_container() {
    "$ENGINE" run --rm \
        -v "$ROOT:/workspace:Z" \
        -v "${GRADLE_VOL}" \
        -e ANDROID_HOME=/android-sdk \
        -e ANDROID_SDK_ROOT=/android-sdk \
        -e HOME=/home/developer \
        -e GRADLE_USER_HOME=/home/developer/.gradle \
        -w /workspace \
        "${USERNS_ARGS[@]}" \
        "${DEVICE_ARGS[@]}" \
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
    echo "[container-test] booting pinned API-34 emulator..."
    run_in_container bash -lc '
        set -euo pipefail
        AVD_NAME="callguard-api34"
        EMULATOR_LOG="/tmp/callguard-emulator.log"
        emulator -avd "$AVD_NAME" \
            -no-window \
            -no-audio \
            -no-boot-anim \
            -no-snapshot \
            -accel on \
            -no-metrics \
            -gpu swiftshader_indirect \
            >"$EMULATOR_LOG" 2>&1 &
        EMULATOR_PID=$!
        finish() {
            kill "$EMULATOR_PID" >/dev/null 2>&1 || true
            wait "$EMULATOR_PID" >/dev/null 2>&1 || true
        }
        trap finish EXIT
        adb start-server >/dev/null
        for _ in $(seq 1 180); do
            if adb get-state >/dev/null 2>&1; then
                break
            fi
            sleep 1
        done
        adb get-state >/dev/null 2>&1 || {
            echo "emulator did not become visible to adb; log follows:" >&2
            cat "$EMULATOR_LOG" >&2
            exit 1
        }
        for _ in $(seq 1 180); do
            if [[ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d "\r")" == "1" ]]; then
                break
            fi
            sleep 1
        done
        [[ "$(adb shell getprop sys.boot_completed | tr -d "\r")" == "1" ]] || {
            echo "emulator failed to boot; log follows:" >&2
            cat "$EMULATOR_LOG" >&2
            exit 1
        }
        ./gradlew --no-daemon --dependency-verification strict connectedDebugAndroidTest
    '
fi

echo "[container-test] OK: formatting, lint, unit tests, manifest audit all passed."
