#!/usr/bin/env bash
set -euo pipefail
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${DIR}/common.sh"

need kubectl

# This already rolls out (once)
ensure_replicas

ready="$(count_ready_pods)"
if [[ "${ready}" -lt "${EXPECTED_REPLICAS}" ]]; then
  echo "❌ Expected ${EXPECTED_REPLICAS} Ready pods, got ${ready}"
  k get pods -l "$(pod_selector)" -o wide || true
  exit 1
fi
echo "✅ Ready pods: ${ready}"

lease="$(find_leader_lease)"
if [[ -z "${lease}" ]]; then
  echo "❌ Could not find leader Lease for app=${APP_NAME} in ns=${NAMESPACE}"
  k get lease -o wide || true
  k get pods -l "$(pod_selector)" -o wide || true
  exit 1
fi
echo "✅ Leader Lease: ${lease}"

holder="$(lease_holder "${lease}")"
if [[ -z "${holder}" ]]; then
  echo "❌ Lease holderIdentity is empty"
  k get lease "${lease}" -o yaml || true
  exit 1
fi
echo "✅ holderIdentity: ${holder}"

leader_pod="$(holder_to_pod_guess "${holder}")"
if ! k get pod "${leader_pod}" >/dev/null 2>&1; then
  echo "❌ holderIdentity did not map to an existing pod: ${leader_pod}"
  k get pods -l "$(pod_selector)" -o wide || true
  k get lease "${lease}" -o yaml || true
  exit 1
fi
echo "✅ Leader pod resolved: ${leader_pod}"

r1="$(lease_renew "${lease}")"
if [[ -z "${r1}" ]]; then
  echo "❌ renewTime is empty"
  k get lease "${lease}" -o yaml || true
  exit 1
fi

r2="$(wait_for_renew_advance "${lease}" "${r1}" "${RENEW_TIMEOUT_SECONDS:-90}" "${RENEW_POLL_SECONDS:-2}" || true)"
if [[ -z "${r2}" ]]; then
  echo "❌ renewTime did not advance within timeout (still ${r1})"
  k get lease "${lease}" -o yaml || true
  exit 1
fi
echo "✅ renewTime advanced: ${r1} -> ${r2}"

echo "🎉 Lease verification passed"