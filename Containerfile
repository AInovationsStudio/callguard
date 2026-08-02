# Reproducible CallGuard Android build image.
# Pinned JDK 17 + only the pinned Android SDK platform and build-tools packages.
# The source tree is mounted at /workspace at run time; nothing is copied in here,
# and no host credentials are baked into the image.

# Base image pinned by immutable digest (tag retained for readability).
# Digest resolves the multi-arch manifest list for eclipse-temurin:17-jdk-jammy.
FROM docker.io/eclipse-temurin:17-jdk-jammy@sha256:723151f3fc88ca2060153ee08ab8dbbea7983d6ed6f2622fe440acf178737c94

ARG ANDROID_SDK_ROOT=/android-sdk
ARG ANDROID_CMDLINE_TOOLS_VERSION=11076708
ARG ANDROID_PLATFORM=android-34
ARG ANDROID_BUILD_TOOLS=34.0.0

# Published integrity for commandlinetools-linux-11076708_latest.zip, as listed in
# Google's dl.google.com/android/repository/repository2-3.xml (sha-1 + size).
# Google publishes only sha-1 (not sha-256) for this archive; both are checked.
ARG ANDROID_CMDLINE_TOOLS_SHA1=d313adb7aedccf6cf0cfca51ec180f0059f5f8f8
ARG ANDROID_CMDLINE_TOOLS_SIZE=153607504

ENV ANDROID_HOME=${ANDROID_SDK_ROOT} \
    ANDROID_SDK_ROOT=${ANDROID_SDK_ROOT} \
    PATH="${ANDROID_SDK_ROOT}/cmdline-tools/latest/bin:${ANDROID_SDK_ROOT}/platform-tools:${PATH}"

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        ca-certificates \
        unzip \
        wget \
    && rm -rf /var/lib/apt/lists/*

RUN groupadd --system --gid 1000 developer \
    && useradd --system --uid 1000 --gid 1000 --create-home --shell /bin/bash developer

RUN mkdir -p "${ANDROID_SDK_ROOT}/cmdline-tools" \
    && wget -q -O /tmp/cmdline-tools.zip \
        "https://dl.google.com/android/repository/commandlinetools-linux-${ANDROID_CMDLINE_TOOLS_VERSION}_latest.zip" \
    && echo "${ANDROID_CMDLINE_TOOLS_SHA1}  /tmp/cmdline-tools.zip" | sha1sum -c - \
    && test "$(stat -c%s /tmp/cmdline-tools.zip)" -eq "${ANDROID_CMDLINE_TOOLS_SIZE}" \
    && unzip -q /tmp/cmdline-tools.zip -d "${ANDROID_SDK_ROOT}/cmdline-tools" \
    && mv "${ANDROID_SDK_ROOT}/cmdline-tools/cmdline-tools" "${ANDROID_SDK_ROOT}/cmdline-tools/latest" \
    && rm /tmp/cmdline-tools.zip \
    && chown -R developer:developer "${ANDROID_SDK_ROOT}"

# Create the Gradle cache mount point owned by the non-root user so a named
# volume mounted here (podman :U or docker first-mount copy) is writable by
# developer without weakening non-root behavior.
RUN mkdir -p /home/developer/.gradle \
    && chown -R developer:developer /home/developer/.gradle

USER developer

RUN yes | sdkmanager --licenses > /dev/null 2>&1 || true \
    && sdkmanager --install \
        "platform-tools" \
        "platforms;${ANDROID_PLATFORM}" \
        "build-tools;${ANDROID_BUILD_TOOLS}" \
    && sdkmanager --version

WORKDIR /workspace
