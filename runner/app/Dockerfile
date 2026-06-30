####
# Quarkus Flow Runner - Multi-Variant Dockerfile
# ============================================================================
# This Dockerfile builds all Quarkus Flow Runner variants from a single source.
# The VARIANT build argument selects which profile to activate at runtime.
#
# Variants:
#   - minimal:   MVStore persistence, single-replica, ~120MB
#   - standard:  JPA + PostgreSQL + durable-kubernetes, HA-ready, ~280MB
#   - messaging: JPA + PostgreSQL + Kafka + durable-kubernetes, HA-ready, ~330MB
#
# Build:
#   docker build --build-arg VARIANT=minimal -f Dockerfile -t quay.io/quarkiverse/quarkus-flow-runner:1.0.0-minimal .
#   docker build --build-arg VARIANT=standard -f Dockerfile -t quay.io/quarkiverse/quarkus-flow-runner:1.0.0-standard .
#   docker build --build-arg VARIANT=messaging -f Dockerfile -t quay.io/quarkiverse/quarkus-flow-runner:1.0.0-messaging .
#
# Run:
#   docker run -p 8080:8080 quay.io/quarkiverse/quarkus-flow-runner:1.0.0-minimal
#
# Documentation: https://docs.quarkiverse.io/quarkus-flow/dev/runner.html
####

# ============================================================================
# Build Arguments
# ============================================================================
# VARIANT controls which Quarkus profile is activated at runtime
# This must match one of the application-{variant}.properties files
ARG VARIANT=minimal

# ============================================================================
# Base Image - Red Hat UBI 9 with OpenJDK 17
# ============================================================================
FROM registry.access.redhat.com/ubi9/openjdk-17-runtime:1.24-2.1782292635

# Re-declare ARG after FROM to make it available in this stage
ARG VARIANT=minimal

# ============================================================================
# Environment Variables
# ============================================================================
# Locale configuration
ENV LANGUAGE='en_US:en'

# Java runtime options
# - Bind to 0.0.0.0 to accept connections from outside the container
# - Use JBoss LogManager for Quarkus logging
ENV JAVA_OPTIONS="-Dquarkus.http.host=0.0.0.0 -Djava.util.logging.manager=org.jboss.logmanager.LogManager"

# Quarkus application JAR location
ENV JAVA_APP_JAR="/deployments/quarkus-run.jar"

# ============================================================================
# Quarkus Profile Selection
# ============================================================================
# Activates both the container profile (common settings) and variant profile
# - container: /deployments/workflows path, Hibernate auto-update
# - ${VARIANT}: minimal/standard/messaging-specific settings
ENV QUARKUS_PROFILE="container,${VARIANT}"

# ============================================================================
# Filesystem Setup
# ============================================================================
# Switch to root to create directories with proper ownership
USER root

# Create data directory for MVStore persistence (minimal variant)
# Volume mount recommended for production to persist across container restarts
RUN mkdir -p /deployments/data && chown 185:185 /deployments/data

# Create workflows directory for user-provided workflow definitions
# Mount via: -v ./workflows:/deployments/workflows:ro
# Required: Set QUARKUS_FLOW_RUNNER_SOURCE_PATH=/deployments/workflows (default in container profile)
RUN mkdir -p /deployments/workflows && chown 185:185 /deployments/workflows

# Create config directory for optional configuration overrides
# Mount via: -v ./config:/deployments/config:ro
# Quarkus automatically loads config/application.properties if present
RUN mkdir -p /deployments/config && chown 185:185 /deployments/config

# ============================================================================
# Application Files
# ============================================================================
# Copy the Quarkus fast-jar structure (built with Maven)
# Ownership set to user 185 (default non-root user in UBI images)
COPY --chown=185 target/quarkus-app/lib/ /deployments/lib/
COPY --chown=185 target/quarkus-app/*.jar /deployments/
COPY --chown=185 target/quarkus-app/app/ /deployments/app/
COPY --chown=185 target/quarkus-app/quarkus/ /deployments/quarkus/

# ============================================================================
# Runtime Configuration
# ============================================================================
# Expose HTTP port for REST API and dashboard
EXPOSE 8080

# Switch to non-root user for security (user 185 = default in UBI)
USER 185

# Set working directory
WORKDIR /deployments

# ============================================================================
# Entrypoint
# ============================================================================
# Start the Quarkus application
# Profiles activated via QUARKUS_PROFILE env var (set above)
ENTRYPOINT [ "java", "-jar", "/deployments/quarkus-run.jar" ]
