# Kubernetes Quick Start Guide

Choose your deployment variant based on your needs:

## 📦 Minimal - Dev/Test/Edge

Single replica, file-based persistence, no external dependencies.

```bash
kubectl apply -k runner/app/k8s/overlays/minimal

# Access dashboard
kubectl port-forward -n quarkus-flow svc/quarkus-flow-runner-minimal 8080:8080
```

**Features:**
- ✅ MVStore persistence (file-based)
- ✅ PVC for data persistence across restarts
- ✅ Example workflow included (hello-world)
- ✅ No external dependencies
- ❌ No HA (single replica only)
- ❌ No messaging

**When to use:** Dev/test environments, edge deployments, single-node production.

**Try the example workflow:**
```bash
curl -X POST http://localhost:8080/examples/hello-world \
  -H "Content-Type: application/json" \
  -d '{"name": "Kubernetes"}'
```

---

## 🏢 Standard - Production HA

Multi-replica with PostgreSQL and automatic failover.

```bash
# From the project root:
kubectl apply -k runner/app/k8s/overlays/standard

# Wait for PostgreSQL
kubectl wait --for=condition=ready pod -l app=postgresql -n quarkus-flow --timeout=120s

# Access dashboard
kubectl port-forward -n quarkus-flow svc/quarkus-flow-runner-standard 8080:8080
```

**Features:**
- ✅ JPA + PostgreSQL (shared database)
- ✅ 3 replicas with automatic failover
- ✅ Lease-based HA coordination
- ✅ Kubernetes RBAC for lease management
- ❌ No messaging

**When to use:** Production deployments requiring high availability.

---

## 🚀 Messaging - Event-Driven HA

Multi-replica with PostgreSQL, Kafka, and automatic failover.

```bash
# From the project root:
kubectl apply -k runner/app/k8s/overlays/messaging

# Wait for dependencies
kubectl wait --for=condition=ready pod -l app=postgresql -n quarkus-flow --timeout=120s
kubectl wait --for=condition=ready pod -l app=kafka -n quarkus-flow --timeout=120s

# Access dashboard
kubectl port-forward -n quarkus-flow svc/quarkus-flow-runner-messaging 8080:8080
```

**Features:**
- ✅ JPA + PostgreSQL (shared database)
- ✅ Kafka for event-driven workflows
- ✅ 3 replicas with automatic failover
- ✅ Lease-based HA coordination
- ✅ Kubernetes RBAC for lease management

**When to use:** Event-driven production deployments with Kafka integration.

---

## 📝 Adding Your Workflows

An example workflow (`hello-world`) is automatically included via kustomize's `configMapGenerator`. To add your own workflows:

### Option 1: Using Kustomize (Recommended)

Edit the `kustomization.yaml` overlay to add your workflows:

```yaml
# overlays/standard/kustomization.yaml
configMapGenerator:
  - name: workflow-example
    files:
      - example-workflow.yaml=../../../workflows/example-workflow.yaml
  # Add your workflows here:
  - name: workflow-myapp
    files:
      - myworkflow.yaml=./my-workflows/myworkflow.yaml
```

Then update the deployment volumes and volumeMounts sections in `runner-standard.yaml` to mount the new ConfigMap.

### Option 2: Manual kubectl

```bash
# Create ConfigMap for your workflow
kubectl create configmap workflow-myapp \
  --from-file=myworkflow.yaml=./my-workflows/myworkflow.yaml \
  -n quarkus-flow

# Patch deployment to mount it
kubectl patch deployment quarkus-flow-runner-standard -n quarkus-flow -p '
spec:
  template:
    spec:
      containers:
      - name: runner
        volumeMounts:
        - name: workflow-myapp
          mountPath: /deployments/workflows/myworkflow.yaml
          subPath: myworkflow.yaml
          readOnly: true
      volumes:
      - name: workflow-myapp
        configMap:
          name: workflow-myapp
'

# Restart to pick up changes
kubectl rollout restart deployment/quarkus-flow-runner-standard -n quarkus-flow
```

---

## ⚙️ Custom Configuration

### Override application.properties

```bash
# Create your custom application.properties
cat > application.properties <<EOF
quarkus.log.level=DEBUG
quarkus.flow.runner.source-path=/deployments/workflows
# Add your custom properties
EOF

# Create ConfigMap
kubectl create configmap custom-config \
  --from-file=application.properties \
  -n quarkus-flow

# Mount to /deployments/config (already configured to auto-load)
kubectl patch deployment quarkus-flow-runner-standard -n quarkus-flow -p '
spec:
  template:
    spec:
      containers:
      - name: runner
        volumeMounts:
        - name: custom-config
          mountPath: /deployments/config
          readOnly: true
      volumes:
      - name: custom-config
        configMap:
          name: custom-config
'
```

Quarkus automatically loads `/deployments/config/application.properties` when present.

---

## 🔐 Using Custom Database Credentials

### Update PostgreSQL credentials

```bash
# Create new PostgreSQL secret
kubectl create secret generic postgresql-secret \
  --from-literal=POSTGRES_USER=myuser \
  --from-literal=POSTGRES_PASSWORD=mypassword \
  --from-literal=POSTGRES_DB=mydb \
  --dry-run=client -o yaml | kubectl apply -f - -n quarkus-flow

# Update runner secret to match
kubectl create secret generic runner-standard-secret \
  --from-literal=QUARKUS_DATASOURCE_PASSWORD=mypassword \
  --dry-run=client -o yaml | kubectl apply -f - -n quarkus-flow

# Restart deployments to pick up new secrets
kubectl rollout restart deployment/postgresql -n quarkus-flow
kubectl rollout restart deployment/quarkus-flow-runner-standard -n quarkus-flow
```

---

## 🔍 Monitoring & Debugging

### View logs
```bash
kubectl logs -f deployment/quarkus-flow-runner-standard -n quarkus-flow
```

### Check health
```bash
kubectl exec -it deployment/quarkus-flow-runner-standard -n quarkus-flow -- \
  curl http://localhost:8080/q/health
```

### View metrics
```bash
kubectl port-forward -n quarkus-flow svc/quarkus-flow-runner-standard 8080:8080
curl http://localhost:8080/q/metrics
```

### Test database connection
```bash
kubectl run -it --rm psql-test \
  --image=postgres:16-alpine \
  --env="PGPASSWORD=flowpass" \
  -n quarkus-flow \
  -- psql -h postgresql -U flowuser -d flowdb
```

### Check HA leases (Standard/Messaging)
```bash
# List active leases
kubectl get leases -n quarkus-flow

# Watch lease changes
kubectl get leases -n quarkus-flow -w
```

---

## 📊 Scaling

### Minimal variant
Cannot scale beyond 1 replica (MVStore limitation).

### Standard/Messaging variants
```bash
# Scale to 5 replicas
kubectl scale deployment quarkus-flow-runner-standard --replicas=5 -n quarkus-flow

# Each replica acquires a unique lease and processes sharded workflows
```

---

## 🧹 Cleanup

```bash
# Delete specific variant
kubectl delete -k overlays/minimal
kubectl delete -k overlays/standard
kubectl delete -k overlays/messaging

# Or delete entire namespace
kubectl delete namespace quarkus-flow
```

---

## 🆘 Troubleshooting

### Pod not starting
```bash
kubectl describe pod -l app=quarkus-flow-runner -n quarkus-flow
kubectl logs -l app=quarkus-flow-runner -n quarkus-flow --all-containers
```

### Database connection failed
- Check PostgreSQL is ready: `kubectl get pods -l app=postgresql -n quarkus-flow`
- Verify credentials match in both secrets: `postgresql-secret` and `runner-*-secret`
- Test connection manually (see "Test database connection" above)

### Workflows not loading
```bash
# Check mounted workflows
kubectl exec deployment/quarkus-flow-runner-standard -n quarkus-flow -- \
  ls -la /deployments/workflows
```

### HA failover not working
```bash
# Verify RBAC permissions
kubectl auth can-i create leases \
  --as=system:serviceaccount:quarkus-flow:quarkus-flow-runner \
  -n quarkus-flow

# Should return "yes"
```

---

## 📚 Next Steps

- [Full Documentation](./README.md)
- [Quarkus Flow Docs](https://docs.quarkiverse.io/quarkus-flow/dev/)
- [Workflow Examples](../../examples/)
