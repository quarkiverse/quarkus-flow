# Quarkus Flow :: Durable Workflows on Kubernetes (Kind)

This example demonstrates **Quarkus Flow Durable Workflows** using **Kubernetes Lease-based coordination** (leader election + failover) via the `quarkus-flow-durable-kubernetes` extension.

It also includes a real-time **Interactive Control Center UI** powered by WebSockets to visualize workflow executions and demonstrate true state persistence across pod restarts.

It is meant as a **hands-on demo** so you can deploy a small app to a local **Kind** cluster, then use the included UI and scripts to verify that:
- a leader is elected using a Kubernetes **Lease**
- the leader keeps renewing the lease
- leadership **fails over** when the leader pod is killed
- the system behaves correctly under multiple pod disruptions
- workflow state is safely persisted and resumed automatically after pod failures

---

## Prerequisites

You’ll need:
- Docker
- Kind
- kubectl
- JDK 17+
- Maven (or use `./mvnw`)

Verify:
```bash
docker --version
kind --version
kubectl version --client
java -version
mvn -version
```

---

## 1) Create a Kind cluster

If you don’t already have a Kind cluster, create one:

```bash
kind create cluster --name quarkus-flow
kubectl cluster-info
```

Make sure your `kubectl` context points to this Kind cluster:
```bash
kubectl config current-context
```

---

## 2) Deploy Redis (State Persistence)

To support durable workflows, **Redis is required** for persisting the workflow state.

Navigate to the example directory and deploy the Redis manifests before deploying the main application:

```bash
cd examples/durable-workflows-k8s
kubectl apply -f manifests/redis.yaml
```

Wait a moment for the Redis pod to be ready:
```bash
kubectl get pods -l app=redis
```

---

## 3) Build + deploy the example

From the **repository root**, build (and deploy) only this module plus its dependencies:

```bash
cd ../.. # back to repo root
./mvnw -pl examples/durable-workflows-k8s -am -Pkind clean package
```

What this does:
- builds the example using the `kind` Maven profile
- generates Kubernetes manifests for Kind
- builds the container image and deploys to the Kind cluster (as configured by the example’s `application.properties`)

Confirm the deployment exists:

```bash
kubectl -n default get deploy durable-flow-demo
kubectl -n default get pods -l app=durable-flow-demo -o wide
```

---

## 4) The Interactive Control Center

This demo includes a real-time UI dashboard to visualize durable workflow execution. It listens natively to the engine's events and streams results via WebSockets.

1. Port-forward the application to access the UI locally:
   ```bash
   kubectl -n default port-forward svc/durable-flow-demo 8080:8080
   ```
2. Open your browser and navigate to: `http://localhost:8080/index.html`
3. **Demo the Durability:**
    - Click **"Fire Async Event"** multiple times to queue up workflows.
    - Quickly kill the Quarkus pods using a separate terminal: `kubectl -n default delete pod -l app=durable-flow-demo`
    - Watch the UI instantly reflect the disconnected state.
    - Once the pods are recreated by the Deployment, the UI will auto-reconnect.
    - Because of Redis state persistence, the interrupted workflows will wake up, resume execution, and dynamically stream their results into the UI table!

---

## 5) Verify Lease coordination using the scripts

The scripts are located in:

```
examples/durable-workflows-k8s/scripts
```

Go to the example directory:

```bash
cd examples/durable-workflows-k8s
```

### 5.1 Verify leader election + renewals

This script ensures you have 3 replicas (it will scale the deployment if needed), then:
- finds the Lease owned by your app
- validates a leader is holding it
- waits until `renewTime` advances

```bash
EXPECTED_REPLICAS=3 ./scripts/verify-lease.sh
```

### 5.2 Verify failover (kill leader)

This script:
- finds the current leader pod (based on the Lease holder identity)
- deletes the leader pod
- waits for another pod to become leader
- verifies `renewTime` continues advancing

```bash
EXPECTED_REPLICAS=3 ./scripts/verify-failover.sh
```

### 5.3 Verify disruption behavior (kill 2 pods)

This script runs two phases:
- delete **two followers** and confirm the leader remains stable
- then delete **leader + one other pod** and confirm takeover still happens

```bash
EXPECTED_REPLICAS=3 ./scripts/verify-two-pod-disruption.sh
```

---

## 6) Inspect the Lease manually (optional)

List Leases:

```bash
kubectl -n default get lease
```

View the Lease YAML:

```bash
kubectl -n default get lease <LEASE_NAME> -o yaml
```

While the cluster is running, you should see:
- `spec.holderIdentity` set to the current leader identity
- `spec.renewTime` periodically advancing while the leader is alive

---

## 7) Useful overrides

All scripts support env var overrides if you deployed with different metadata:

```bash
NAMESPACE=default \
APP_NAME=durable-flow-demo \
APP_PART_OF=quarkus-flow \
APP_VERSION=0.1.0 \
EXPECTED_REPLICAS=3 \
./scripts/verify-lease.sh
```

If you know the exact Lease name (e.g., running in a namespace with many Leases), you can pin it:

```bash
LEASE_NAME=<your-lease-name> EXPECTED_REPLICAS=3 ./scripts/verify-failover.sh
```

---

## Cleanup

Delete the deployment resources (optional):

```bash
kubectl -n default delete deploy durable-flow-demo service durable-flow-demo \
  role durable-flow-demo rolebinding durable-flow-demo serviceaccount durable-flow-demo \
  --ignore-not-found=true
```

Or delete the entire Kind cluster:

```bash
kind delete cluster --name quarkus-flow
```

---

## What this demo proves

This example validates the behavior of the **Quarkus Flow Durable Kubernetes Lease coordination** plugin:

- Multiple replicas can run the same workload.
- Exactly one pod becomes the **leader** and holds a Lease.
- Leadership can **transfer** on pod termination without manual intervention.
- The system remains stable under pod churn.
- **State is completely durable:** Engine state is backed by Redis, surviving total pod annihilation and automatically resuming once pods are restored.

If you’re evaluating durable workflow execution on Kubernetes, this is the recommended quick-start to confirm your cluster setup and observe Lease behavior in practice.