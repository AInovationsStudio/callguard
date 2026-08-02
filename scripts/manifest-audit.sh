#!/usr/bin/env bash
# Manifest permission audit for CallGuard.
# concurrency and UI work declares only READ_CONTACTS for the explicitly user-triggered contact
# rule flow. This script fails if any other permission is present, if the
# launcher activity is missing or not exported, or if a second activity is
# declared. The call-screening service is intentionally NOT declared here; it
# arrives in screening service once its behavior is tested.
set -euo pipefail

cd "$(dirname "$0")/.."
MANIFEST="app/src/main/AndroidManifest.xml"

if [[ ! -f "$MANIFEST" ]]; then
    echo "error: manifest not found at $MANIFEST" >&2
    exit 1
fi

# 1. Only READ_CONTACTS is allowed in concurrency and UI work.
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

# 3. No service/receiver/provider yet.
for TAG in "<service" "<receiver" "<provider"; do
    C=$(grep -c "$TAG" "$MANIFEST" || true)
    if [[ "$C" -ne 0 ]]; then
        echo "error: unexpected $TAG element in Task 1 manifest." >&2
        exit 1
    fi
done

echo "[manifest-audit] OK: READ_CONTACTS only, single exported launcher activity, no service/receiver/provider."
