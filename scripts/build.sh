#!/usr/bin/env bash
# SPDX-License-Identifier: AGPL-3.0-or-later
# Copyright (C) 2026 UncleJS
#
# Builds or rebuilds the builder image (toolchain + current source).
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."
# shellcheck source=scripts/common.sh
source scripts/common.sh

usage() {
  cat <<USAGE
Usage: scripts/build.sh [-h|--help]

Re-copies the Quadlet units (so they match the repo) and (re)builds
${IMAGE} by restarting ${BUILD_SERVICE}.
Toolchain layers are cached; only the source layer is rebuilt after code changes.

Options:
  -h, --help   Show this help.
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    -h|--help) usage; exit 0 ;;
    *) usage >&2; die "Unknown option: $1" ;;
  esac
done

require_rootless_podman
require_user_systemd
install_quadlets

log "Building ${IMAGE} via ${BUILD_SERVICE}..."
if ! systemctl --user restart "$BUILD_SERVICE"; then
  show_journal "$BUILD_SERVICE" 50
  die "Image build failed."
fi
log "Image ${IMAGE} is ready."
