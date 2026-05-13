#!/usr/bin/env bash
set -uo pipefail

# SCRUM-266
# 서버 헬스체크 + OOM 이벤트 감지 후 Discord Webhook 알림 스크립트

HEALTHCHECK_URL="${HEALTHCHECK_URL:-}"
DISCORD_WEBHOOK_URL="${DISCORD_WEBHOOK_URL:-${DISCORD_PR_WEBHOOK_URL:-}}"
CHECK_NAME="${CHECK_NAME:-withbuddy-server}"

CURL_CONNECT_TIMEOUT="${CURL_CONNECT_TIMEOUT:-5}"
CURL_MAX_TIME="${CURL_MAX_TIME:-10}"
STATE_DIR="${STATE_DIR:-/tmp/withbuddy-monitor}"
ALERT_COOLDOWN_SECONDS="${ALERT_COOLDOWN_SECONDS:-600}"

OOM_CHECK_ENABLED="${OOM_CHECK_ENABLED:-1}"
OOM_LOOKBACK="${OOM_LOOKBACK:-5 minutes ago}"

if [[ -z "$HEALTHCHECK_URL" || -z "$DISCORD_WEBHOOK_URL" ]]; then
  echo "[error] HEALTHCHECK_URL and (DISCORD_WEBHOOK_URL or DISCORD_PR_WEBHOOK_URL) are required." >&2
  exit 2
fi

if ! command -v curl >/dev/null 2>&1; then
  echo "[error] curl is required." >&2
  exit 2
fi

if ! command -v python3 >/dev/null 2>&1; then
  echo "[error] python3 is required for safe JSON payload encoding." >&2
  exit 2
fi

safe_name="$(printf '%s' "$CHECK_NAME" | tr -c 'A-Za-z0-9._-' '_')"
mkdir -p "$STATE_DIR"

health_state_file="$STATE_DIR/${safe_name}.health.state"
down_alert_ts_file="$STATE_DIR/${safe_name}.down.last_alert_ts"
oom_sig_file="$STATE_DIR/${safe_name}.oom.last_sig"

now_utc() {
  date -u +"%Y-%m-%dT%H:%M:%SZ"
}

json_escape() {
  # stdin 문자열을 JSON string literal 형태로 안전하게 인코딩
  python3 -c 'import json,sys; print(json.dumps(sys.stdin.read()))'
}

send_discord() {
  local title="$1"
  local detail="$2"
  local content
  content=$(
    cat <<EOF
[WithBuddy Monitor] ${title}
- check: ${CHECK_NAME}
- time(UTC): $(now_utc)
- health_url: ${HEALTHCHECK_URL}
- detail: ${detail}
EOF
  )

  local payload
  payload=$(
    printf '{"content":%s,"allowed_mentions":{"parse":[]}}' \
      "$(printf '%s' "$content" | json_escape)"
  )

  if ! curl -fsS \
    -H "Content-Type: application/json" \
    -X POST \
    --data "$payload" \
    "$DISCORD_WEBHOOK_URL" >/dev/null; then
    echo "[warn] discord webhook send failed" >&2
    return 1
  fi
}

check_health() {
  local http_code
  local curl_rc=0
  local curl_err=""
  local curl_err_file

  curl_err_file="$(mktemp)"
  http_code="$(curl -sS \
    --connect-timeout "$CURL_CONNECT_TIMEOUT" \
    --max-time "$CURL_MAX_TIME" \
    -o /dev/null \
    -w "%{http_code}" \
    "$HEALTHCHECK_URL" 2>"$curl_err_file")" || curl_rc=$?
  curl_err="$(tr '\n' ' ' <"$curl_err_file" | sed 's/[[:space:]]\+/ /g')"
  rm -f "$curl_err_file"

  local is_up="0"
  if [[ "$curl_rc" -eq 0 && "$http_code" == "200" ]]; then
    is_up="1"
  fi

  local prev_state="UP"
  if [[ -f "$health_state_file" ]]; then
    prev_state="$(cat "$health_state_file")"
  fi

  if [[ "$is_up" == "1" ]]; then
    echo "UP" >"$health_state_file"
    if [[ "$prev_state" == "DOWN" ]]; then
      send_discord "RECOVERY" "health check recovered (HTTP 200)"
    fi
    return 0
  fi

  echo "DOWN" >"$health_state_file"

  local now_epoch
  now_epoch="$(date +%s)"
  local last_alert_epoch=0
  if [[ -f "$down_alert_ts_file" ]]; then
    last_alert_epoch="$(cat "$down_alert_ts_file")"
  fi

  local elapsed=$((now_epoch - last_alert_epoch))
  if [[ "$prev_state" != "DOWN" || "$elapsed" -ge "$ALERT_COOLDOWN_SECONDS" ]]; then
    echo "$now_epoch" >"$down_alert_ts_file"
    send_discord "DOWN" "http_code=${http_code}, curl_rc=${curl_rc}, err=${curl_err:-none}"
  fi
}

check_oom() {
  if [[ "$OOM_CHECK_ENABLED" != "1" ]]; then
    return 0
  fi

  if ! command -v dmesg >/dev/null 2>&1; then
    return 0
  fi

  local oom_lines
  oom_lines="$(dmesg --since "$OOM_LOOKBACK" 2>/dev/null | grep -Ei "out of memory|killed process" || true)"
  if [[ -z "$oom_lines" ]]; then
    return 0
  fi

  local signature
  if command -v sha256sum >/dev/null 2>&1; then
    signature="$(printf '%s' "$oom_lines" | sha256sum | awk '{print $1}')"
  else
    signature="$(printf '%s' "$oom_lines" | python3 -c 'import hashlib,sys;print(hashlib.sha256(sys.stdin.buffer.read()).hexdigest())')"
  fi

  local prev_sig=""
  if [[ -f "$oom_sig_file" ]]; then
    prev_sig="$(cat "$oom_sig_file")"
  fi

  if [[ "$signature" != "$prev_sig" ]]; then
    echo "$signature" >"$oom_sig_file"
    local first_line
    first_line="$(printf '%s\n' "$oom_lines" | head -n 1)"
    send_discord "OOM_DETECTED" "${first_line}"
  fi
}

main() {
  check_health
  check_oom
}

main "$@"
