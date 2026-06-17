#!/bin/bash
set -e

# Script to test the runner standard variant in Kind locally
# Following the same steps as .github/workflows/durable-k8s-kind.yml

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="${SCRIPT_DIR}/../../.."

echo "📂 Project root: ${PROJECT_ROOT}"
cd "${PROJECT_ROOT}"

# Colors
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

step() {
    echo -e "\n${BLUE}==>${NC} ${GREEN}$1${NC}\n"
}

step "1. Create Kind cluster"
if kind get clusters | grep -q "^quarkus-flow-test$"; then
    echo "⚠️  Cluster 'quarkus-flow-test' already exists. Delete it? (y/n)"
    read -r answer
    if [ "$answer" = "y" ]; then
        kind delete cluster --name quarkus-flow-test
        kind create cluster --name quarkus-flow-test
    fi
else
    kind create cluster --name quarkus-flow-test
fi

step "2. Cluster sanity check"
kubectl cluster-info --context kind-quarkus-flow-test
kubectl get nodes -o wide

step "3. Build dependencies and process k8s manifests (Maven filtering)"
mvn -DskipTests -Dquarkus.log.level=OFF -Dquarkus.langchain4j.ollama.devservices.enabled=false -ntp \
    -pl runner/app -am install
# Maven filtering replaces ${project.version} in manifests

step "4. Build runner standard image"
cd runner/app
make build-standard
cd "${PROJECT_ROOT}"

step "5. Get project version and load image into Kind"
VERSION=$(mvn help:evaluate -Dexpression=project.version -q -DforceStdout -pl runner/app)
echo "📌 Version: ${VERSION}"
kind load docker-image quay.io/quarkiverse/quarkus-flow-runner:"${VERSION}"-standard --name quarkus-flow-test

step "6. Deploy runner standard to Kind (includes namespace + PostgreSQL + RBAC)"
# Use filtered manifests from target/k8s (${project.version} replaced by Maven)
kubectl apply -k runner/app/target/k8s/overlays/standard

step "7. Wait for PostgreSQL to be ready"
kubectl wait --for=condition=Ready pod -l app=postgresql --timeout=120s -n quarkus-flow

step "8. Wait for deployment"
kubectl -n quarkus-flow rollout status deployment/quarkus-flow-runner-standard --timeout=240s
kubectl -n quarkus-flow get pods -l app=quarkus-flow-runner,variant=standard -o wide
kubectl -n quarkus-flow get lease -o wide || true

echo -e "\n${YELLOW}Waiting 10 seconds for leases to stabilize...${NC}\n"
sleep 10

step "9. Verify lease renew"
cd examples/durable-workflows-k8s
NAMESPACE=quarkus-flow \
APP_NAME=quarkus-flow-runner-standard \
APP_PART_OF=quarkus-flow-runner \
APP_VERSION=standard \
EXPECTED_REPLICAS=3 \
FLOW_POOL_NAME=flow-runner-standard \
RENEW_TIMEOUT_SECONDS=120 \
FAILOVER_TIMEOUT_SECONDS=180 \
DEPLOYMENT_NAME=quarkus-flow-runner-standard \
./scripts/verify-lease.sh

step "10. Verify failover"
NAMESPACE=quarkus-flow \
APP_NAME=quarkus-flow-runner-standard \
APP_PART_OF=quarkus-flow-runner \
APP_VERSION=standard \
EXPECTED_REPLICAS=3 \
FLOW_POOL_NAME=flow-runner-standard \
RENEW_TIMEOUT_SECONDS=120 \
FAILOVER_TIMEOUT_SECONDS=180 \
DEPLOYMENT_NAME=quarkus-flow-runner-standard \
./scripts/verify-failover.sh

step "11. Verify two-pod disruption"
NAMESPACE=quarkus-flow \
APP_NAME=quarkus-flow-runner-standard \
APP_PART_OF=quarkus-flow-runner \
APP_VERSION=standard \
EXPECTED_REPLICAS=3 \
FLOW_POOL_NAME=flow-runner-standard \
RENEW_TIMEOUT_SECONDS=120 \
FAILOVER_TIMEOUT_SECONDS=240 \
STABILITY_WINDOW_SECONDS=20 \
DEPLOYMENT_NAME=quarkus-flow-runner-standard \
./scripts/verify-two-pod-disruption.sh

cd "${PROJECT_ROOT}"

echo -e "\n${GREEN}✅ All tests passed!${NC}\n"
echo "🔍 To inspect the cluster:"
echo "  kubectl -n quarkus-flow get pods"
echo "  kubectl -n quarkus-flow get lease"
echo "  kubectl port-forward -n quarkus-flow svc/quarkus-flow-runner-standard 8080:8080"
echo ""
echo "🧹 To clean up:"
echo "  kind delete cluster --name quarkus-flow-test"
