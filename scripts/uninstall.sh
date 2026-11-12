#!/usr/bin/env bash
# SPDX-License-Identifier: AGPL-3.0-or-later
# Copyright (C) 2026 UncleJS
#
# Removes everything this repo created in Podman/systemd/$HOME.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."
# shellcheck source=scripts/common.sh
source scripts/common.sh

usage() {
  cat <<USAGE
Usage: scripts/uninstall.sh [--purge] [--remove-quadlets] [-h|--help]

Stops the services and removes the containers (incl. build containers), the
builder image and its build cache, the base image (${BASE_IMAGE},
unless something else still uses it), any networks/secrets labelled
${LABEL}, and ${DIST_DIR}/.

Kept by default:
  - volumes: ${VOLUMES[*]}
  - Quadlet units in ${QUADLET_DST}/

Options:
  --purge            Also remove the volumes (Gradle cache, debug signing key,
                     build output). Newly built APKs will then be signed with a
                     new key: uninstall the old app from the phone first.
  --remove-quadlets  Also remove the Quadlet units and run daemon-reload.
  -h, --help         Show this help.

With both flags the host is left as if the project had never been installed.
Exits non-zero if unexpected leftovers are found.
USAGE
}

PURGE=0
REMOVE_QUADLETS=0
while [[ $# -gt 0 ]]; do
  case "$1" in
    --purge) PURGE=1 ;;
    --remove-quadlets) REMOVE_QUADLETS=1 ;;
    -h|--help) usage; exit 0 ;;
    *) usage >&2; die "Unknown option: $1" ;;
  esac
  shift
done

require_rootless_podman
require_user_systemd

# Lines from stdin that mention the project (no error when nothing matches).
match_project() { grep -i -- "$PROJECT" || true; }

# --- 1. Stop services ----------------------------------------------------------
for unit in "${SERVICES[@]}"; do
  if unit_loaded "$unit"; then
    systemctl --user stop "$unit" 2>/dev/null || warn "Could not stop ${unit}"
  fi
done
for unit in "${SERVICES[@]}"; do
  systemctl --user reset-failed "$unit" >/dev/null 2>&1 || true
done
log "Services stopped."

# --- 2. Containers (incl. buildah build containers) and pods -------------------
our_image_ids() {
  {
    podman images -a -q --no-trunc --filter "label=${LABEL}" 2>/dev/null || true
    podman image inspect --format '{{.Id}}' "$IMAGE" 2>/dev/null || true
  } | sed 's/^sha256://' | sort -u
}

container_ids() {
  local ids
  ids="$(our_image_ids | tr '\n' '|' | sed 's/|$//')"
  {
    podman ps -a --external -q --filter "label=${LABEL}" 2>/dev/null || true
    podman ps -a --external --format '{{.ID}} {{.Names}} {{.Image}} {{.ImageID}}' 2>/dev/null \
      | awk -v p="$PROJECT" -v ids="$ids" '
          BEGIN { n = split(ids, a, "|"); for (i = 1; i <= n; i++) if (a[i] != "") want[a[i]] = 1 }
          {
            hit = index(tolower($0), p) > 0
            img = $4; sub(/^sha256:/, "", img)
            for (k in want) if (img != "" && index(k, img) == 1) hit = 1
            if (hit) print $1
          }' || true
  } | sort -u
}

mapfile -t ctrs < <(container_ids)
if [[ "${#ctrs[@]}" -gt 0 ]]; then
  log "Removing containers: ${ctrs[*]}"
  podman rm -f -v "${ctrs[@]}" >/dev/null 2>&1 || true
else
  log "No containers to remove."
fi

mapfile -t pods < <(podman pod ls --format '{{.ID}} {{.Name}} {{.Labels}}' 2>/dev/null | match_project | awk '{print $1}')
if [[ "${#pods[@]}" -gt 0 ]]; then
  log "Removing pods: ${pods[*]}"
  podman pod rm -f "${pods[@]}" >/dev/null 2>&1 || true
fi

# --- 3. Images and build cache -------------------------------------------------
if podman image exists "$IMAGE"; then
  log "Removing image ${IMAGE}"
  podman rmi -f "$IMAGE" >/dev/null 2>&1 || warn "Could not remove ${IMAGE}"
fi
# Intermediate layers (the build cache) carry the project label from the
# Containerfile, so a label-filtered prune removes exactly our cache.
for _ in 1 2 3; do
  podman image prune -a -f --filter "label=${LABEL}" >/dev/null 2>&1 || true
  mapfile -t imgs < <({
    podman images -a -q --filter "label=${LABEL}" 2>/dev/null || true
    podman images -a --format '{{.ID}} {{.Repository}}:{{.Tag}}' 2>/dev/null | match_project | awk '{print $1}'
  } | sort -u)
  [[ "${#imgs[@]}" -gt 0 ]] || break
  podman rmi -f "${imgs[@]}" >/dev/null 2>&1 || true
done
log "Builder image and build cache removed."

BASE_KEPT_REASON=""
if podman image exists "$BASE_IMAGE"; then
  users="$(podman ps -a --external --filter "ancestor=${BASE_IMAGE}" --format '{{.Names}}' 2>/dev/null | tr '\n' ' ' || true)"
  if [[ -n "${users// /}" ]]; then
    BASE_KEPT_REASON="still used by container(s): ${users}"
  elif ! podman rmi "$BASE_IMAGE" >/dev/null 2>&1; then
    BASE_KEPT_REASON="podman refused to remove it (other images are probably built on it)"
  else
    log "Removed base image ${BASE_IMAGE}"
  fi
  if [[ -n "$BASE_KEPT_REASON" ]]; then
    log "Skipped base image ${BASE_IMAGE}: ${BASE_KEPT_REASON}"
  fi
fi

# --- 4. Networks and secrets (none are created; remove any labelled/prefixed) --
mapfile -t nets < <({
  podman network ls -q --filter "label=${LABEL}" 2>/dev/null || true
  podman network ls --format '{{.Name}}' 2>/dev/null | match_project
} | sort -u)
if [[ "${#nets[@]}" -gt 0 ]]; then
  log "Removing networks: ${nets[*]}"
  podman network rm -f "${nets[@]}" >/dev/null 2>&1 || true
fi
mapfile -t secrets < <(podman secret ls --format '{{.Name}}' 2>/dev/null | match_project)
if [[ "${#secrets[@]}" -gt 0 ]]; then
  log "Removing secrets: ${secrets[*]}"
  podman secret rm "${secrets[@]}" >/dev/null 2>&1 || true
fi

# --- 5. Files written by the scripts -------------------------------------------
if [[ -e "$DIST_DIR" ]]; then
  rm -rf "${DIST_DIR:?}"
  log "Removed ${DIST_DIR}/"
fi

# --- 6. Volumes (--purge) ------------------------------------------------------
if [[ "$PURGE" -eq 1 ]]; then
  mapfile -t vols < <({
    printf '%s\n' "${VOLUMES[@]}"
    podman volume ls -q --filter "label=${LABEL}" 2>/dev/null || true
  } | sort -u)
  for v in "${vols[@]}"; do
    if podman volume exists "$v"; then
      podman volume rm -f "$v" >/dev/null && log "Removed volume ${v}"
    fi
  done
  warn "The debug signing key (volume ${KEYSTORE_VOLUME}) is gone. The next APK is signed with a new key: uninstall the old app from the phone before installing it."
else
  log "Kept volumes (use --purge to remove): ${VOLUMES[*]}"
fi

# --- 7. Quadlet units (--remove-quadlets) --------------------------------------
if [[ "$REMOVE_QUADLETS" -eq 1 ]]; then
  for unit in "${UNIT_FILES[@]}"; do
    if [[ -e "${QUADLET_DST:?}/${unit:?}" ]]; then
      rm -f -- "${QUADLET_DST:?}/${unit:?}"
      log "Removed ${QUADLET_DST}/${unit}"
    fi
  done
  systemctl --user daemon-reload
  for unit in "${SERVICES[@]}"; do
    systemctl --user reset-failed "$unit" >/dev/null 2>&1 || true
  done
  log "Quadlet units removed; systemd user daemon reloaded."
else
  log "Kept Quadlet units in ${QUADLET_DST}/ (use --remove-quadlets to remove)."
fi

# --- 8. Leftover check ---------------------------------------------------------
log "Checking for leftovers..."
leftovers=0
# report <category> <lines> [kept=0|1]
report() {
  local what="$1" lines="$2" kept="${3:-0}"
  [[ -n "$lines" ]] || return 0
  if [[ "$kept" -eq 1 ]]; then
    echo "  kept ${what}:"
  else
    echo "  LEFTOVER ${what}:"
    leftovers=1
  fi
  printf '%s\n' "$lines" | sed 's/^/    /'
}

report "containers" "$(podman ps -a --external --format '{{.ID}} {{.Names}} {{.Image}} {{.Labels}}' 2>/dev/null | match_project)"
report "pods" "$(podman pod ls --format '{{.ID}} {{.Name}} {{.Labels}}' 2>/dev/null | match_project)"
report "images" "$({
  podman images -a --format '{{.ID}} {{.Repository}}:{{.Tag}}' 2>/dev/null | match_project
  podman images -a --filter "label=${LABEL}" --format '{{.ID}} {{.Repository}}:{{.Tag}}' 2>/dev/null || true
} | sort -u)"
report "volumes" "$({
  podman volume ls --format '{{.Name}}' 2>/dev/null | match_project
  podman volume ls -q --filter "label=${LABEL}" 2>/dev/null || true
} | sort -u)" "$((1 - PURGE))"
report "networks" "$(podman network ls --format '{{.Name}}' 2>/dev/null | match_project)"
report "secrets" "$(podman secret ls --format '{{.Name}}' 2>/dev/null | match_project)"
report "Quadlet units in ${QUADLET_DST}" \
  "$(find "$QUADLET_DST" -maxdepth 1 -iname "*${PROJECT}*" 2>/dev/null || true)" "$((1 - REMOVE_QUADLETS))"
report "failed systemd units" "$(systemctl --user list-units --failed --all --no-legend --plain "${PROJECT}*" 2>/dev/null || true)"
if [[ "$REMOVE_QUADLETS" -eq 1 ]]; then
  report "systemd units" "$(systemctl --user list-units --all --no-legend --plain "${PROJECT}*" 2>/dev/null || true)"
fi
if [[ -e "$DIST_DIR" ]]; then
  report "files" "$DIST_DIR"
fi
if [[ -n "$BASE_KEPT_REASON" ]]; then
  echo "  note: base image ${BASE_IMAGE} kept (${BASE_KEPT_REASON})"
fi

if [[ "$leftovers" -ne 0 ]]; then
  die "Unexpected leftovers found (listed above)."
fi
log "Uninstall complete; no unexpected leftovers."
