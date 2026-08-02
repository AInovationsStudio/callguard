# CallGuard

Offline-first Android app for blocking or allowing incoming cellular calls
using understandable, country-aware rules. Android package:
`studio.ainovations.callguard`.

The MVP includes a guided Compose rule editor, country-aware normalization,
Room persistence, and an Android `CallScreeningService`. Screening is inactive
until the user selects CallGuard as the system call-screening app. Contact
matching is optional and requires an explicit contacts permission grant.

## Reproducible container build

The primary build path is a container with a pinned JDK 17 and the pinned
Android SDK platform/build-tools. No host Java, Gradle, or Android SDK is
required. The source tree is mounted at `/workspace`; Gradle caches live in a
named volume. No host credentials are copied into the image. Podman is the
tested engine (rootless, non-root `developer` user via `--userns=keep-id`);
Docker is supported best-effort via engine gating in the scripts
(`CONTAINER_ENGINE=podman|docker` to override).

Build the debug APK:

```bash
./scripts/container-build.sh
```

Expected: `app/build/outputs/apk/debug/app-debug.apk` is produced and the
command exits 0.

Run the deterministic local gate (formatting, lint, unit tests, and manifest
audit):

```bash
./scripts/container-test.sh
```

Run Compose and service contract tests on the pinned API-34 emulator:

```bash
./scripts/container-test.sh --instrumentation
```

Capture the verified GUI screens:

```bash
./scripts/capture-screenshots.sh
```

The emulator is created inside the container and uses `/dev/kvm` when
available. Instrumentation is a hard failure when the emulator cannot boot; it
is never silently skipped.

Check dependency integrity directly:

```bash
./scripts/verify-dependencies.sh
```

On Android, open CallGuard settings and tap **Set CallGuard as screening app**.
The system role dialog is authoritative; CallGuard never claims the role is
active based only on the user's intent. Unknown or unparseable caller IDs use
the configured fallback action, which defaults to allow.

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
Dependency locking is enabled in strict mode (`dependencyLocking` with
`lockMode = LockMode.STRICT` in `app/build.gradle.kts`); the lockfiles
(`app/gradle.lockfile`, `settings-gradle.lockfile`) are committed. Dependency
verification is enforced at build time via the `--dependency-verification strict`
flag passed by both scripts, backed by the committed
`gradle/verification-metadata.xml` (sha-256). A fresh container build is
reproducible and integrity-checked. The base image is pinned by immutable
digest and the Android command-line tools archive is checked against Google's
published sha-1 and size before install.

## Project layout

```
settings.gradle.kts        # project + repository settings
build.gradle.kts           # top-level plugin declarations
gradle/libs.versions.toml  # version catalog
app/                       # application module (namespace studio.ainovations.callguard)
Containerfile              # reproducible build image
.devcontainer/             # VS Code dev container
scripts/                   # build, tests, emulator, manifest, and dependency gates
docs/screenshots/          # emulator-captured GUI snapshots
```
