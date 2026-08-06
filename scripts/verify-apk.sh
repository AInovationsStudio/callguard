#!/usr/bin/env bash
# Verify the CallGuard release-candidate APK's identity before it is trusted
# as this release's artifact: application ID, version name/code, and the
# manifest permission set, plus that the file is a well-formed APK.
# Container-first: parses the APK using `aapt` from the pinned build-tools
# inside the same integrity-checked image used to build it, so no host
# Android SDK is required.
#
# Usage: scripts/verify-apk.sh [path/to/app.apk]
#   Defaults to the unsigned release APK produced by container-release.sh.
#   A path argument must be inside the repository (it is mounted at
#   /workspace in the container).
set -euo pipefail

cd "$(dirname "$0")/.."
ROOT="$PWD"

IMAGE_TAG="callguard-android:api34-emu-v2"
BUILD_TOOLS_VERSION="34.0.0"

EXPECTED_APPLICATION_ID="studio.ainovations.callguard"
EXPECTED_VERSION_NAME="0.1.0"
EXPECTED_VERSION_CODE="1"
EXPECTED_PERMISSION="android.permission.READ_CONTACTS"
# AGP's manifest merger auto-declares and self-requests a signature-level,
# app-scoped "<applicationId>.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION" in
# every build whenever a dependency (here, an AndroidX/Compose library)
# calls Context.registerReceiver() without an explicit
# RECEIVER_EXPORTED/RECEIVER_NOT_EXPORTED flag, for compileSdk 33+. It is not
# present in app/src/main/AndroidManifest.xml (scripts/manifest-audit.sh
# checks that source file and correctly does not see it) and grants no
# cross-app capability: it exists purely so the app can protect its own
# dynamically-registered receivers on Android 13+, and no other app can hold
# it. It is present in both the debug and unsigned release builds regardless
# of source manifest content, so it is allowlisted here by exact name rather
# than treated as an undeclared permission.
EXPECTED_SYNTHETIC_PERMISSION="${EXPECTED_APPLICATION_ID}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"

APK_PATH="${1:-app/build/outputs/apk/release/app-release-unsigned.apk}"

if [[ ! -f "$APK_PATH" ]]; then
    echo "error: APK not found at $APK_PATH (run scripts/container-release.sh first)" >&2
    exit 1
fi
if [[ ! -s "$APK_PATH" ]]; then
    echo "error: APK at $APK_PATH is empty" >&2
    exit 1
fi

# Normalize to a path relative to the repo root, since that is what the
# container sees mounted at /workspace.
case "$APK_PATH" in
    /*) APK_REL="${APK_PATH#"$ROOT"/}" ;;
    *) APK_REL="$APK_PATH" ;;
esac

ENGINE="${CONTAINER_ENGINE:-}"
if [[ -z "$ENGINE" ]]; then
    if command -v podman >/dev/null 2>&1; then ENGINE="podman"; \
    elif command -v docker >/dev/null 2>&1; then ENGINE="docker"; \
    else echo "error: no container engine (podman/docker) found" >&2; exit 127; fi
fi

case "$ENGINE" in
    podman) USERNS_ARGS=(--userns=keep-id) ;;
    docker)
        USERNS_ARGS=()
        echo "[verify-apk] note: docker is best-effort; podman is the tested engine." >&2
        ;;
    *)
        echo "error: unsupported container engine '$ENGINE' (set CONTAINER_ENGINE to podman or docker)" >&2
        exit 127
        ;;
esac

if ! "$ENGINE" image inspect "$IMAGE_TAG" >/dev/null 2>&1; then
    echo "[verify-apk] building image $IMAGE_TAG (one-time)..."
    "$ENGINE" build -t "$IMAGE_TAG" -f Containerfile .
fi

if ! BADGING="$("$ENGINE" run --rm \
    -v "$ROOT:/workspace:Z" \
    -w /workspace \
    "${USERNS_ARGS[@]}" \
    --user developer \
    "$IMAGE_TAG" \
    "/android-sdk/build-tools/${BUILD_TOOLS_VERSION}/aapt" dump badging "$APK_REL" 2>&1)"; then
    echo "error: aapt could not parse '$APK_PATH' as a valid APK:" >&2
    echo "$BADGING" >&2
    exit 1
fi

PACKAGE_LINE="$(grep -m1 '^package:' <<<"$BADGING" || true)"
if [[ -z "$PACKAGE_LINE" ]]; then
    echo "error: no 'package:' line in aapt output for '$APK_PATH'; not a valid APK." >&2
    echo "$BADGING" >&2
    exit 1
fi

extract_field() {
    grep -oP "$1='[^']*'" <<<"$PACKAGE_LINE" | head -1 | sed -E "s/^[^=]+='(.*)'$/\1/"
}

APP_ID="$(extract_field "name")"
VERSION_CODE="$(extract_field "versionCode")"
VERSION_NAME="$(extract_field "versionName")"

FAIL=0

if [[ "$APP_ID" != "$EXPECTED_APPLICATION_ID" ]]; then
    echo "error: application ID mismatch: expected '$EXPECTED_APPLICATION_ID', got '$APP_ID'" >&2
    FAIL=1
fi
if [[ "$VERSION_NAME" != "$EXPECTED_VERSION_NAME" ]]; then
    echo "error: versionName mismatch: expected '$EXPECTED_VERSION_NAME', got '$VERSION_NAME'" >&2
    FAIL=1
fi
if [[ "$VERSION_CODE" != "$EXPECTED_VERSION_CODE" ]]; then
    echo "error: versionCode mismatch: expected '$EXPECTED_VERSION_CODE', got '$VERSION_CODE'" >&2
    FAIL=1
fi

PERMISSIONS="$(grep '^uses-permission:' <<<"$BADGING" | grep -oP "name='[^']*'" | sed -E "s/^name='(.*)'$/\1/" || true)"

if ! grep -qxF "$EXPECTED_PERMISSION" <<<"$PERMISSIONS"; then
    echo "error: expected permission '$EXPECTED_PERMISSION' not found; found:" >&2
    echo "${PERMISSIONS:-<none>}" >&2
    FAIL=1
fi

UNEXPECTED_PERMISSIONS="$(grep -vxF -e "$EXPECTED_PERMISSION" -e "$EXPECTED_SYNTHETIC_PERMISSION" <<<"$PERMISSIONS" || true)"
if [[ -n "$UNEXPECTED_PERMISSIONS" ]]; then
    echo "error: unexpected permission(s) present beyond '$EXPECTED_PERMISSION' and the AGP-injected '$EXPECTED_SYNTHETIC_PERMISSION':" >&2
    echo "$UNEXPECTED_PERMISSIONS" >&2
    FAIL=1
fi

if [[ "$FAIL" -ne 0 ]]; then
    exit 1
fi

echo "[verify-apk] OK: $APK_PATH"
echo "  applicationId: $APP_ID"
echo "  versionName:   $VERSION_NAME"
echo "  versionCode:   $VERSION_CODE"
echo "  permissions:"
echo "    ${PERMISSIONS//$'\n'/$'\n'    }"
