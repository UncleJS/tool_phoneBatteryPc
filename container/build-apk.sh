#!/usr/bin/env bash
# SPDX-License-Identifier: AGPL-3.0-or-later
# Copyright (C) 2026 UncleJS
#
# Runs inside the phonebattery-builder container: unit tests + debug APK,
# then copies the results to the output volume (/out).
set -euo pipefail

SRC_DIR="${SRC_DIR:-/src}"
OUT_DIR="${OUT_DIR:-/out}"
APK_NAME="${APK_NAME:-BatteryLevelApp.apk}"
BUILD_TOOLS_VERSION="${BUILD_TOOLS_VERSION:-36.0.0}"
BUILD_TOOLS_DIR="${ANDROID_HOME:-/opt/android-sdk}/build-tools/${BUILD_TOOLS_VERSION}"

APK_SRC="app/build/outputs/apk/debug/app-debug.apk"
TEST_REPORT_DIR="app/build/reports/tests/testDebugUnitTest"
TEST_RESULTS_DIR="app/build/test-results/testDebugUnitTest"

utc_now() { date -u '+%Y-%m-%d %H:%M:%S'; }
log() { printf '%s [build-apk] %s\n' "$(utc_now)" "$*" >&2; }

case "${1:-}" in
  -h|--help)
    cat <<EOF
Usage: build-apk.sh

Builds the debug APK in ${SRC_DIR} with:
  gradle --no-daemon --stacktrace testDebugUnitTest assembleDebug
and writes to ${OUT_DIR}:
  ${APK_NAME}      the debug APK
  build-info.txt   UTC build time, package info, signing cert SHA-256, test totals
  test-summary.txt per-suite unit test counts
  test-reports/    HTML unit test report
EOF
    exit 0
    ;;
esac

# Writes per-suite counts to test-summary.txt and echoes the totals line.
summarize_tests() {
  local summary="${OUT_DIR}/test-summary.txt"
  local total=0 failures=0 errors=0 skipped=0 f line t fl e s name
  : > "$summary"
  shopt -s nullglob
  for f in "${TEST_RESULTS_DIR}"/TEST-*.xml; do
    line="$(grep -o -m1 '<testsuite [^>]*>' "$f" || true)"
    [[ -n "$line" ]] || continue
    name="$(sed -n 's/.* name="\([^"]*\)".*/\1/p' <<<"$line")"
    t="$(sed -n 's/.* tests="\([0-9]*\)".*/\1/p' <<<"$line")"
    fl="$(sed -n 's/.* failures="\([0-9]*\)".*/\1/p' <<<"$line")"
    e="$(sed -n 's/.* errors="\([0-9]*\)".*/\1/p' <<<"$line")"
    s="$(sed -n 's/.* skipped="\([0-9]*\)".*/\1/p' <<<"$line")"
    t="${t:-0}" fl="${fl:-0}" e="${e:-0}" s="${s:-0}"
    printf '%-60s tests=%s failures=%s errors=%s skipped=%s\n' "$name" "$t" "$fl" "$e" "$s" >> "$summary"
    total=$((total + t)) failures=$((failures + fl)) errors=$((errors + e)) skipped=$((skipped + s))
  done
  shopt -u nullglob
  printf 'TOTAL tests=%s failures=%s errors=%s skipped=%s\n' "$total" "$failures" "$errors" "$skipped" | tee -a "$summary"
}

cd "$SRC_DIR"
mkdir -p "$OUT_DIR"
# Start from a clean output volume so stale artifacts never mix with new ones.
find "$OUT_DIR" -mindepth 1 -delete

started="$(utc_now)"
log "Build started (UTC ${started})"
log "Running: gradle --no-daemon --stacktrace testDebugUnitTest assembleDebug"

gradle_status=0
gradle --no-daemon --stacktrace testDebugUnitTest assembleDebug || gradle_status=$?

# Test reports are useful even (especially) when the build failed.
if [[ -d "$TEST_REPORT_DIR" ]]; then
  cp -r "$TEST_REPORT_DIR" "${OUT_DIR}/test-reports"
fi
test_totals="$(summarize_tests)"
log "Unit tests: ${test_totals}"

finished="$(utc_now)"

if [[ "$gradle_status" -ne 0 ]]; then
  {
    echo "Status:        FAILED (gradle exit code ${gradle_status})"
    echo "Started (UTC): ${started}"
    echo "Finished (UTC): ${finished}"
    echo "Unit tests:    ${test_totals}"
  } > "${OUT_DIR}/build-info.txt"
  log "Gradle build FAILED with exit code ${gradle_status}"
  exit "$gradle_status"
fi

if [[ ! -f "$APK_SRC" ]]; then
  log "Gradle succeeded but ${APK_SRC} was not found"
  exit 1
fi

cp "$APK_SRC" "${OUT_DIR}/${APK_NAME}"
log "APK copied to ${OUT_DIR}/${APK_NAME}"

cert_sha256="$("${BUILD_TOOLS_DIR}/apksigner" verify --print-certs "${OUT_DIR}/${APK_NAME}" \
  | sed -n 's/.*certificate SHA-256 digest: *//p' | head -n1 || true)"
cert_sha256_colon=""
if [[ -n "$cert_sha256" ]]; then
  cert_sha256_colon="$(tr '[:lower:]' '[:upper:]' <<<"$cert_sha256" | sed 's/../&:/g; s/:$//')"
fi
package_info="$("${BUILD_TOOLS_DIR}/aapt2" dump badging "${OUT_DIR}/${APK_NAME}" 2>/dev/null \
  | sed -n '1s/^package: //p' || true)"
apk_bytes="$(stat -c '%s' "${OUT_DIR}/${APK_NAME}")"

{
  echo "Status:              OK"
  echo "Started (UTC):       ${started}"
  echo "Built (UTC):         ${finished}"
  echo "APK:                 ${APK_NAME} (${apk_bytes} bytes)"
  echo "Package:             ${package_info:-unknown}"
  echo "Signing cert SHA-256: ${cert_sha256:-unknown}"
  echo "                      ${cert_sha256_colon}"
  echo "Unit tests:          ${test_totals}"
} > "${OUT_DIR}/build-info.txt"

log "Signing cert SHA-256: ${cert_sha256:-unknown}"
log "Build finished (UTC ${finished})"
