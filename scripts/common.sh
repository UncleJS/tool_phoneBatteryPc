#!/usr/bin/env bash
# SPDX-License-Identifier: AGPL-3.0-or-later
# Copyright (C) 2026 UncleJS
#
# Shared names and helpers for the lifecycle scripts. Source it, don't run it:
#   source "$(dirname "${BASH_SOURCE[0]}")/common.sh"

if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
  case "${1:-}" in
    -h|--help)
      echo "common.sh: shared names and helpers, sourced by the other scripts in scripts/."
      exit 0
      ;;
  esac
  echo "common.sh is meant to be sourced by the other scripts, not executed." >&2
  exit 1
fi

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"

# --- Names -------------------------------------------------------------------
PROJECT="phonebattery"
LABEL_KEY="io.github.unclejs.project"
LABEL="${LABEL_KEY}=${PROJECT}"

IMAGE="localhost/phonebattery-builder:latest"
BASE_IMAGE="docker.io/library/eclipse-temurin:21-jdk"

BUILD_SERVICE="phonebattery-builder-build.service"
APK_SERVICE="phonebattery-apk.service"
APK_CONTAINER="phonebattery-apk"

VOLUMES=(phonebattery-gradle phonebattery-keystore phonebattery-dist)
DIST_VOLUME="phonebattery-dist"
KEYSTORE_VOLUME="phonebattery-keystore"
VOLUME_SERVICES=(
  phonebattery-gradle-volume.service
  phonebattery-keystore-volume.service
  phonebattery-dist-volume.service
)
# Every systemd unit Quadlet generates for this repo.
SERVICES=("$APK_SERVICE" "$BUILD_SERVICE" "${VOLUME_SERVICES[@]}")

QUADLET_SRC="${REPO_ROOT}/quadlet"
QUADLET_DST="${XDG_CONFIG_HOME:-$HOME/.config}/containers/systemd"
UNIT_FILES=(
  phonebattery-builder.build
  phonebattery-apk.container
  phonebattery-gradle.volume
  phonebattery-keystore.volume
  phonebattery-dist.volume
)
REPO_DIR_PLACEHOLDER="@REPO_DIR@"

DIST_DIR="${REPO_ROOT}/dist"
APK_NAME="BatteryLevelApp.apk"

# --- Helpers -----------------------------------------------------------------
# Timestamps are UTC, formatted yyyy-MM-dd HH:mm:ss.
log()  { printf '%s [%s] %s\n' "$(date -u '+%Y-%m-%d %H:%M:%S')" "$PROJECT" "$*"; }
warn() { printf '%s [%s] WARNING: %s\n' "$(date -u '+%Y-%m-%d %H:%M:%S')" "$PROJECT" "$*" >&2; }
die()  { printf '%s [%s] ERROR: %s\n' "$(date -u '+%Y-%m-%d %H:%M:%S')" "$PROJECT" "$*" >&2; exit 1; }

require_rootless_podman() {
  [[ "${EUID:-$(id -u)}" -ne 0 ]] || die "Run as your normal user, not root (rootless Podman only)."
  command -v podman >/dev/null 2>&1 || die "podman is not installed or not on PATH."
  local rootless
  rootless="$(podman info --format '{{.Host.Security.Rootless}}' 2>/dev/null || true)"
  [[ "$rootless" == "true" ]] || die "Podman is not running rootless (podman info reports Rootless=${rootless:-unknown})."
}

require_user_systemd() {
  command -v systemctl >/dev/null 2>&1 || die "systemctl not found; a systemd user session is required."
  systemctl --user show-environment >/dev/null 2>&1 \
    || die "No systemd user session (systemctl --user is not reachable). Log in to a normal session and retry."
  if [[ ! -x /usr/lib/systemd/user-generators/podman-user-generator \
     && ! -x /usr/libexec/podman/quadlet ]]; then
    warn "Podman Quadlet generator not found; Quadlet units may not be generated."
  fi
}

# Prints a unit file from quadlet/ with the repo path filled in.
render_unit() {
  local src="${QUADLET_SRC}/$1" escaped
  escaped="$(printf '%s' "$REPO_ROOT" | sed -e 's/[\\&|]/\\&/g')"
  sed -e "s|${REPO_DIR_PLACEHOLDER}|${escaped}|g" "$src"
}

unit_loaded() {
  [[ "$(systemctl --user show -p LoadState --value "$1" 2>/dev/null || true)" == "loaded" ]]
}

# Copies the repo's Quadlet units to QUADLET_DST (only when they changed) and
# reloads systemd so the generated services exist. Pass --force to always reload.
install_quadlets() {
  local force=0 changed=0 unit content dst
  [[ "${1:-}" == "--force" ]] && force=1
  [[ "$REPO_ROOT" != *[[:space:]]* ]] || die "The repo path must not contain whitespace: ${REPO_ROOT}"
  mkdir -p "$QUADLET_DST"
  for unit in "${UNIT_FILES[@]}"; do
    [[ -f "${QUADLET_SRC}/${unit}" ]] || die "Missing unit file: ${QUADLET_SRC}/${unit}"
    content="$(render_unit "$unit")"
    dst="${QUADLET_DST}/${unit}"
    if [[ ! -f "$dst" || "$(cat "$dst")" != "$content" ]]; then
      printf '%s\n' "$content" > "$dst"
      changed=1
      log "Installed ${dst}"
    fi
  done
  if [[ "$force" -eq 1 || "$changed" -eq 1 ]] || ! unit_loaded "$APK_SERVICE" || ! unit_loaded "$BUILD_SERVICE"; then
    systemctl --user daemon-reload
    log "systemd user daemon reloaded"
  fi
  unit_loaded "$APK_SERVICE" && unit_loaded "$BUILD_SERVICE" \
    || die "Quadlet did not generate ${APK_SERVICE}/${BUILD_SERVICE}. Check: /usr/libexec/podman/quadlet -dryrun -user"
}

show_journal() {
  local unit="$1" lines="${2:-50}"
  echo "----- journalctl --user -u ${unit} -n ${lines} -----" >&2
  journalctl --user -u "$unit" -n "$lines" --no-pager >&2 || true
  echo "-----" >&2
}
