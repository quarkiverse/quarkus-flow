# Kubernetes Manifests for Quarkus Flow Runner

This directory contains Kubernetes manifests for deploying the Quarkus Flow Runner in three variants.

## Important: Maven Resource Filtering

**Do NOT use these manifests directly!** They contain Maven placeholders like `${project.version}`.

Maven automatically processes these manifests during build and outputs the filtered versions to `target/k8s` with actual values substituted.

## How to Use

### 1. Build and Process Manifests

```bash
# From project root
mvn install -pl runner/app -am

# Filtered manifests are now in runner/app/target/k8s
```

### 2. Deploy to Kubernetes

```bash
# Deploy the desired variant using filtered manifests
kubectl apply -k runner/app/target/k8s/overlays/minimal
kubectl apply -k runner/app/target/k8s/overlays/standard
kubectl apply -k runner/app/target/k8s/overlays/messaging
```

## Variants

### minimal
- **Storage**: MVStore (file-based, single PVC)
- **HA**: No (single replica only)
- **Messaging**: No
- **Use case**: Development, testing, simple deployments

### standard
- **Storage**: PostgreSQL (JPA)
- **HA**: Yes (durable-kubernetes with lease-based coordination)
- **Messaging**: No
- **Replicas**: 3 (configurable)
- **Use case**: Production deployments without event streaming

### messaging
- **Storage**: PostgreSQL (JPA)
- **HA**: Yes (durable-kubernetes with lease-based coordination)
- **Messaging**: Yes (Kafka via SmallRye Reactive Messaging)
- **Replicas**: 3 (configurable)
- **Use case**: Production deployments with event-driven workflows

## Version Consistency

The image tags in these manifests use `${project.version}` which Maven replaces with the actual project version:

- **Development**: `quay.io/quarkiverse/quarkus-flow-runner:1.0.0-SNAPSHOT-standard`
- **Release**: `quay.io/quarkiverse/quarkus-flow-runner:1.0.0-standard`

This ensures the manifests always reference the correct image version for the code you're building.

## Testing Locally with Kind

See `runner/app/scripts/test-k8s-locally.sh` for a complete example of building, loading into Kind, and deploying.

```bash
# Quick test with Kind
./runner/app/scripts/test-k8s-locally.sh
```

## Directory Structure

```
src/main/k8s/               ← Source manifests (DO NOT deploy directly)
├── base/                   ← Shared by all variants
│   ├── namespace.yaml
│   ├── example-workflow.yaml
│   └── kustomization.yaml
├── base-durable/           ← Shared by standard + messaging (PostgreSQL + RBAC)
│   ├── postgresql.yaml
│   ├── rbac.yaml
│   └── kustomization.yaml
└── overlays/               ← Variant-specific manifests
    ├── minimal/
    ├── standard/
    └── messaging/

target/k8s/                 ← Filtered manifests (safe to deploy)
└── (same structure, with ${project.version} replaced)
```

## Customization

### Application Properties

Mount a ConfigMap to `/deployments/config` with custom `application.properties`:

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: runner-custom-config
  namespace: quarkus-flow
data:
  application.properties: |
    quarkus.log.level=DEBUG
    quarkus.datasource.jdbc.url=jdbc:postgresql://my-postgres:5432/mydb
```

Then add to the deployment:

```yaml
volumeMounts:
  - name: custom-config
    mountPath: /deployments/config
volumes:
  - name: custom-config
    configMap:
      name: runner-custom-config
```

### Workflows

Mount workflow YAML files to `/deployments/workflows`:

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: my-workflows
  namespace: quarkus-flow
data:
  workflow1.yaml: |
    # Your workflow definition
```

Multiple ConfigMaps can be mounted to the same directory.

## More Information

- [Quarkus Flow Documentation](https://docs.quarkiverse.io/quarkus-flow/dev/)
- [Durable Kubernetes Module](https://docs.quarkiverse.io/quarkus-flow/dev/durable-kubernetes.html)
- [CNCF Serverless Workflow Spec](https://serverlessworkflow.io/)
