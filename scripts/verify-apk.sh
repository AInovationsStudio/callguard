#!/usr/bin/env bash
# Verify the CallGuard release-candidate APK's identity before it is trusted
# as this release's artifact: application ID, version name/code, the merged
# manifest permission set, non-debuggable release posture, and that the file
# is a well-formed APK.
# Container-first: parses the APK using `aapt` from the pinned build-tools
# inside the same integrity-checked image used to build it, so no host
# Android SDK is required.
#
# Usage: scripts/verify-apk.sh [--direct] [path/to/app.apk]
#   --direct  Run `aapt` on the current machine/container without spawning a
#             nested container. Used by container-release.sh after assembleRelease.
#   --self-test
#             Run deterministic negative controls and exit (no APK argument).
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

collect_badging_direct() {
    local apk_rel="$1"
    local sdk_root="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-/android-sdk}}"
    local aapt="${sdk_root}/build-tools/${BUILD_TOOLS_VERSION}/aapt"

    if [[ ! -x "$aapt" ]]; then
        echo "error: aapt not found at $aapt (set ANDROID_SDK_ROOT or use container mode)" >&2
        exit 127
    fi

    local output
    if ! output="$("$aapt" dump badging "$apk_rel" 2>&1)"; then
        echo "error: aapt could not parse '$apk_rel' as a valid APK:" >&2
        echo "$output" >&2
        exit 1
    fi
    printf '%s\n' "$output"
}

collect_badging_container() {
    local apk_rel="$1"

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

    local output
    if ! output="$("$ENGINE" run --rm \
        -v "$ROOT:/workspace:Z" \
        -w /workspace \
        "${USERNS_ARGS[@]}" \
        --user developer \
        "$IMAGE_TAG" \
        "/android-sdk/build-tools/${BUILD_TOOLS_VERSION}/aapt" dump badging "$apk_rel" 2>&1)"; then
        echo "error: aapt could not parse '$apk_rel' as a valid APK:" >&2
        echo "$output" >&2
        exit 1
    fi
    printf '%s\n' "$output"
}

collect_badging() {
    local apk_rel="$1"
    local use_direct="$2"

    if [[ "$use_direct" -eq 1 ]]; then
        collect_badging_direct "$apk_rel"
    else
        collect_badging_container "$apk_rel"
    fi
}

verify_apk_badging() {
    local apk_display_path="$1"
    local badging="$2"

    local package_line
    package_line="$(grep -m1 '^package:' <<<"$badging" || true)"
    if [[ -z "$package_line" ]]; then
        echo "error: no 'package:' line in aapt output for '$apk_display_path'; not a valid APK." >&2
        echo "$badging" >&2
        exit 1
    fi

    extract_field() {
        grep -oP "$1='[^']*'" <<<"$package_line" | head -1 | sed -E "s/^[^=]+='(.*)'$/\1/"
    }

    local app_id version_code version_name fail=0
    app_id="$(extract_field "name")"
    version_code="$(extract_field "versionCode")"
    version_name="$(extract_field "versionName")"

    if [[ "$app_id" != "$EXPECTED_APPLICATION_ID" ]]; then
        echo "error: application ID mismatch: expected '$EXPECTED_APPLICATION_ID', got '$app_id'" >&2
        fail=1
    fi
    if [[ "$version_name" != "$EXPECTED_VERSION_NAME" ]]; then
        echo "error: versionName mismatch: expected '$EXPECTED_VERSION_NAME', got '$version_name'" >&2
        fail=1
    fi
    if [[ "$version_code" != "$EXPECTED_VERSION_CODE" ]]; then
        echo "error: versionCode mismatch: expected '$EXPECTED_VERSION_CODE', got '$version_code'" >&2
        fail=1
    fi

    if grep -q '^application-debuggable' <<<"$badging"; then
        echo "error: APK is debuggable; release verification requires a non-debuggable artifact" >&2
        fail=1
    fi

    # Parse every aapt permission line whose key begins with `uses-permission`,
    # including SDK-qualified variants such as `uses-permission-sdk-23:`.
    local permissions permission_count expected_sorted actual_sorted
    mapfile -t permissions < <(
        grep -E '^uses-permission' <<<"$badging" \
            | grep -oP "name='[^']*'" \
            | sed -E "s/^name='(.*)'$/\1/" \
            | sort -u
    )
    permission_count="${#permissions[@]}"

    expected_sorted="$(printf '%s\n' "$EXPECTED_PERMISSION" "$EXPECTED_SYNTHETIC_PERMISSION" | sort)"
    actual_sorted="$(printf '%s\n' "${permissions[@]:-}" | sed '/^$/d' | sort -u)"

    if [[ "$permission_count" -ne 2 ]]; then
        echo "error: expected exactly 2 unique permissions, found $permission_count:" >&2
        printf '  %s\n' "${permissions[@]:-<none>}" >&2
        fail=1
    elif [[ "$actual_sorted" != "$expected_sorted" ]]; then
        echo "error: permission set mismatch." >&2
        echo "  expected:" >&2
        printf '    %s\n' "$EXPECTED_PERMISSION" "$EXPECTED_SYNTHETIC_PERMISSION" >&2
        echo "  found:" >&2
        printf '    %s\n' "${permissions[@]:-<none>}" >&2
        fail=1
    fi

    if [[ "$fail" -ne 0 ]]; then
        exit 1
    fi

    echo "[verify-apk] OK: $apk_display_path"
    echo "  applicationId: $app_id"
    echo "  versionName:   $version_name"
    echo "  versionCode:   $version_code"
    echo "  debuggable:    no"
    echo "  permissions:"
    printf '    %s\n' "${permissions[@]}"
}

run_verify() {
    local apk_path="$1"
    local use_direct="$2"

    if [[ ! -f "$apk_path" ]]; then
        echo "error: APK not found at $apk_path (run scripts/container-release.sh first)" >&2
        exit 1
    fi
    if [[ ! -s "$apk_path" ]]; then
        echo "error: APK at $apk_path is empty" >&2
        exit 1
    fi

    local apk_rel
    case "$apk_path" in
        "$ROOT"/*) apk_rel="${apk_path#"$ROOT"/}" ;;
        /*)
            echo "error: APK path must be inside the repository: $apk_path" >&2
            exit 1
            ;;
        *) apk_rel="$apk_path" ;;
    esac

    local badging
    badging="$(collect_badging "$apk_rel" "$use_direct")"
    verify_apk_badging "$apk_path" "$badging"
}

run_self_test() {
    local failures=0
    local script="$ROOT/scripts/verify-apk.sh"

    expect_failure() {
        local label="$1"
        shift
        if "$@" >/dev/null 2>&1; then
            echo "error: negative control '$label' should have failed" >&2
            failures=1
        else
            echo "[verify-apk self-test] OK: $label rejected"
        fi
    }

    expect_failure "missing APK path" \
        bash "$script" /tmp/callguard-missing.apk

    local empty_apk="$ROOT/.verify-apk-selftest-empty.apk"
    : >"$empty_apk"
    expect_failure "empty APK" \
        bash "$script" "$empty_apk"
    rm -f "$empty_apk"

    local release_apk="app/build/outputs/apk/release/app-release-unsigned.apk"
    if [[ -s "$ROOT/$release_apk" ]]; then
        local corrupt_apk="$ROOT/.verify-apk-selftest-corrupt.apk"
        head -c 128 "$ROOT/$release_apk" >"$corrupt_apk"
        expect_failure "truncated/corrupt APK" \
            bash "$script" "$corrupt_apk"
        rm -f "$corrupt_apk"
    else
        echo "[verify-apk self-test] skip: truncated/corrupt APK (release artifact not built yet)" >&2
    fi

    local debug_apk="app/build/outputs/apk/debug/app-debug.apk"
    if [[ -s "$ROOT/$debug_apk" ]]; then
        expect_failure "debuggable debug APK on release verification path" \
            bash "$script" "$debug_apk"
    else
        echo "[verify-apk self-test] skip: debuggable debug APK (debug artifact not built yet)" >&2
    fi

    if [[ "$failures" -ne 0 ]]; then
        exit 1
    fi
    echo "[verify-apk self-test] OK: negative controls passed"
}

DIRECT=0
SELF_TEST=0
APK_PATH=""

for arg in "$@"; do
    case "$arg" in
        --direct) DIRECT=1 ;;
        --self-test) SELF_TEST=1 ;;
        -*) echo "error: unknown option '$arg'" >&2; exit 64 ;;
        *)
            if [[ -n "$APK_PATH" ]]; then
                echo "error: unexpected extra argument '$arg'" >&2
                exit 64
            fi
            APK_PATH="$arg"
            ;;
    esac
done

if [[ "$SELF_TEST" -eq 1 ]]; then
    if [[ -n "$APK_PATH" ]]; then
        echo "error: --self-test does not accept an APK path" >&2
        exit 64
    fi
    run_self_test
    exit 0
fi

APK_PATH="${APK_PATH:-app/build/outputs/apk/release/app-release-unsigned.apk}"
run_verify "$APK_PATH" "$DIRECT"
