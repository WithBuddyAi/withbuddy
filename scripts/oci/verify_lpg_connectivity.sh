#!/usr/bin/env bash
set -euo pipefail

# Usage:
#   ./scripts/oci/verify_lpg_connectivity.sh <ai_private_ip> [ai_port]
#
# Example:
#   ./scripts/oci/verify_lpg_connectivity.sh 10.1.2.10 8000

if [[ $# -lt 1 ]]; then
  echo "Usage: $0 <ai_private_ip> [ai_port]" >&2
  exit 1
fi

AI_PRIVATE_IP="$1"
AI_PORT="${2:-8000}"

check_tcp() {
  local host="$1"
  local port="$2"
  local name="$3"

  if timeout 3 bash -c "cat < /dev/null > /dev/tcp/${host}/${port}" 2>/dev/null; then
    echo "OK: ${name} ${host}:${port}"
  else
    echo "FAIL: ${name} ${host}:${port}" >&2
    return 1
  fi
}

echo "Checking Backend -> AI (${AI_PRIVATE_IP}:${AI_PORT})..."
check_tcp "${AI_PRIVATE_IP}" "${AI_PORT}" "AI API"
echo "Backend -> AI connectivity check completed."
