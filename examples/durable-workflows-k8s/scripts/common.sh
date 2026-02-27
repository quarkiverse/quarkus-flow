#!/usr/bin/env bash
set -euo pipefail

need() { command -v "$1" >/dev/null 2>&1 || { echo "❌ Missing '$1' in PATH"; exit 1; }; }

# Safe defaults under `set -u`
: "${NAMESPACE:=default}"
: "${APP_NAME:=durable-flow-demo}"
: "${APP_PART_OF:=quarkus-flow}"
: "${APP_VERSION:=0.1.0}"
: "${EXPECTED_REPLICAS:=3}"
: "${FLOW_POOL_NAME:=durable-flow}"
: "${ROLLOUT_TIMEOUT:=240s}"

# Single canonical name used by kubectl for the Deployment
: "${DEPLOYMENT_NAME:=${APP_NAME}}"

k() { kubectl -n "$NAMESPACE" "$@"; }

pod_selector() {
  echo "app.kubernetes.io/name=${APP_NAME},app.kubernetes.io/part-of=${APP_PART_OF},app.kubernetes.io/version=${APP_VERSION}"
}

list_pods() {
  k get pods -l "$(pod_selector)" -o jsonpath='{range .items[*]}{.metadata.name}{"\n"}{end}'
}

count_ready_pods() {
  k get pods -l "$(pod_selector)" \
    -o jsonpath='{range .items[*]}{.status.conditions[?(@.type=="Ready")].status}{"\n"}{end}' \
    | grep -c '^True$' || true
}

wait_rollout() {
  echo "⏳ Waiting for rollout: deployment/${DEPLOYMENT_NAME} (ns=${NAMESPACE})"
  k rollout status "deployment/${DEPLOYMENT_NAME}" --timeout="${ROLLOUT_TIMEOUT}"
}

ensure_replicas() {
  local current
  current="$(k get deploy "${DEPLOYMENT_NAME}" -o jsonpath='{.spec.replicas}' 2>/dev/null || true)"
  if [[ -z "${current}" ]]; then
    echo "❌ deployment/${DEPLOYMENT_NAME} not found in ns=${NAMESPACE}"
    k get deploy || true
    exit 1
  fi

  if [[ "${current}" != "${EXPECTED_REPLICAS}" ]]; then
    echo "ℹ️ scaling deployment/${DEPLOYMENT_NAME} from ${current} -> ${EXPECTED_REPLICAS}"
    k scale "deployment/${DEPLOYMENT_NAME}" --replicas="${EXPECTED_REPLICAS}"
  fi

  wait_rollout
}

lease_jsonpath() {
  local lease="$1" path="$2"
  k get lease "$lease" -o "jsonpath=${path}" 2>/dev/null || true
}

lease_holder() { lease_jsonpath "$1" '{.spec.holderIdentity}'; }
lease_renew()  { lease_jsonpath "$1" '{.spec.renewTime}'; }

holder_to_pod_guess() {
  local holder="$1"
  echo "${holder}" | awk -F'[_:]' '{print $1}'
}

find_leader_lease() {
  if [[ -n "${LEASE_NAME:-}" ]]; then
    echo "${LEASE_NAME}"
    return 0
  fi

  local pods leases lease holder pod_guess
  pods="$(list_pods | tr '\n' ' ')"
  [[ -z "${pods// }" ]] && { echo ""; return 0; }

  leases="$(k get lease -o jsonpath='{range .items[*]}{.metadata.name}{"\n"}{end}' 2>/dev/null || true)"
  [[ -z "${leases}" ]] && { echo ""; return 0; }

  while IFS= read -r lease; do
    holder="$(lease_holder "$lease")"
    [[ -z "$holder" ]] && continue
    pod_guess="$(holder_to_pod_guess "$holder")"
    if [[ " ${pods} " == *" ${pod_guess} "* ]]; then
      echo "$lease"
      return 0
    fi
  done <<< "${leases}"

  # fallback hints
  echo "${leases}" | grep -i "${FLOW_POOL_NAME}" | head -n 1 || true
}

wait_for_renew_advance() {
  local lease="$1"
  local prev="$2"
  local timeout_s="${3:-90}"
  local poll_s="${4:-2}"

  local end=$((SECONDS + timeout_s))
  local cur=""

  while (( SECONDS < end )); do
    cur="$(lease_renew "$lease")"
    if [[ -n "$cur" && -n "$prev" && "$cur" != "$prev" ]]; then
      echo "$cur"
      return 0
    fi
    sleep "$poll_s"
  done

  echo ""
  return 1
}