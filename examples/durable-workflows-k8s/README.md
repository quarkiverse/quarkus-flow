# Durable Kubernetes Lease Demo (Quarkus Flow)

This example demonstrates:
- a simple Quarkus Flow workflow with 3 tasks
- Kubernetes Lease acquisition (member/leader) from the durable-kubernetes module
- binding the acquired lease name to the WorkflowApplication ID

## Prereqs
- Docker
- kubectl
- kind

## 1) Create a local cluster
kind create cluster --name flow --config kind/kind.yaml

## 2) Build the app
mvn clean package

## 3) Build the container image (Dockerfile)
docker build -f src/main/docker/Dockerfile.jvm -t quarkus-flow-lease-demo:latest .

## 4) Load the image into kind
kind load docker-image quarkus-flow-lease-demo:latest --name flow

## 5) Deploy (RBAC + app)
kubectl apply -f k8s/00-rbac.yaml
kubectl apply -f k8s/10-deployment.yaml
kubectl apply -f k8s/20-service.yaml

## 6) Observe leases
kubectl get lease

You should see leases following your naming pattern, e.g.:
- flow-pool-leader-<pool>
- flow-pool-member-<pool>-00 ...

## 7) Call the endpoints
kubectl port-forward svc/flow-lease-demo 8080:8080

# Run the workflow
curl -s http://localhost:8080/demo/run | jq .

# Check pod + lease + WorkflowApplication ID
curl -s http://localhost:8080/debug/id | jq .

## 8) Verify across replicas
# hit the service multiple times; you should see different pods/leases answering
for i in {1..10}; do curl -s http://localhost:8080/debug/id; echo; done
