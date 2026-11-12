#!/usr/bin/env bash
# SPDX-License-Identifier: AGPL-3.0-or-later
# Copyright (C) 2026 UncleJS
#
# Stops the APK build job and the image build, if they are running.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."
# shellcheck source=scripts/common.sh
source scripts/common.sh

usage() {
  cat <<USAGE
Usage: scripts/stop.sh [-h|--help]

Stops ${APK_SERVICE} and ${BUILD_SERVICE} if they are active.
Safe to run when nothing is running or nothing is installed.

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

require_user_systemd

for unit in "$APK_SERVICE" "$BUILD_SERVICE"; do
  if ! unit_loaded "$unit"; then
    log "${unit} is not installed; nothing to stop."
    continue
  fi
  state="$(systemctl --user show -p ActiveState --value "$unit" 2>/dev/null || true)"
  case "$state" in
    inactive|failed|"")
      log "${unit} is not running (${state:-unknown})." ;;
    *)
      log "Stopping ${unit} (${state})..."
      systemctl --user stop "$unit" || warn "Could not stop ${unit}" ;;
  esac
done
log "Stopped."
