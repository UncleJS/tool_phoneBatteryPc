#!/usr/bin/env bash
# SPDX-License-Identifier: AGPL-3.0-or-later
# Copyright (C) 2026 UncleJS
#
# Stops any running build, then builds the APK again (stop.sh + start.sh).
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."

case "${1:-}" in
  -h|--help)
    cat <<USAGE
Usage: scripts/restart.sh [start.sh options] [-h|--help]

Runs scripts/stop.sh, then scripts/start.sh with the given options
(for example --rebuild).

Options:
  -h, --help   Show this help.
USAGE
    exit 0
    ;;
esac

scripts/stop.sh
exec scripts/start.sh "$@"
