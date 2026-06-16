# Kubernetes Manifests Summary

Comprehensive Kubernetes deployment manifests for all three Quarkus Flow Runner variants.

## 📁 Files Created

```
runner/app/k8s/
├── README.md                          # Full documentation (architecture, config, troubleshooting)
├── QUICKSTART.md                      # Quick deployment guide for each variant
├── SUMMARY.md                         # This file
├── kustomization.yaml                 # Base kustomize config
├── namespace.yaml                     # Namespace: quarkus-flow
├── postgresql.yaml                    # PostgreSQL (for standard/messaging)
├── kafka.yaml                         # Kafka + Zookeeper (for messaging)
├── runner-minimal.yaml                # Minimal variant deployment
├── runner-standard.yaml               # Standard variant deployment (HA)
├── runner-messaging.yaml              # Messaging variant deployment (HA)
└── overlays/
    ├── minimal/kustomization.yaml     # Kustomize overlay
    ├── standard/kustomization.yaml    # Kustomize overlay
    └── messaging/kustomization.yaml   # Kustomize overlay
```

## 🎯 Design Principles

1. **Minimal overrides** - Only set what's required (DB credentials, Kafka bootstrap), rely on classpath defaults
2. **ConfigMap-based** - Workflows and custom config via ConfigMaps (no PVCs by default)
3. **Production-ready** - RBAC, health probes, resource limits, HA support
4. **durable-kubernetes compliant** - Proper Downward API, RBAC, deployment strategy for lease-based HA

## 📦 Variant Comparison

| Feature | Minimal | Standard | Messaging |
|---------|---------|----------|-----------|
| **Replicas** | 1 | 3+ | 3+ |
| **Persistence** | MVStore (file) | JPA + PostgreSQL | JPA + PostgreSQL |
| **Messaging** | None | None | Kafka |
| **HA** | ❌ | ✅ (lease-based) | ✅ (lease-based) |
| **Dependencies** | None | PostgreSQL | PostgreSQL, Kafka |
| **RBAC** | ❌ | ✅ (Lease management) | ✅ (Lease management) |
| **Use Case** | Dev/test, edge | Production HA | Event-driven HA |

## 🔐 Security

- **Secrets:** Database passwords stored in Kubernetes Secrets
- **RBAC:** ServiceAccount with minimal permissions (Lease management, read-only Pods/Deployments)
- **Non-root:** Container runs as user 185
- **Read-only volumes:** Workflows and config mounted read-only

## ⚙️ Key Configuration

### Standard/Messaging Variants (HA)

**Required Environment Variables:**
```yaml
# Database connection (only these are required)
- name: QUARKUS_DATASOURCE_JDBC_URL
  value: "jdbc:postgresql://postgresql:5432/flowdb"
- name: QUARKUS_DATASOURCE_USERNAME
  value: "flowuser"
- name: QUARKUS_DATASOURCE_PASSWORD
  valueFrom:
    secretKeyRef: ...
      
# Kafka (messaging only)
- name: KAFKA_BOOTSTRAP_SERVERS
  value: "kafka:9092"

# Pod identity for durable-kubernetes (Downward API)
- name: POD_NAME
  valueFrom:
    fieldRef:
      fieldPath: metadata.name
- name: POD_NAMESPACE
  valueFrom:
    fieldRef:
      fieldPath: metadata.namespace
```

**Deployment Strategy:**
```yaml
strategy:
  type: RollingUpdate
  rollingUpdate:
    maxUnavailable: 1  # Kill 1 pod before starting new one (releases Lease)
    maxSurge: 1
```

**RBAC Permissions:**
- `coordination.k8s.io/leases`: get, list, watch, create, update, patch, delete
- `pods`: get, list, watch
- `deployments`, `replicasets`: get, list, watch

### Minimal Variant

**No RBAC required** - Single replica, no lease coordination

**Optional PVC** for MVStore persistence at `/deployments/data`

## 📝 User Customization

### Custom application.properties

```bash
kubectl create configmap runner-custom-config \
  --from-file=application.properties=./my-app.properties \
  -n quarkus-flow
```

Mount to `/deployments/config` - Quarkus auto-loads and overrides classpath defaults.

### Workflows

```bash
# One ConfigMap per workflow
kubectl create configmap workflow-hello \
  --from-file=hello.yaml=./hello.yaml \
  -n quarkus-flow
```

Mount each to `/deployments/workflows/<name>.yaml` using `subPath`.

Can mount multiple ConfigMaps - runner auto-discovers all `.yaml` files.

## 🚀 Quick Deploy

```bash
# Minimal
kubectl apply -k overlays/minimal

# Standard (HA with PostgreSQL)
kubectl apply -k overlays/standard

# Messaging (HA with PostgreSQL + Kafka)
kubectl apply -k overlays/messaging
```

## ✅ Validation Checklist

### Standard/Messaging (HA)

- [ ] PostgreSQL ready: `kubectl wait --for=condition=ready pod -l app=postgresql -n quarkus-flow`
- [ ] Kafka ready (messaging): `kubectl wait --for=condition=ready pod -l app=kafka -n quarkus-flow`
- [ ] Leases created: `kubectl get lease -l io.quarkiverse.flow.durable.k8s/pool=flow-pool -n quarkus-flow`
- [ ] Pods acquired leases: `kubectl exec deployment/quarkus-flow-runner-standard -n quarkus-flow -- curl -s localhost:8080/q/health/ready | jq '.checks[] | select(.name == "Lease Acquired")'`
- [ ] All pods ready: `kubectl get pods -l app=quarkus-flow-runner -n quarkus-flow`

### Minimal

- [ ] Pod ready: `kubectl get pods -l app=quarkus-flow-runner,variant=minimal -n quarkus-flow`
- [ ] Health check: `kubectl exec deployment/quarkus-flow-runner-minimal -n quarkus-flow -- curl -s localhost:8080/q/health`

## 🔍 Troubleshooting

**Pod not Ready (standard/messaging):**
- Check lease acquisition: `kubectl exec <pod> -n quarkus-flow -- curl localhost:8080/q/health/ready`
- View leases: `kubectl get lease -n quarkus-flow`
- Check RBAC: `kubectl auth can-i create leases --as=system:serviceaccount:quarkus-flow:quarkus-flow-runner -n quarkus-flow`

**Database connection failed:**
- Verify PostgreSQL ready: `kubectl get pods -l app=postgresql -n quarkus-flow`
- Test connection: `kubectl run -it --rm psql-test --image=postgres:16-alpine --env="PGPASSWORD=flowpass" -n quarkus-flow -- psql -h postgresql -U flowuser -d flowdb`

**Workflows not loading:**
- Check mounted workflows: `kubectl exec deployment/quarkus-flow-runner-standard -n quarkus-flow -- ls -la /deployments/workflows`
- View logs: `kubectl logs deployment/quarkus-flow-runner-standard -n quarkus-flow | grep -i workflow`

## 📚 References

- [Full Documentation](./README.md) - Complete guide with architecture, config, monitoring
- [Quick Start](./QUICKSTART.md) - Fast deployment commands
- [Quarkus Flow Docs](https://docs.quarkiverse.io/quarkus-flow/dev/)
- [Durable Kubernetes Concept](https://docs.quarkiverse.io/quarkus-flow/dev/concepts-durable-workflow-k8s.html)
