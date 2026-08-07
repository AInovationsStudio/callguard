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
        echo "[container] CI/docker: making Gradle write paths writable for uid 1000 (developer)..." >&2
        # A fresh checkout is owned by the runner uid; the container writes build
        # outputs as uid 1000. Scope chmod to paths Gradle actually writes instead
        # of the entire repository tree.
        local path
        for path in \
            "$root/.gradle" \
            "$root/app/build" \
            "$root/build" \
            "$root/.kotlin" \
            "$root/.idea"; do
            if [[ -e "$path" ]]; then
                chmod -R a+rwX "$path" 2>/dev/null || true
            fi
        done
        mkdir -p "$root/app/build" "$root/build" "$root/.gradle" "$root/.kotlin"
        chmod -R a+rwX "$root/app/build" "$root/build" "$root/.gradle" "$root/.kotlin" 2>/dev/null || true
    fi
}

# Keep Gradle's project-local cache off the bind mount; everything else still
# lands under GRADLE_USER_HOME on the named cache volume.
CONTAINER_GRADLE_CACHE_ARGS=(--project-cache-dir=/home/developer/.gradle/project-cache)
