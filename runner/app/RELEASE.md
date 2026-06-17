# Releasing Runner Docker Images

This document describes how to release the Quarkus Flow Runner Docker images to Quay.io.

## Prerequisites

Before releasing Docker images, ensure:

1. ✅ **Maven artifacts released to Maven Central**
   - The runner images depend on released artifacts
   - Version in `pom.xml` must NOT be a SNAPSHOT
   - All dependencies must be available in Maven Central
   - **CRITICAL**: The workflow uses `mvn install -pl runner/app` (NO `-am` flag)
   - This means dependencies are **pulled from Maven Central**, NOT built from source
   - If dependencies are not in Maven Central, the build will FAIL (this is intentional)

2. ✅ **Git release tag pushed**
   - Tag format: `1.0.0` (semantic version, no `v` prefix)
   - Tag must match the version in `pom.xml`
   - Example: `git tag 1.0.0 && git push origin 1.0.0`

3. ✅ **Repository secrets configured**
   - `QUAY_USERNAME` - Quay.io username (usually a robot account)
   - `QUAY_ROBOT_TOKEN` - Quay.io authentication token

## Release Process

### Option 1: Official Release (Automatic - Recommended)

Push a release tag and the workflow triggers automatically:

```bash
# Ensure pom.xml version is 1.0.0 (not 1.0.0-SNAPSHOT)
mvn versions:set -DnewVersion=1.0.0 -DgenerateBackupPoms=false

# Commit the version change
git add pom.xml
git commit -m "Release 1.0.0"

# Create and push tag
git tag 1.0.0
git push origin main
git push origin 1.0.0

# GitHub Actions automatically builds and publishes images
```

**Validation (Strict for tag pushes):**
- ✅ Version must match tag exactly (`1.0.0`)
- ❌ SNAPSHOT versions are **rejected**
- ✅ Builds from Maven Central only

The workflow will:
1. Checkout code at the release tag
2. Verify version matches tag and is not a SNAPSHOT
3. Build dependencies from Maven Central
4. Build all 3 variants (minimal, standard, messaging)
5. Push images to Quay.io with version tag and `latest-*` tags

### Option 2: Test/Patch Build (Manual)

Trigger the workflow manually to publish test builds or patches for users:

1. Go to: https://github.com/quarkiverse/quarkus-flow/actions/workflows/publish-runner-images.yml
2. Click "Run workflow"
3. Select branch (e.g., `main` for latest, or a feature branch)
4. Enter tag/ref (optional):
   - Leave empty to build from selected branch
   - Or enter specific tag/commit (e.g., `1.0.0`, `main`, `feature-branch`)
5. Click "Run workflow"

**Validation (Flexible for manual builds):**
- ✅ SNAPSHOT versions are **allowed** (e.g., `1.0.0-SNAPSHOT`)
- ✅ Can build from any branch/commit
- ⚠️ Still requires dependencies in Maven Central

**Use cases:**
- **Test builds for users**: Publish `1.0.0-SNAPSHOT-standard` from `main` for testing a patch
- **Re-publishing**: Re-run for an existing release after Maven Central sync completes
- **Branch builds**: Test a feature branch before merging

## Release Workflow Details

The workflow (`.github/workflows/publish-runner-images.yml`) performs these steps:

### 1. Version Verification
- Checks tag matches `pom.xml` version
- Ensures version is NOT a SNAPSHOT
- Fails fast if version mismatch

### 2. Build from Maven Central
- Builds with `mvn install -pl runner/app` (NO `-am` flag)
- **Does NOT build dependencies from source** - pulls from Maven Central
- Dependencies MUST be available in Maven Central or build will FAIL
- This ensures images use stable, released artifacts only
- Uses Maven cache for faster builds

### 3. Docker Build (Matrix Strategy)
Builds all 3 variants in parallel:
- **minimal**: `make build-minimal`
- **standard**: `make build-standard`
- **messaging**: `make build-messaging`

### 4. Image Publishing
For each variant, publishes two tags:
- Version tag: `quay.io/quarkiverse/quarkus-flow-runner:1.0.0-standard`
- Latest tag: `quay.io/quarkiverse/quarkus-flow-runner:latest-standard`

## Published Images

After successful release, images are available at:

- **Minimal**: `quay.io/quarkiverse/quarkus-flow-runner:${VERSION}-minimal`
- **Standard**: `quay.io/quarkiverse/quarkus-flow-runner:${VERSION}-standard`
- **Messaging**: `quay.io/quarkiverse/quarkus-flow-runner:${VERSION}-messaging`

Latest tags always point to the most recent release:
- `latest-minimal`
- `latest-standard`
- `latest-messaging`

## Verifying the Release

### Official Release
After the workflow completes:

```bash
# Pull the official release image
docker pull quay.io/quarkiverse/quarkus-flow-runner:1.0.0-standard

# Or pull latest
docker pull quay.io/quarkiverse/quarkus-flow-runner:latest-standard

# Check version
docker run --rm quay.io/quarkiverse/quarkus-flow-runner:1.0.0-standard \
  java -jar /deployments/quarkus-run.jar --version

# Test with Kubernetes
kubectl apply -k https://github.com/quarkiverse/quarkus-flow/runner/app/target/k8s/overlays/standard?ref=1.0.0
```

### Test/Snapshot Build
After a manual build from `main`:

```bash
# Pull the snapshot image (version from pom.xml)
docker pull quay.io/quarkiverse/quarkus-flow-runner:1.0.0-SNAPSHOT-standard

# Or use the latest tag (points to most recent build)
docker pull quay.io/quarkiverse/quarkus-flow-runner:latest-standard

# Test locally
docker run --rm -p 8080:8080 quay.io/quarkiverse/quarkus-flow-runner:1.0.0-SNAPSHOT-standard
```

## Troubleshooting

### "Tag does not match pom.xml version"

The tag must exactly match the version in `pom.xml`:

```bash
# Check pom.xml version
mvn help:evaluate -Dexpression=project.version -q -DforceStdout -pl runner/app

# If mismatch, update version and retag
mvn versions:set -DnewVersion=1.0.0 -DgenerateBackupPoms=false
git add pom.xml
git commit --amend -m "Release 1.0.0"
git tag -d 1.0.0
git tag 1.0.0
git push origin main --force
git push origin 1.0.0 --force
```

### "Cannot release SNAPSHOT version"

SNAPSHOT versions cannot be released:

```bash
# Remove -SNAPSHOT suffix
mvn versions:set -DnewVersion=1.0.0 -DgenerateBackupPoms=false
git add pom.xml
git commit -m "Release 1.0.0"
git tag 1.0.0
git push origin main
git push origin 1.0.0
```

### "Dependency not found in Maven Central"

The workflow uses `mvn install -pl runner/app` (NO `-am` flag), which means:
- Dependencies are **pulled from Maven Central**, NOT built from source
- This is **intentional** to ensure reproducible builds with released artifacts

If you see dependency resolution errors:

1. **Wait for Maven Central sync** (can take 2-4 hours after Sonatype release)
2. **Check sync status**: https://repo1.maven.org/maven2/io/quarkiverse/flow/
   - Look for your release version under each module
   - Example: `io/quarkiverse/flow/quarkus-flow-runner/1.0.0/`
3. **Verify all modules are synced**:
   ```bash
   # Check if all modules are available
   VERSION=1.0.0
   curl -I https://repo1.maven.org/maven2/io/quarkiverse/flow/quarkus-flow-runner/${VERSION}/quarkus-flow-runner-${VERSION}.pom
   curl -I https://repo1.maven.org/maven2/io/quarkiverse/flow/quarkus-flow-durable-kubernetes/${VERSION}/quarkus-flow-durable-kubernetes-${VERSION}.pom
   # etc.
   ```
4. **Re-run the workflow manually** after sync completes

**DO NOT add `-am` flag** to "fix" this - that would build from source and defeat the purpose of using released artifacts.

### "Login to quay.io failed"

Check repository secrets are configured:

1. Go to: https://github.com/quarkiverse/quarkus-flow/settings/secrets/actions
2. Verify `QUAY_USERNAME` and `QUAY_ROBOT_TOKEN` exist
3. Generate new robot token if needed: https://quay.io/organization/quarkiverse?tab=robots

## Post-Release

After images are published:

1. **Update documentation** with the new version
2. **Test deployment** using the release tag
3. **Announce** on GitHub Discussions/Twitter/etc.
4. **Bump version** to next SNAPSHOT for development:
   ```bash
   mvn versions:set -DnewVersion=1.1.0-SNAPSHOT -DgenerateBackupPoms=false
   git add pom.xml
   git commit -m "Prepare for next development iteration"
   git push origin main
   ```

## Release Cadence

Quarkus Flow follows semantic versioning:
- **Major** (X.0.0): Breaking changes, major features
- **Minor** (1.X.0): New features, backwards compatible
- **Patch** (1.0.X): Bug fixes, no new features

Runner images are released when:
- Core Quarkus Flow libraries are released
- Runner-specific features are added
- Security vulnerabilities are patched

## Related Workflows

- **Durable K8s Kind Verification** (`.github/workflows/durable-k8s-kind.yml`)
  - Runs on every PR/push
  - Tests runner standard variant in Kind cluster
  - Ensures deployment manifests work correctly

- **Publish Runner Images** (`.github/workflows/publish-runner-images.yml`)
  - Runs on release tag push or manual trigger
  - Publishes images to Quay.io
  - Uses released Maven artifacts only
  - **Note**: Currently uses 3 separate build jobs due to Quarkiverse reusable workflow limitation
  - **Future**: Will use matrix strategy once `artifact_name` parameter is added to Quarkiverse deploy-image.yml
