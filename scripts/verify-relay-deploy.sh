#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${1:-${BASE_URL:-https://app.falkenr.com}}"
SITE_URL="${2:-${SITE_URL:-https://falkenr.com}}"

failures=0

pass() {
  printf 'PASS %s\n' "$1"
}

fail() {
  printf 'FAIL %s\n' "$1" >&2
  failures=$((failures + 1))
}

fetch_body() {
  local url="$1"
  curl -fsS --max-time 15 "$url"
}

fetch_headers() {
  local url="$1"
  curl -sSI --max-time 15 "$url"
}

expect_body_contains() {
  local name="$1"
  local url="$2"
  local needle="$3"

  local body
  if ! body="$(fetch_body "$url")"; then
    fail "$name could not fetch $url"
    return
  fi

  if [[ "$body" == *"$needle"* ]]; then
    pass "$name"
  else
    fail "$name missing expected text: $needle"
  fi
}

expect_header_contains() {
  local name="$1"
  local url="$2"
  local needle="$3"

  local headers
  if ! headers="$(fetch_headers "$url")"; then
    fail "$name could not fetch headers from $url"
    return
  fi

  local normalized_headers
  local normalized_needle
  normalized_headers="$(printf '%s' "$headers" | tr '[:upper:]' '[:lower:]')"
  normalized_needle="$(printf '%s' "$needle" | tr '[:upper:]' '[:lower:]')"

  if [[ "$normalized_headers" == *"$normalized_needle"* ]]; then
    pass "$name"
  else
    fail "$name missing expected header text: $needle"
  fi
}

expect_status_code() {
  local name="$1"
  local method="$2"
  local url="$3"
  local expected="$4"

  local status
  if ! status="$(curl -sS -o /dev/null -w '%{http_code}' --max-time 15 -X "$method" "$url")"; then
    fail "$name could not reach $url"
    return
  fi

  if [[ "$status" == "$expected" ]]; then
    pass "$name"
  else
    fail "$name expected HTTP $expected, got HTTP $status"
  fi
}

printf 'Verifying relay deploy\n'
printf '  app:  %s\n' "$BASE_URL"
printf '  site: %s\n' "$SITE_URL"

expect_body_contains "relay status endpoint" "$BASE_URL/sessions/status" '"status":"ok"'
expect_body_contains "hosted dashboard route" "$BASE_URL/app" "activationFunnel"
expect_body_contains "hosted dashboard onboarding" "$BASE_URL/app" "First relay attach"
expect_body_contains "public homepage shell" "$SITE_URL/" '<div id="root"></div>'
expect_body_contains "public homepage assets" "$SITE_URL/" '/assets/index-'

expect_status_code "checkout rejects GET" "GET" "$BASE_URL/sessions/billing/checkout" "405"
expect_header_contains "checkout allows POST" "$BASE_URL/sessions/billing/checkout" "allow: POST"
expect_status_code "customer portal rejects GET" "GET" "$BASE_URL/sessions/billing/portal" "405"
expect_header_contains "customer portal allows POST" "$BASE_URL/sessions/billing/portal" "allow: POST"
expect_status_code "stripe webhook rejects GET" "GET" "$BASE_URL/sessions/billing/stripe/webhook" "405"
expect_header_contains "stripe webhook allows POST" "$BASE_URL/sessions/billing/stripe/webhook" "allow: POST"

if [[ "$failures" -gt 0 ]]; then
  printf '\n%d verification check(s) failed.\n' "$failures" >&2
  exit 1
fi

printf '\nRelay deploy verification passed.\n'
