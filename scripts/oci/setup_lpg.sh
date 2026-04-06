#!/usr/bin/env bash
set -euo pipefail

# Usage:
#   export OCI_COMPARTMENT_OCID=...
#   export VCN_A_OCID=...
#   export VCN_B_OCID=...
#   export VCN_A_ROUTE_TABLE_OCID=...
#   export VCN_B_ROUTE_TABLE_OCID=...
#   export VCN_A_CIDR=10.1.0.0/16
#   export VCN_B_CIDR=10.0.0.0/16
#   ./scripts/oci/setup_lpg.sh

required_vars=(
  OCI_COMPARTMENT_OCID
  VCN_A_OCID
  VCN_B_OCID
  VCN_A_ROUTE_TABLE_OCID
  VCN_B_ROUTE_TABLE_OCID
  VCN_A_CIDR
  VCN_B_CIDR
)

for v in "${required_vars[@]}"; do
  if [[ -z "${!v:-}" ]]; then
    echo "Missing required env var: ${v}" >&2
    exit 1
  fi
done

if ! command -v oci >/dev/null 2>&1; then
  echo "OCI CLI (oci) is required." >&2
  exit 1
fi

if ! command -v jq >/dev/null 2>&1; then
  echo "jq is required." >&2
  exit 1
fi

echo "[1/5] Creating LPG in VCN-A..."
LPG_A_OCID="$(
  oci network local-peering-gateway create \
    --compartment-id "${OCI_COMPARTMENT_OCID}" \
    --vcn-id "${VCN_A_OCID}" \
    --display-name "withbuddy-lpg-a" \
    --query 'data.id' \
    --raw-output
)"

echo "[2/5] Creating LPG in VCN-B..."
LPG_B_OCID="$(
  oci network local-peering-gateway create \
    --compartment-id "${OCI_COMPARTMENT_OCID}" \
    --vcn-id "${VCN_B_OCID}" \
    --display-name "withbuddy-lpg-b" \
    --query 'data.id' \
    --raw-output
)"

echo "[3/5] Connecting LPG-A -> LPG-B..."
oci network local-peering-gateway connect \
  --local-peering-gateway-id "${LPG_A_OCID}" \
  --peer-id "${LPG_B_OCID}" \
  >/dev/null

echo "[4/5] Adding route rule in VCN-A route table..."
ROUTES_A="$(
  oci network route-table get \
    --rt-id "${VCN_A_ROUTE_TABLE_OCID}" \
    --query 'data."route-rules"' \
    --raw-output
)"
UPDATED_ROUTES_A="$(
  echo "${ROUTES_A}" | jq \
    --arg cidr "${VCN_B_CIDR}" \
    --arg entity "${LPG_A_OCID}" \
    '
      if any(.[]; .cidrBlock == $cidr) then
        map(if .cidrBlock == $cidr then .networkEntityId = $entity else . end)
      else
        . + [{"cidrBlock": $cidr, "networkEntityId": $entity}]
      end
    '
)"
oci network route-table update \
  --rt-id "${VCN_A_ROUTE_TABLE_OCID}" \
  --route-rules "${UPDATED_ROUTES_A}" \
  --force \
  >/dev/null

echo "[5/5] Adding route rule in VCN-B route table..."
ROUTES_B="$(
  oci network route-table get \
    --rt-id "${VCN_B_ROUTE_TABLE_OCID}" \
    --query 'data."route-rules"' \
    --raw-output
)"
UPDATED_ROUTES_B="$(
  echo "${ROUTES_B}" | jq \
    --arg cidr "${VCN_A_CIDR}" \
    --arg entity "${LPG_B_OCID}" \
    '
      if any(.[]; .cidrBlock == $cidr) then
        map(if .cidrBlock == $cidr then .networkEntityId = $entity else . end)
      else
        . + [{"cidrBlock": $cidr, "networkEntityId": $entity}]
      end
    '
)"
oci network route-table update \
  --rt-id "${VCN_B_ROUTE_TABLE_OCID}" \
  --route-rules "${UPDATED_ROUTES_B}" \
  --force \
  >/dev/null

echo "Done."
echo "LPG_A_OCID=${LPG_A_OCID}"
echo "LPG_B_OCID=${LPG_B_OCID}"
