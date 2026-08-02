# CallGuard

Offline-first Android app for blocking or allowing incoming cellular calls
using understandable, country-aware rules. Android package:
`studio.ainovations.callguard`.

This repository is in early scaffolding (Task 1 of the MVP plan). The launcher
activity exists; call-screening, persistence, and rule logic arrive in later
tasks. The manifest intentionally declares **no permissions** in this task.

## Reproducible container build

The primary build path is a container with a pinned JDK 17 and the pinned
Android SDK platform/build-tools. No host Java, Gradle, or Android SDK is
required. The source tree is mounted at `/workspace`; Gradle caches live in a
named volume. No host credentials are copied into the image.

Build the debug APK:

```bash
./scripts/container-build.sh
```

Expected: `app/build/outputs/apk/debug/app-debug.apk` is produced and the
command exits 0.

Run the deterministic local gate (formatting, lint, unit tests, manifest
audit):

```bash
./scripts/container-test.sh
```

## Toolchain pins

| Component | Version |
|---|---|
| JDK | 17 (eclipse-temurin) |
| Gradle | 8.7 (wrapper) |
| Android Gradle Plugin | 8.5.2 |
| Kotlin | 2.0.20 |
| Compose BOM | 2024.09.02 |
| Android platform | android-34 |
| Build tools | 34.0.0 |
| minSdk / targetSdk / compileSdk | 26 / 34 / 34 |

All plugin and library versions live in `gradle/libs.versions.toml`.
Dependency locking (`dependencyLocking`) and strict dependency verification
(`dependencyVerification`) are enabled; the lockfile and verification metadata
are committed so a fresh container build is reproducible and integrity-checked.

## Project layout

```
settings.gradle.kts        # project + verification settings
build.gradle.kts           # top-level plugin declarations
gradle/libs.versions.toml  # version catalog
app/                       # application module (namespace studio.ainovations.callguard)
Containerfile              # reproducible build image
.devcontainer/             # VS Code dev container
scripts/                   # container-build.sh, container-test.sh, manifest-audit.sh
```
