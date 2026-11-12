# SPDX-License-Identifier: AGPL-3.0-or-later
# Copyright (C) 2026 UncleJS
#
# Builder image for the phonebattery Android app.
# Toolchain layers (JDK, Android SDK, Gradle) come first so that source
# changes only invalidate the final COPY layer.

FROM docker.io/library/eclipse-temurin:21-jdk

# Label first so every intermediate layer carries it (lets uninstall.sh find
# and prune the build cache by label).
LABEL io.github.unclejs.project="phonebattery" \
      org.opencontainers.image.title="phonebattery-builder" \
      org.opencontainers.image.description="Builds the phonebattery Android debug APK" \
      org.opencontainers.image.licenses="AGPL-3.0-or-later"

ARG CMDLINE_TOOLS_URL=https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip
ARG ANDROID_PLATFORM=android-36
ARG BUILD_TOOLS_VERSION=36.0.0
ARG GRADLE_VERSION=8.14.3

ENV ANDROID_HOME=/opt/android-sdk \
    ANDROID_SDK_ROOT=/opt/android-sdk \
    GRADLE_HOME=/opt/gradle/current \
    GRADLE_USER_HOME=/root/.gradle \
    BUILD_TOOLS_VERSION=${BUILD_TOOLS_VERSION}
ENV PATH=${GRADLE_HOME}/bin:${ANDROID_HOME}/cmdline-tools/latest/bin:${ANDROID_HOME}/build-tools/${BUILD_TOOLS_VERSION}:${PATH}

# Base tools.
RUN apt-get update \
 && DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
        ca-certificates curl unzip \
 && rm -rf /var/lib/apt/lists/*

# Android command-line tools.
RUN set -eux; \
    mkdir -p "${ANDROID_HOME}/cmdline-tools" /tmp/cmdline-tools; \
    curl -fsSL -o /tmp/cmdline-tools.zip "${CMDLINE_TOOLS_URL}"; \
    unzip -q /tmp/cmdline-tools.zip -d /tmp/cmdline-tools; \
    mv /tmp/cmdline-tools/cmdline-tools "${ANDROID_HOME}/cmdline-tools/latest"; \
    rm -rf /tmp/cmdline-tools /tmp/cmdline-tools.zip

# Android SDK packages (licenses accepted non-interactively). The ~/.android
# directory is reset afterwards because the keystore volume is mounted there.
RUN set -eux; \
    yes | sdkmanager --licenses > /dev/null; \
    sdkmanager --install "platforms;${ANDROID_PLATFORM}" "build-tools;${BUILD_TOOLS_VERSION}"; \
    rm -rf /root/.android; \
    mkdir -p /root/.android

# Gradle distribution (checksum verified against the published .sha256).
RUN set -eux; \
    base="https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip"; \
    curl -fsSL -o /tmp/gradle.zip "${base}"; \
    curl -fsSL -o /tmp/gradle.zip.sha256 "${base}.sha256"; \
    echo "$(tr -d '[:space:]' < /tmp/gradle.zip.sha256)  /tmp/gradle.zip" | sha256sum -c -; \
    mkdir -p /opt/gradle; \
    unzip -q /tmp/gradle.zip -d /opt/gradle; \
    ln -s "/opt/gradle/gradle-${GRADLE_VERSION}" /opt/gradle/current; \
    rm -f /tmp/gradle.zip /tmp/gradle.zip.sha256; \
    gradle --version

COPY --chmod=0755 container/build-apk.sh /usr/local/bin/build-apk.sh

WORKDIR /src
COPY . /src

CMD ["/usr/local/bin/build-apk.sh"]
