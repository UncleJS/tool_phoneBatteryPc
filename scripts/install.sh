#!/usr/bin/env bash
# SPDX-License-Identifier: AGPL-3.0-or-later
# Copyright (C) 2026 UncleJS
#
# Checks prerequisites, installs the Quadlet units and builds the builder image.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."
# shellcheck source=scripts/common.sh
source scripts/common.sh

usage() {
  cat <<USAGE
Usage: scripts/install.sh [--no-build] [-h|--help]

Checks for rootless Podman and a systemd user session, copies the Quadlet units
from quadlet/ to ${QUADLET_DST}/ (filling in the repo path),
runs 'systemctl --user daemon-reload' and builds the builder image
(${IMAGE}) via ${BUILD_SERVICE}.

The first image build downloads the JDK base image, Android SDK and Gradle
and can take several minutes.

Options:
  --no-build   Install the units only; skip the image build.
  -h, --help   Show this help.
USAGE
}

NO_BUILD=0
while [[ $# -gt 0 ]]; do
  case "$1" in
    --no-build) NO_BUILD=1 ;;
    -h|--help) usage; exit 0 ;;
    *) usage >&2; die "Unknown option: $1" ;;
  esac
  shift
done

require_rootless_podman
require_user_systemd
log "Podman $(podman --version | awk '{print $NF}') (rootless), systemd user session OK"

install_quadlets --force

if [[ "$NO_BUILD" -eq 1 ]]; then
  log "Skipping image build (--no-build). Run scripts/build.sh to build it."
else
  log "Building ${IMAGE} via ${BUILD_SERVICE} (first run can take several minutes)..."
  if ! systemctl --user start "$BUILD_SERVICE"; then
    show_journal "$BUILD_SERVICE" 50
    die "Image build failed."
  fi
  log "Image ${IMAGE} is ready."
fi

log "Installed. Next: scripts/start.sh builds the APK into ${DIST_DIR}/${APK_NAME}"
