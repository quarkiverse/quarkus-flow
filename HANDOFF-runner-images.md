# Handoff: Runner Image Distribution Design

**Date:** 2026-06-11  
**Session Focus:** Design pre-baked Docker images for Quarkus Flow Runner

## Summary

Designed a comprehensive strategy for distributing pre-built Docker images of the Quarkus Flow Runner, enabling users to deploy workflows without Java/Maven knowledge. The design supports both standard use cases (pull-and-run) and custom builds (single docker command with extension args).

## Related Issues

- **#632** - Runner Image Distribution: Pre-baked Docker images for workflow execution
- **#627** - Persistence deployment module cleanup (PR #636 merged)
- **#629** - durable-kubernetes: Auto-detect Kubernetes environment

## Key Decisions

### 1. Image Strategy: Pre-built + Extensible

**Three pre-built variants** (JVM only for now):
- `quay.io/quarkiverse/quarkus-flow-runner:1.0.0-minimum` (~160MB)
  - MVStore + in-memory messaging
  - Local dev, testing, small production
  
- `quay.io/quarkiverse/quarkus-flow-runner:1.0.0-standard` (~280MB)
  - JPA + MVStore + Redis (all persistence, runtime-selected)
  - PostgreSQL driver (swappable via layering)
  - Production with database or Redis or MVStore
  
- `quay.io/quarkiverse/quarkus-flow-runner:1.0.0-messaging` (~330MB)
  - Everything in standard + Kafka client
  - Event-driven workflows

**Extensible Dockerfile** for custom builds:
- Multi-stage build that compiles `runner/app` with custom extensions
- Users provide `--build-arg PERSISTENCE_EXTENSIONS=...` etc.
- No local Maven/Java needed
- Example: Redis + MySQL + Kafka in single docker build command

### 2. Image Naming Convention

**After discussion with Quarkus CI team:**
- Single repository: `quarkiverse/quarkus-flow-runner`
- Variant in tag: `{version}-{variant}`
- Examples:
  - `quay.io/quarkiverse/quarkus-flow-runner:1.0.0-minimum`
  - `quay.io/quarkiverse/quarkus-flow-runner:1.0.0-standard`
  - `quay.io/quarkiverse/quarkus-flow-runner:1.0.0-messaging`

**Rationale:** Easier registry management, single namespace, clear variant identification.

### 3. Persistence Module Refactoring (PR #636 - Merged)

**Problem:** Original plan to remove deployment modules entirely didn't work because:
- Quarkus extensions require deployment modules (even minimal ones)
- `quarkus-extension-maven-plugin` expects them

**Solution:** Keep deployment modules but strip to bare minimum:
- Only `FeatureBuildItem` registration
- Removed `AdditionalBeanBuildItem` 
- Added `@Unremovable` to runtime beans
- Added Jandex indexing for bean discovery

**Impact on images:**
- Standard image can include JPA + MVStore + Redis simultaneously
- `PersistenceSelector` picks the right one at runtime based on config
- Priority: JPA > Redis > MVStore

### 4. Runtime Persistence Selection

Created `PersistenceSelector` using `Class.forName()` pattern:

```java
@Produces
@ApplicationScoped
public PersistenceInstanceOperations selectPersistence() {
    // Check datasource config -> JPA
    // Check Redis config -> Redis  
    // Fallback -> MVStore
    // Uses isClassPresent() to safely detect available implementations
}
```

**Why Class.forName():** Allows users to customize images by removing JARs without breaking CDI startup.

### 5. Customization Options

**What users CAN do via Docker layering:**
```dockerfile
# Swap JDBC drivers (runtime libs, no build-time discovery)
FROM quay.io/quarkiverse/quarkus-flow-runner:1.0.0-standard
RUN rm /deployments/lib/postgresql-*.jar
COPY mysql-connector.jar /deployments/lib/
```

**What users CANNOT do via layering:**
- Swap persistence modules (CDI beans, build-time augmentation)
- For that, use the extensible Dockerfile

### 6. Auto-Detection (Issue #629)

**Kubernetes detection:**
- Check `KUBERNETES_SERVICE_HOST` env var
- Present → enable durable-kubernetes, lease-based coordination
- Missing → local mode, skip K8s API calls

**Enhancement needed:** Make `DevModeStrategy` smarter:
- Rename to `LocalModeStrategy`
- Skip K8s API when not in cluster (production + no K8s)
- Currently only skips in dev/test mode

### 7. New Module: runner/app

**Structure:**
```
runner/app/
├── pom.xml                    # Templated app with all deps
├── Dockerfile                 # Multi-stage for pre-built variants
├── Dockerfile.extensible      # Multi-stage for custom builds
├── README.md                  # Custom build guide
└── src/main/
    ├── java/.../PersistenceSelector.java
    └── resources/application.properties
```

**Maven profiles:**
- `image-minimum` - excludes PostgreSQL, Redis, JPA
- `image-standard` - includes all persistence + PostgreSQL
- `image-messaging` - includes everything + Kafka

### 8. CI/CD Strategy

**GitHub Actions workflow:**
```yaml
jobs:
  build:
    strategy:
      matrix:
        variant: [minimum, standard, messaging]
    steps:
      # 1. Maven build with profile: ./mvnw package -P image-{variant}
      # 2. Docker build from variant-specific target/
      # 3. Upload artifact
  
  publish:
    uses: quarkiverse/.github/.github/workflows/deploy-image.yml@main
```

**Total builds per release:** 3 images (3 variants, JVM only)

### 9. Extensible Dockerfile Design

**Example usage:**
```bash
docker build \
  --build-arg PERSISTENCE_EXTENSIONS="quarkus-flow-persistence-redis,quarkus-redis-client" \
  --build-arg JDBC_DRIVER="quarkus-jdbc-mysql" \
  --build-arg MESSAGING_EXTENSIONS="quarkus-messaging-kafka" \
  -t my-runner:redis-mysql-kafka \
  -f runner/app/Dockerfile.extensible \
  https://github.com/quarkiverse/quarkus-flow.git#main
```

**Pre-defined extension lists** will be documented for common combinations:
- Redis + MySQL + Kafka
- Infinispan + PostgreSQL + AMQP
- JPA + MariaDB + in-memory

## Implementation Status

**Completed:**
- ✅ Design finalized and documented in issue #632
- ✅ Persistence module cleanup (PR #636 merged)
- ✅ Issue #629 created for K8s auto-detection
- ✅ Create `runner/app/` module skeleton
  - ✅ pom.xml with image-minimal, image-standard, image-messaging profiles
  - ✅ image-minimal: MVStore only (no messaging, no durable-kubernetes)
  - ✅ image-standard/messaging: Include durable-kubernetes for HA
  - ✅ application.properties with container profile for production paths
  - ✅ README.md documenting usage, HA architecture, and MVStore limitations
  - ✅ Basic smoke test (RunnerAppSmokeTest.java) with unique DB paths
  - ✅ Event emission test (EmitEventWorkflowTest.java) with MessagingProfile
  - ✅ Test workflow (emit-event.yaml)

**Blocked - Requires Persistence Selection Enhancement:**
- ⚠️ `PersistenceSelector.java` - CANNOT implement until CDI ambiguity is resolved
- ⚠️ image-standard profile - Currently causes CDI errors
- ⚠️ image-messaging profile - Currently causes CDI errors

**Pending:**
- [ ] Create issue for runtime persistence selection mechanism
- [ ] Create issue for messaging runtime config support (see Critical Discovery #2)
- [ ] Create `Dockerfile` (pre-built variants)
- [ ] Create `Dockerfile.extensible` (custom builds)
- [ ] Create GitHub Actions workflow
- [ ] Create Helm chart
- [ ] Documentation in `docs/modules/ROOT/pages/runner-images.adoc`

**Issues Created:**
- [x] Issue #645 - Messaging module runtime config support

## Key Files to Review

- **Issue #632:** Complete design specification
  - https://github.com/quarkiverse/quarkus-flow/issues/632
  
- **Issue #627:** Persistence module cleanup rationale
  - https://github.com/quarkiverse/quarkus-flow/issues/627
  
- **Issue #629:** K8s auto-detection requirements
  - https://github.com/quarkiverse/quarkus-flow/issues/629

- **PR #636:** Actual persistence module changes (merged)
  - Kept deployment modules, stripped to feature flags only
  - Added `@Unremovable` to runtime beans
  - Added Jandex indexing

## Design Principles Applied

1. **User experience first:** Pull-and-run for common cases, single docker command for custom
2. **No local toolchain required:** Users don't need Java/Maven installed
3. **Strict API contracts:** Async execution always returns 202 with null output
4. **Runtime flexibility:** Multiple persistence options in one image, selected by config
5. **Safe customization:** JDBC drivers via layering, persistence via extensible build
6. **Auto-detection:** Works in both local Docker and Kubernetes without config changes

## Technical Debt / Future Work

- Native images (add `-native` tag variants)
- Additional pre-built variants (MySQL-specific, AMQP-specific)
- Multi-architecture builds (arm64)
- Instance query endpoint for async execution status polling
- Hot-reload of workflow definitions (currently requires restart)

## Critical Discovery #1: Persistence Layer Limitation

**Problem:** During implementation, we discovered that the current persistence layer architecture **does not support multiple persistence modules in the classpath**.

**Root Cause:**
- Each persistence module (`jpa`, `redis`, `mvstore`) produces a `PersistenceInstanceHandlers` CDI bean
- `FlowPersistenceApplicationBuilderCustomizer` (in `persistence/common`) expects exactly ONE handler
- Multiple handlers cause CDI `AmbiguousResolutionException` at startup

**Impact on Runner Images:**
- ✅ `image-minimal` profile works (single persistence: MVStore)
- ❌ `image-standard` profile fails (JPA + MVStore + Redis)
- ❌ `image-messaging` profile fails (JPA + MVStore + Redis + Kafka)

**Required Fix (Separate Issue):**
1. Add `@Typed` qualifier or use `Instance<PersistenceInstanceHandlers>` with selection logic
2. Implement runtime persistence selection via config property (e.g., `quarkus.flow.persistence.type=jpa|redis|mvstore`)
3. Update each persistence module's producer to conditionally register based on config
4. Possibly use Quarkus `@IfBuildProperty` at build time for static selection

**Workaround for This Release:**
- Publish only `image-minimal` variant (MVStore + in-memory messaging)
- Document standard/messaging as "coming soon" after persistence selection is implemented
- Users can build custom images with `Dockerfile.extensible` if they need specific persistence

## Critical Discovery #2: Messaging Module Build-Time Config Detection

**Problem:** The `FlowMessagingProcessor` uses build-time configuration to detect messaging channels, preventing runtime/profile-based configuration.

**Root Cause:**
```java
// In FlowMessagingProcessor.registerDefaults()
Optional<String> flowOut = ConfigProvider.getConfig()
    .getOptionalValue("mp.messaging.outgoing.flow-out.connector", String.class);
if (flowOut.isPresent()) {
    builder.addBeanClass(FlowDomainEventsPublisher.class);
}
```

This reads config **at build time**, so:
- ❌ Profile-specific configs invisible: `%minimal.mp.messaging.outgoing.flow-out.connector`
- ❌ Environment variables not detected at build time
- ❌ Different configs per environment require separate builds

**Impact on Runner Images:**
- Cannot use profile-based messaging configuration
- Must configure channels **without profile prefixes** in `application.properties`
- Limits flexibility for multi-environment Docker deployments

**Example - What Doesn't Work:**
```properties
%minimal.mp.messaging.outgoing.flow-out.connector=smallrye-in-memory  # Not detected!
```

**Current Workaround:**
```properties
# Must be at root level (no profile prefix) for build-time detection
mp.messaging.outgoing.flow-out.connector=smallrye-in-memory
```

**Required Fix (Issue Created):**
See `.github/ISSUE_TEMPLATE/messaging-runtime-config.md`

Recommended approach: Register messaging beans **unconditionally** and handle missing config gracefully at runtime, rather than conditional registration at build time.

**Tests Affected:**
- `EmitEventWorkflowTest` works because we moved config to root level
- Users trying to use `%prod`, `%dev`, `%test` profiles for messaging will encounter issues

## Concrete Artifacts for Implementation

### Dockerfile (Pre-built Variants)

**Location:** `runner/app/Dockerfile`

```dockerfile
# Multi-stage Dockerfile for pre-built variants
ARG VARIANT=standard

FROM registry.access.redhat.com/ubi9/openjdk-17-runtime:1.20 AS base
WORKDIR /deployments

FROM base AS minimum
COPY --chown=185 target/quarkus-app/lib/ /deployments/lib/
COPY --chown=185 target/quarkus-app/*.jar /deployments/
COPY --chown=185 target/quarkus-app/app/ /deployments/app/
COPY --chown=185 target/quarkus-app/quarkus/ /deployments/quarkus/

FROM base AS standard
COPY --chown=185 target/quarkus-app/lib/ /deployments/lib/
COPY --chown=185 target/quarkus-app/*.jar /deployments/
COPY --chown=185 target/quarkus-app/app/ /deployments/app/
COPY --chown=185 target/quarkus-app/quarkus/ /deployments/quarkus/

FROM base AS messaging
COPY --chown=185 target/quarkus-app/lib/ /deployments/lib/
COPY --chown=185 target/quarkus-app/*.jar /deployments/
COPY --chown=185 target/quarkus-app/app/ /deployments/app/
COPY --chown=185 target/quarkus-app/quarkus/ /deployments/quarkus/

FROM ${VARIANT}
ENV LANGUAGE='en_US:en'
EXPOSE 8080
USER 185
ENTRYPOINT ["java", "-jar", "/deployments/quarkus-run.jar"]
```

**Build flow per variant:**
1. `./mvnw clean package -pl runner/app -am -P image-{variant} -DskipTests`
2. `docker build --build-arg VARIANT={variant} -t runner-{variant}:jvm .`

### Dockerfile.extensible (Custom Builds)

**Location:** `runner/app/Dockerfile.extensible`

```dockerfile
###########################################
# Stage 1: Build runner/app with extensions
###########################################
ARG EXTENSIONS=""
ARG PERSISTENCE_EXTENSIONS=""
ARG MESSAGING_EXTENSIONS=""
ARG JDBC_DRIVER=""

FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /build

# Copy runner/app module
COPY runner/app/pom.xml ./pom.xml
COPY runner/app/src ./src

# Copy parent POMs for dependency resolution
COPY pom.xml /workspace/pom.xml
COPY runner/pom.xml /workspace/runner/pom.xml

# Install parent POMs
RUN cd /workspace && mvn install -N -DskipTests
RUN cd /workspace/runner && mvn install -N -DskipTests

# Add custom extensions
ARG PERSISTENCE_EXTENSIONS
ARG MESSAGING_EXTENSIONS  
ARG JDBC_DRIVER
ARG EXTENSIONS

RUN if [ -n "$PERSISTENCE_EXTENSIONS" ]; then \
      ./mvnw quarkus:add-extension -Dextensions="$PERSISTENCE_EXTENSIONS"; \
    fi

RUN if [ -n "$MESSAGING_EXTENSIONS" ]; then \
      ./mvnw quarkus:add-extension -Dextensions="$MESSAGING_EXTENSIONS"; \
    fi

RUN if [ -n "$JDBC_DRIVER" ]; then \
      ./mvnw quarkus:add-extension -Dextensions="$JDBC_DRIVER"; \
    fi

RUN if [ -n "$EXTENSIONS" ]; then \
      ./mvnw quarkus:add-extension -Dextensions="$EXTENSIONS"; \
    fi

# Build the application
RUN ./mvnw clean package -DskipTests

###########################################
# Stage 2: Runtime image
###########################################
FROM registry.access.redhat.com/ubi9/openjdk-17-runtime:1.20
WORKDIR /deployments

COPY --chown=185 --from=builder /build/target/quarkus-app/lib/ ./lib/
COPY --chown=185 --from=builder /build/target/quarkus-app/*.jar ./
COPY --chown=185 --from=builder /build/target/quarkus-app/app/ ./app/
COPY --chown=185 --from=builder /build/target/quarkus-app/quarkus/ ./quarkus/

ENV LANGUAGE='en_US:en'
EXPOSE 8080
USER 185
ENTRYPOINT ["java", "-jar", "/deployments/quarkus-run.jar"]
```

**Example usage:**
```bash
docker build \
  --build-arg PERSISTENCE_EXTENSIONS="quarkus-flow-persistence-redis,quarkus-redis-client" \
  --build-arg JDBC_DRIVER="quarkus-jdbc-mysql" \
  --build-arg MESSAGING_EXTENSIONS="quarkus-messaging-kafka" \
  -t my-runner:redis-mysql-kafka \
  -f runner/app/Dockerfile.extensible .
```

### Maven Profiles for runner/app

**Location:** `runner/app/pom.xml`

```xml
<profiles>
    <!-- Minimum variant: MVStore + in-memory messaging only -->
    <profile>
        <id>image-minimum</id>
        <dependencies>
            <!-- Core -->
            <dependency>
                <groupId>io.quarkiverse.flow</groupId>
                <artifactId>quarkus-flow-runner</artifactId>
            </dependency>
            
            <!-- Persistence: MVStore only -->
            <dependency>
                <groupId>io.quarkiverse.flow</groupId>
                <artifactId>quarkus-flow-persistence-mvstore</artifactId>
            </dependency>
            
            <!-- Messaging: In-memory only -->
            <dependency>
                <groupId>io.quarkiverse.flow</groupId>
                <artifactId>quarkus-flow-messaging</artifactId>
            </dependency>
            <dependency>
                <groupId>io.smallrye.reactive</groupId>
                <artifactId>smallrye-reactive-messaging-in-memory</artifactId>
            </dependency>
        </dependencies>
    </profile>
    
    <!-- Standard variant: All persistence options + PostgreSQL -->
    <profile>
        <id>image-standard</id>
        <dependencies>
            <!-- Core -->
            <dependency>
                <groupId>io.quarkiverse.flow</groupId>
                <artifactId>quarkus-flow-runner</artifactId>
            </dependency>
            
            <!-- Persistence: All options (runtime-selected) -->
            <dependency>
                <groupId>io.quarkiverse.flow</groupId>
                <artifactId>quarkus-flow-persistence-jpa</artifactId>
            </dependency>
            <dependency>
                <groupId>io.quarkiverse.flow</groupId>
                <artifactId>quarkus-flow-persistence-mvstore</artifactId>
            </dependency>
            <dependency>
                <groupId>io.quarkiverse.flow</groupId>
                <artifactId>quarkus-flow-persistence-redis</artifactId>
            </dependency>
            
            <!-- JDBC: PostgreSQL (swappable via layering) -->
            <dependency>
                <groupId>io.quarkus</groupId>
                <artifactId>quarkus-jdbc-postgresql</artifactId>
            </dependency>
            
            <!-- Messaging: In-memory only -->
            <dependency>
                <groupId>io.quarkiverse.flow</groupId>
                <artifactId>quarkus-flow-messaging</artifactId>
            </dependency>
            <dependency>
                <groupId>io.smallrye.reactive</groupId>
                <artifactId>smallrye-reactive-messaging-in-memory</artifactId>
            </dependency>
        </dependencies>
    </profile>
    
    <!-- Messaging variant: Everything in standard + Kafka -->
    <profile>
        <id>image-messaging</id>
        <dependencies>
            <!-- Core -->
            <dependency>
                <groupId>io.quarkiverse.flow</groupId>
                <artifactId>quarkus-flow-runner</artifactId>
            </dependency>
            
            <!-- Persistence: All options (runtime-selected) -->
            <dependency>
                <groupId>io.quarkiverse.flow</groupId>
                <artifactId>quarkus-flow-persistence-jpa</artifactId>
            </dependency>
            <dependency>
                <groupId>io.quarkiverse.flow</groupId>
                <artifactId>quarkus-flow-persistence-mvstore</artifactId>
            </dependency>
            <dependency>
                <groupId>io.quarkiverse.flow</groupId>
                <artifactId>quarkus-flow-persistence-redis</artifactId>
            </dependency>
            
            <!-- JDBC: PostgreSQL (swappable via layering) -->
            <dependency>
                <groupId>io.quarkus</groupId>
                <artifactId>quarkus-jdbc-postgresql</artifactId>
            </dependency>
            
            <!-- Messaging: In-memory + Kafka -->
            <dependency>
                <groupId>io.quarkiverse.flow</groupId>
                <artifactId>quarkus-flow-messaging</artifactId>
            </dependency>
            <dependency>
                <groupId>io.smallrye.reactive</groupId>
                <artifactId>smallrye-reactive-messaging-in-memory</artifactId>
            </dependency>
            <dependency>
                <groupId>io.quarkus</groupId>
                <artifactId>quarkus-messaging-kafka</artifactId>
            </dependency>
        </dependencies>
    </profile>
</profiles>
```

### PersistenceSelector Implementation

**Location:** `runner/app/src/main/java/io/quarkiverse/flow/runner/app/PersistenceSelector.java`

```java
package io.quarkiverse.flow.runner.app;

import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.quarkiverse.flow.persistence.PersistenceInstanceOperations;
import io.quarkiverse.flow.persistence.jpa.JpaInstanceOperations;
import io.quarkiverse.flow.persistence.mvstore.MVStoreInstanceOperations;
import io.quarkiverse.flow.persistence.redis.RedisInstanceStore;
import io.quarkus.arc.Unremovable;

import jakarta.enterprise.inject.spi.CDI;

/**
 * Selects the appropriate persistence implementation at runtime based on configuration.
 * 
 * Priority: JPA > Redis > MVStore
 * 
 * - If quarkus.datasource.jdbc.url is configured -> JPA
 * - Else if quarkus.redis.hosts is configured -> Redis
 * - Else -> MVStore (fallback)
 */
@ApplicationScoped
public class PersistenceSelector {
    
    @Inject
    @ConfigProperty(name = "quarkus.datasource.jdbc.url")
    Optional<String> datasourceUrl;
    
    @Inject
    @ConfigProperty(name = "quarkus.redis.hosts")
    Optional<String> redisHosts;
    
    @Produces
    @ApplicationScoped
    @Unremovable
    public PersistenceInstanceOperations selectPersistence() {
        boolean hasDatasource = datasourceUrl.isPresent() && !datasourceUrl.get().isBlank();
        boolean hasRedis = redisHosts.isPresent() && !redisHosts.get().isBlank();
        
        // Priority 1: JPA if datasource is configured and class is on classpath
        if (hasDatasource && isClassPresent("io.quarkiverse.flow.persistence.jpa.JpaInstanceOperations")) {
            return CDI.current().select(JpaInstanceOperations.class).get();
        }
        
        // Priority 2: Redis if configured and class is on classpath
        if (hasRedis && isClassPresent("io.quarkiverse.flow.persistence.redis.RedisInstanceStore")) {
            return CDI.current().select(RedisInstanceStore.class).get();
        }
        
        // Priority 3: MVStore fallback
        if (isClassPresent("io.quarkiverse.flow.persistence.mvstore.MVStoreInstanceOperations")) {
            return CDI.current().select(MVStoreInstanceOperations.class).get();
        }
        
        throw new IllegalStateException("No persistence implementation found on classpath");
    }
    
    private boolean isClassPresent(String className) {
        try {
            Class.forName(className);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
```

## Notes for Next Session

- `runner/app/pom.xml` dependencies need all persistence options for standard image
- `PersistenceSelector` priority order: JPA > Redis > MVStore (document why)
- Extensible Dockerfile needs parent POM installation in builder stage
- Helm chart should default to `variant: standard` for production readiness
- CI workflow should run integration tests for each variant before publishing

## Questions Still Open

None - design is finalized and approved in issue #632.
