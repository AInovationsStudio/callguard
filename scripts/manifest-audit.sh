#!/usr/bin/env bash
# Manifest permission audit for CallGuard.
# Task 1 declares only the application and launcher activity and adds NO
# permissions. This script fails if any <uses-permission> is present, if the
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

# 1. No permissions at all in Task 1.
PERM_COUNT=$(grep -c "<uses-permission" "$MANIFEST" || true)
if [[ "$PERM_COUNT" -ne 0 ]]; then
    echo "error: manifest declares $PERM_COUNT <uses-permission> element(s); Task 1 allows none." >&2
    grep -n "<uses-permission" "$MANIFEST" >&2
    exit 1
fi

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

echo "[manifest-audit] OK: no permissions, single exported launcher activity, no service/receiver/provider."
