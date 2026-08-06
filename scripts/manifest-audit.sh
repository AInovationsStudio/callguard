#!/usr/bin/env bash
# Manifest permission audit for CallGuard.
# The app declares only READ_CONTACTS for its explicitly user-triggered contact
# rule flow. This script fails if any other permission is present, if the
# launcher activity is missing or not exported, or if a second activity is
# declared. The call-screening service must be protected by the platform
# binding permission and expose only the screening intent.
set -euo pipefail

cd "$(dirname "$0")/.."
MANIFEST="app/src/main/AndroidManifest.xml"

if [[ ! -f "$MANIFEST" ]]; then
    echo "error: manifest not found at $MANIFEST" >&2
    exit 1
fi

# 1. Only READ_CONTACTS is allowed.
PERM_COUNT=$(grep -c "<uses-permission" "$MANIFEST" || true)
if [[ "$PERM_COUNT" -ne 1 ]] || ! grep -q 'android.permission.READ_CONTACTS' "$MANIFEST"; then
    echo "error: manifest must declare exactly android.permission.READ_CONTACTS and no other permissions." >&2
    grep -n "<uses-permission" "$MANIFEST" >&2 || true
    exit 1
fi
for FORBIDDEN in INTERNET SEND_SMS RECORD_AUDIO ACCESS_FINE_LOCATION ACCESS_COARSE_LOCATION; do
    if grep -q "android.permission.$FORBIDDEN" "$MANIFEST"; then
        echo "error: forbidden permission android.permission.$FORBIDDEN declared." >&2
        exit 1
    fi
done

# 2. Exactly one activity, exported, with MAIN/LAUNCHER.
ACTIVITY_COUNT=$(grep -c "<activity" "$MANIFEST" || true)
if [[ "$ACTIVITY_COUNT" -ne 1 ]]; then
    echo "error: expected exactly one <activity>, found $ACTIVITY_COUNT." >&2
    exit 1
fi

if ! grep -q 'android:name=".MainActivity"' "$MANIFEST"; then
    echo "error: launcher activity .MainActivity not found." >&2
    exit 1
fi
if ! grep -q 'android:exported="true"' "$MANIFEST"; then
    echo "error: launcher activity must be exported=true." >&2
    exit 1
fi
if ! grep -q 'android.intent.action.MAIN' "$MANIFEST"; then
    echo "error: MAIN intent action missing." >&2
    exit 1
fi
if ! grep -q 'android.intent.category.LAUNCHER' "$MANIFEST"; then
    echo "error: LAUNCHER category missing." >&2
    exit 1
fi

# 3. Exactly one protected call-screening service; no receivers/providers.
SERVICE_COUNT=$(grep -c "<service" "$MANIFEST" || true)
if [[ "$SERVICE_COUNT" -ne 1 ]] ||
    ! grep -q 'android:name=".screening.CallGuardScreeningService"' "$MANIFEST" ||
    ! grep -q 'android.permission.BIND_SCREENING_SERVICE' "$MANIFEST" ||
    ! grep -q 'android.telecom.CallScreeningService' "$MANIFEST"; then
    echo "error: call-screening service declaration is missing or incomplete." >&2
    exit 1
fi
for TAG in "<receiver" "<provider"; do
    C=$(grep -c "$TAG" "$MANIFEST" || true)
    if [[ "$C" -ne 0 ]]; then
        echo "error: unexpected $TAG element in manifest." >&2
        exit 1
    fi
done

echo "[manifest-audit] OK: READ_CONTACTS only, exported launcher, protected call-screening service, no receiver/provider."
