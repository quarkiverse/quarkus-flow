#!/usr/bin/env bash
set -euo pipefail
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=common.sh
source "${DIR}/common.sh"

need kubectl

ensure_replicas

lease="$(find_leader_lease)"
if [[ -z "${lease}" ]]; then
  echo "❌ Could not find leader Lease for app=${APP_NAME} in ns=${NS}"
  k get lease -o wide || true
  exit 1
fi

h1="$(lease_holder "${lease}")"
if [[ -z "${h1}" ]]; then
  echo "❌ holderIdentity empty on lease=${lease}"
  k get lease "${lease}" -o yaml || true
  exit 1
fi

leader1="$(holder_to_pod_guess "${h1}")"
echo "✅ Current leader: ${leader1} (holderIdentity=${h1})"

echo "💥 Deleting leader pod: ${leader1}"
k delete pod "${leader1}" --ignore-not-found=true

echo "⏳ Waiting for new leader..."
timeout_s="${FAILOVER_TIMEOUT_SECONDS:-300}"
end=$((SECONDS + timeout_s))

leader2=""
while (( SECONDS < end )); do
  h2="$(lease_holder "${lease}")"
  if [[ -n "${h2}" ]]; then
    candidate="$(holder_to_pod_guess "${h2}")"
    if [[ "${candidate}" != "${leader1}" ]] && k get pod "${candidate}" >/dev/null 2>&1; then
      leader2="${candidate}"
      break
    fi
  fi
  sleep 2
done

if [[ -z "${leader2}" ]]; then
  echo "❌ Failover did not complete within ${timeout_s}s"
  echo "   Pods:"
  k get pods -l "$(pod_selector)" -o wide || true
  echo "   Lease:"
  k get lease "${lease}" -o yaml || true
  exit 1
fi

echo "✅ New leader: ${leader2}"

wait_rollout
echo "🎉 Failover verification passed"