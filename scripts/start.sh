#!/usr/bin/env bash
# SPDX-License-Identifier: AGPL-3.0-or-later
# Copyright (C) 2026 UncleJS
#
# Runs the one-shot APK build container and copies the results to dist/.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."
# shellcheck source=scripts/common.sh
source scripts/common.sh

usage() {
  cat <<USAGE
Usage: scripts/start.sh [--rebuild] [-h|--help]

Starts ${APK_SERVICE}: a one-shot container that runs the unit tests and
builds the debug APK. The command waits until the build has finished, then
exports the ${DIST_VOLUME} volume to ${DIST_DIR}/.

Starting ${APK_SERVICE} also runs ${BUILD_SERVICE}, so the builder image
is refreshed from the current source each time (cached layers keep it fast).

Options:
  --rebuild    Run scripts/build.sh first (reports image build errors on their own).
  -h, --help   Show this help.
USAGE
}

REBUILD=0
while [[ $# -gt 0 ]]; do
  case "$1" in
    --rebuild) REBUILD=1 ;;
    -h|--help) usage; exit 0 ;;
    *) usage >&2; die "Unknown option: $1" ;;
  esac
  shift
done

require_rootless_podman
require_user_systemd
install_quadlets

if [[ "$REBUILD" -eq 1 ]]; then
  scripts/build.sh
fi

log "Building the APK in ${APK_CONTAINER} (first run downloads Gradle dependencies; this can take a while)..."
if ! systemctl --user start "$APK_SERVICE"; then
  if [[ "$(systemctl --user show -p Result --value "$BUILD_SERVICE" 2>/dev/null || true)" != "success" ]]; then
    show_journal "$BUILD_SERVICE" 50
  fi
  show_journal "$APK_SERVICE" 80
  die "APK build failed. Full log: journalctl --user -u ${APK_SERVICE} --no-pager"
fi

log "Exporting volume ${DIST_VOLUME} to ${DIST_DIR}/"
rm -rf "${DIST_DIR:?}"
mkdir -p "$DIST_DIR"
podman volume export "$DIST_VOLUME" | tar -x -C "$DIST_DIR"

APK_PATH="${DIST_DIR}/${APK_NAME}"
[[ -f "$APK_PATH" ]] || die "Build finished but ${APK_PATH} is missing."

size_bytes="$(stat -c '%s' "$APK_PATH")"
size_human="$(numfmt --to=iec --suffix=B "$size_bytes" 2>/dev/null || echo "${size_bytes} bytes")"

echo
echo "APK:  ${APK_PATH}"
echo "Size: ${size_human} (${size_bytes} bytes)"
if [[ -f "${DIST_DIR}/build-info.txt" ]]; then
  echo
  echo "Build info (${DIST_DIR}/build-info.txt):"
  sed 's/^/  /' "${DIST_DIR}/build-info.txt"
fi
if [[ -d "${DIST_DIR}/test-reports" ]]; then
  echo
  echo "Unit test report: ${DIST_DIR}/test-reports/index.html"
fi
cat <<HINTS

Install on your phone (sideload):
  1. Copy ${APK_NAME} to the phone (USB file transfer, cloud drive, e-mail, ...).
  2. On the phone, allow your file manager or browser to install apps:
     Settings > Apps > Special app access > Install unknown apps.
  3. Open the APK on the phone and tap Install.
  The debug signing key lives in the ${KEYSTORE_VOLUME} volume, so later builds
  install as updates. If the key changes (e.g. after 'uninstall.sh --purge'),
  uninstall the old app from the phone first.
HINTS
