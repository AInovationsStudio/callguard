# Reproducible CallGuard Android build image.
# Pinned JDK 17 + only the pinned Android SDK platform and build-tools packages.
# The source tree is mounted at /workspace at run time; nothing is copied in here,
# and no host credentials are baked into the image.

FROM docker.io/eclipse-temurin:17-jdk-jammy

ARG ANDROID_SDK_ROOT=/android-sdk
ARG ANDROID_CMDLINE_TOOLS_VERSION=11076708
ARG ANDROID_PLATFORM=android-34
ARG ANDROID_BUILD_TOOLS=34.0.0

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
    && unzip -q /tmp/cmdline-tools.zip -d "${ANDROID_SDK_ROOT}/cmdline-tools" \
    && mv "${ANDROID_SDK_ROOT}/cmdline-tools/cmdline-tools" "${ANDROID_SDK_ROOT}/cmdline-tools/latest" \
    && rm /tmp/cmdline-tools.zip \
    && chown -R developer:developer "${ANDROID_SDK_ROOT}"

USER developer

RUN yes | sdkmanager --licenses > /dev/null 2>&1 || true \
    && sdkmanager --install \
        "platform-tools" \
        "platforms;${ANDROID_PLATFORM}" \
        "build-tools;${ANDROID_BUILD_TOOLS}" \
    && sdkmanager --version

WORKDIR /workspace
