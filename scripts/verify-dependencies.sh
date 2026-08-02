#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

for required in app/gradle.lockfile gradle/verification-metadata.xml; do
    if [[ ! -s "$required" ]]; then
        echo "error: required dependency integrity file is missing or empty: $required" >&2
        exit 1
    fi
done

./gradlew --no-daemon --dependency-verification strict :app:assembleDebug >/dev/null
echo "[dependencies] OK: locked debug build and checksum verification passed."
