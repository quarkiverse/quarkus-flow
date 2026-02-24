# Quarkus Flow :: Durable Workflows on Kubernetes (Kind)

This example demonstrates **Quarkus Flow Durable Workflows** using **Kubernetes Lease-based coordination** (leader election + failover) via the `quarkus-flow-durable-kubernetes` extension.

It is meant as a **hands-on demo** so you can deploy a small app to a local **Kind** cluster, then use the included scripts to verify that:
- a leader is elected using a Kubernetes **Lease**
- the leader keeps renewing the lease
- leadership **fails over** when the leader pod is killed
- the system behaves correctly under multiple pod disruptions

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

## 2) Build + deploy the example

This example lives under:

```
examples/durable-workflows-k8s
```

From the **repository root**, build (and deploy) only this module plus its dependencies:

```bash
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

## 3) Verify Lease coordination using the scripts

The scripts are located in:

```
examples/durable-workflows-k8s/scripts
```

Go to the example directory:

```bash
cd examples/durable-workflows-k8s
```

### 3.1 Verify leader election + renewals

This script ensures you have 3 replicas (it will scale the deployment if needed), then:
- finds the Lease owned by your app
- validates a leader is holding it
- waits until `renewTime` advances

```bash
EXPECTED_REPLICAS=3 ./scripts/verify-lease.sh
```

### 3.2 Verify failover (kill leader)

This script:
- finds the current leader pod (based on the Lease holder identity)
- deletes the leader pod
- waits for another pod to become leader
- verifies `renewTime` continues advancing

```bash
EXPECTED_REPLICAS=3 ./scripts/verify-failover.sh
```

### 3.3 Verify disruption behavior (kill 2 pods)

This script runs two phases:
- delete **two followers** and confirm the leader remains stable
- then delete **leader + one other pod** and confirm takeover still happens

```bash
EXPECTED_REPLICAS=3 ./scripts/verify-two-pod-disruption.sh
```

---

## 4) Inspect the Lease manually (optional)

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

## 5) Useful overrides

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

If you’re evaluating durable workflow execution on Kubernetes, this is the recommended quick-start to confirm your cluster setup and observe Lease behavior in practice.
