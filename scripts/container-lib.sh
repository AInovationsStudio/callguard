#!/usr/bin/env bash
# Shared helpers for CallGuard container build/test scripts.

# GitHub Actions checks out the repo as the runner user (uid != 1000) while
# the pinned image runs Gradle as `developer` (uid 1000). Docker bind-mounts
# preserve host ownership, so Gradle cannot create /workspace/.gradle unless
# the tree is group/world writable. Podman uses --userns=keep-id instead.
container_prepare_workspace() {
    local root="$1"
    local engine="$2"
    if [[ "${CI:-}" == "true" && "$engine" == "docker" ]]; then
        echo "[container] CI/docker: making bind-mounted workspace writable for uid 1000 (developer)..." >&2
        chmod -R a+rwX "$root"
    fi
}

# Keep Gradle's project-local cache off the bind mount; everything else still
# lands under GRADLE_USER_HOME on the named cache volume.
CONTAINER_GRADLE_CACHE_ARGS=(--project-cache-dir=/home/developer/.gradle/project-cache)
