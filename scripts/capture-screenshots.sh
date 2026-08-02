#!/usr/bin/env bash
# Build, launch, and capture the CallGuard GUI in the pinned Android emulator.
set -euo pipefail

cd "$(dirname "$0")/.."
ROOT="$PWD"
IMAGE_TAG="callguard-android:api34-emu-v2"
GRADLE_VOLUME="callguard-gradle-cache"

ENGINE="${CONTAINER_ENGINE:-podman}"
case "$ENGINE" in
    podman)
        USERNS_ARGS=(--userns=keep-id)
        DEVICE_ARGS=(--device /dev/kvm --group-add keep-groups)
        GRADLE_VOL="${GRADLE_VOLUME}:/home/developer/.gradle:U,Z"
        ;;
    docker)
        USERNS_ARGS=()
        DEVICE_ARGS=(--device /dev/kvm)
        GRADLE_VOL="${GRADLE_VOLUME}:/home/developer/.gradle:Z"
        ;;
    *)
        echo "error: unsupported container engine '$ENGINE'" >&2
        exit 127
        ;;
esac

mkdir -p docs/screenshots

"$ENGINE" run --rm \
    -v "$ROOT:/workspace:Z" \
    -v "$GRADLE_VOL" \
    -e ANDROID_HOME=/android-sdk \
    -e ANDROID_SDK_ROOT=/android-sdk \
    -w /workspace \
    "${USERNS_ARGS[@]}" \
    "${DEVICE_ARGS[@]}" \
    --user developer \
    "$IMAGE_TAG" \
    bash -lc '
        set -euo pipefail
        emulator -avd callguard-api34 \
            -no-window \
            -no-audio \
            -no-boot-anim \
            -no-snapshot \
            -accel on \
            -no-metrics \
            -gpu swiftshader_indirect \
            >/tmp/callguard-emulator.log 2>&1 &
        EMULATOR_PID=$!
        finish() {
            kill "$EMULATOR_PID" >/dev/null 2>&1 || true
            wait "$EMULATOR_PID" >/dev/null 2>&1 || true
        }
        trap finish EXIT
        adb start-server >/dev/null
        for _ in $(seq 1 120); do
            if adb get-state >/dev/null 2>&1; then break; fi
            sleep 1
        done
        adb get-state >/dev/null 2>&1 || {
            cat /tmp/callguard-emulator.log >&2
            exit 1
        }
        for _ in $(seq 1 120); do
            if [[ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d "\r")" == "1" ]]; then break; fi
            sleep 1
        done
        [[ "$(adb shell getprop sys.boot_completed | tr -d "\r")" == "1" ]] || {
            cat /tmp/callguard-emulator.log >&2
            exit 1
        }
        ./gradlew --no-daemon --dependency-verification strict assembleDebug
        adb install -r app/build/outputs/apk/debug/app-debug.apk >/dev/null
        adb shell am force-stop studio.ainovations.callguard
        adb shell monkey -p studio.ainovations.callguard 1 >/dev/null
        sleep 2
        adb exec-out screencap -p > docs/screenshots/01-rule-list.png
        adb shell input tap 1000 1800
        sleep 1
        adb shell input tap 400 800
        adb shell input text 1571888
        adb shell input keyevent 4
        sleep 1
        adb exec-out screencap -p > docs/screenshots/02-rule-wizard.png
        adb shell input tap 85 1815
        sleep 1
        adb shell input tap 950 140
        sleep 1
        adb exec-out screencap -p > docs/screenshots/03-settings.png
    '

echo "[screenshots] captured:"
ls -lh docs/screenshots/*.png
