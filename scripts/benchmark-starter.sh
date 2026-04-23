#!/bin/zsh

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
BASELINE_LOG="$(mktemp)"
DEVTOOLS_LOG="$(mktemp)"

cleanup() {
  [[ -n "${BASELINE_PID:-}" ]] && kill "${BASELINE_PID}" >/dev/null 2>&1 || true
  [[ -n "${DEVTOOLS_PID:-}" ]] && kill "${DEVTOOLS_PID}" >/dev/null 2>&1 || true
  rm -f "${BASELINE_LOG}" "${DEVTOOLS_LOG}"
}
trap cleanup EXIT

cd "${ROOT_DIR}"

./gradlew :examples:baseline-app:bootJar :examples:demo-app:bootJar >/dev/null

measure_app() {
  local jar_path="$1"
  local log_path="$2"
  local label="$3"
  local port="$4"

  java -jar "${jar_path}" --server.port="${port}" >"${log_path}" 2>&1 &
  local pid=$!

  if [[ "${label}" == "baseline" ]]; then
    BASELINE_PID="${pid}"
  else
    DEVTOOLS_PID="${pid}"
  fi

  local startup=""
  for _ in {1..60}; do
    if grep -q "Started .* in " "${log_path}"; then
      startup="$(sed -nE 's/.*Started .* in ([0-9.]+) seconds.*/\1/p' "${log_path}" | tail -n1)"
      break
    fi
    sleep 1
  done

  if [[ -z "${startup}" ]]; then
    echo "Failed to capture startup time for ${label}" >&2
    cat "${log_path}" >&2
    exit 1
  fi

  sleep 3
  local rss_kb
  rss_kb="$(ps -o rss= -p "${pid}" | tr -d ' ')"

  printf '%s|%s|%s\n' "${startup}" "${rss_kb}" "${pid}"
}

baseline_result="$(measure_app \
  "${ROOT_DIR}/examples/baseline-app/build/libs/baseline-app-0.1.0-SNAPSHOT.jar" \
  "${BASELINE_LOG}" \
  "baseline" \
  "18083")"

devtools_result="$(measure_app \
  "${ROOT_DIR}/examples/demo-app/build/libs/demo-app-0.1.0-SNAPSHOT.jar" \
  "${DEVTOOLS_LOG}" \
  "devtools" \
  "18080")"

baseline_startup="${baseline_result%%|*}"
baseline_rest="${baseline_result#*|}"
baseline_rss="${baseline_rest%%|*}"

devtools_startup="${devtools_result%%|*}"
devtools_rest="${devtools_result#*|}"
devtools_rss="${devtools_rest%%|*}"

startup_delta="$(awk "BEGIN { printf \"%.3f\", ${devtools_startup} - ${baseline_startup} }")"
rss_delta_mb="$(awk "BEGIN { printf \"%.2f\", (${devtools_rss} - ${baseline_rss}) / 1024 }")"
baseline_rss_mb="$(awk "BEGIN { printf \"%.2f\", ${baseline_rss} / 1024 }")"
devtools_rss_mb="$(awk "BEGIN { printf \"%.2f\", ${devtools_rss} / 1024 }")"

echo "Falkenr benchmark"
echo "baseline startup seconds: ${baseline_startup}"
echo "devtools startup seconds: ${devtools_startup}"
echo "startup delta seconds: ${startup_delta}"
echo "baseline idle rss MB: ${baseline_rss_mb}"
echo "devtools idle rss MB: ${devtools_rss_mb}"
echo "idle rss delta MB: ${rss_delta_mb}"
