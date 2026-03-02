#!/usr/bin/env bash
set -euo pipefail
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${DIR}/common.sh"

need kubectl

ensure_replicas

lease="$(find_leader_lease)"
[[ -z "$lease" ]] && { echo "❌ Could not find leader Lease"; k get lease -o wide || true; exit 1; }

h1="$(lease_holder "$lease")"
[[ -z "$h1" ]] && { echo "❌ holderIdentity empty"; k get lease "$lease" -o yaml || true; exit 1; }
leader1="$(holder_to_pod_guess "$h1")"

echo "✅ Lease=${lease}"
echo "✅ Leader=${leader1} (holderIdentity=${h1})"

pods=($(list_pods))
if [[ "${#pods[@]}" -lt 3 ]]; then
  echo "❌ Expected 3 pods, got ${#pods[@]}: ${pods[*]}"
  k get pods -l "$(pod_selector)" -o wide || true
  exit 1
fi

followers=()
for p in "${pods[@]}"; do
  [[ "$p" == "$leader1" ]] && continue
  followers+=("$p")
done

if [[ "${#followers[@]}" -lt 2 ]]; then
  echo "❌ Could not identify 2 followers (leader mapping may differ)."
  echo "Pods: ${pods[*]}"
  echo "Leader guess: ${leader1}"
  exit 1
fi

f1="${followers[0]}"
f2="${followers[1]}"

echo ""
echo "=== Phase A: delete 2 followers; leader should remain ==="
echo "💥 Deleting followers: ${f1} ${f2}"
k delete pod "${f1}" --ignore-not-found=true
k delete pod "${f2}" --ignore-not-found=true

# Ensure holder stays the same leader for a short window
stable_end=$((SECONDS + ${STABILITY_WINDOW_SECONDS:-20}))
while (( SECONDS < stable_end )); do
  cur_holder="$(lease_holder "$lease")"
  cur_leader="$(holder_to_pod_guess "$cur_holder")"
  if [[ -z "$cur_holder" || "$cur_leader" != "$leader1" ]]; then
    echo "❌ Leader changed unexpectedly while only followers were deleted."
    echo "Expected leader=${leader1}, got holderIdentity=${cur_holder}"
    k get pods -l "$(pod_selector)" -o wide || true
    k get lease "$lease" -o yaml || true
    exit 1
  fi
  sleep 2
done
echo "✅ Leader stayed stable while followers were deleted"

# Ensure renewTime still advances
r1="$(lease_renew "$lease")"
r2="$(wait_for_renew_advance "$lease" "$r1" "${RENEW_TIMEOUT_SECONDS:-90}" "${RENEW_POLL_SECONDS:-2}" || true)"
[[ -z "$r2" ]] && { echo "❌ renewTime did not advance under follower churn"; k get lease "$lease" -o yaml || true; exit 1; }
echo "✅ renewTime advanced under follower churn: ${r1} -> ${r2}"

# Wait for pods to be recreated and ready
ensure_replicas
echo "✅ Replicas restored to ${EXPECTED_REPLICAS}"

echo ""
echo "=== Phase B: delete leader + one follower; remaining pod must take over ==="
# Recompute current leader + pods (since names may have changed after recreation)
h1="$(lease_holder "$lease")"
leader1="$(holder_to_pod_guess "$h1")"
pods=($(list_pods))

victims=()
victims+=("$leader1")
for p in "${pods[@]}"; do
  [[ "$p" == "$leader1" ]] && continue
  victims+=("$p")
  break
done

echo "💥 Deleting: ${victims[*]}"
for v in "${victims[@]}"; do
  k delete pod "$v" --ignore-not-found=true
done

echo "⏳ Waiting for a new leader..."
timeout_s="${FAILOVER_TIMEOUT_SECONDS:-180}"
end=$((SECONDS + timeout_s))
new_leader=""

while (( SECONDS < end )); do
  h2="$(lease_holder "$lease")"
  if [[ -n "$h2" ]]; then
    cand="$(holder_to_pod_guess "$h2")"
    # ensure it's a live pod in our selector set
    if k get pod "$cand" >/dev/null 2>&1; then
      # must not be one of the victims
      if [[ " ${victims[*]} " != *" ${cand} "* ]]; then
        new_leader="$cand"
        break
      fi
    fi
  fi
  sleep 2
done

if [[ -z "$new_leader" ]]; then
  echo "❌ No new leader elected within ${timeout_s}s after deleting 2 pods"
  k get pods -l "$(pod_selector)" -o wide || true
  k get lease "$lease" -o yaml || true
  exit 1
fi

echo "✅ New leader elected: ${new_leader}"

# renewTime should advance again after takeover
r1="$(lease_renew "$lease")"
r2="$(wait_for_renew_advance "$lease" "$r1" "${RENEW_TIMEOUT_SECONDS:-90}" "${RENEW_POLL_SECONDS:-2}" || true)"
[[ -z "$r2" ]] && { echo "❌ renewTime did not advance after takeover"; k get lease "$lease" -o yaml || true; exit 1; }
echo "✅ renewTime advanced after takeover: ${r1} -> ${r2}"

echo "🎉 Two-pod disruption test passed"