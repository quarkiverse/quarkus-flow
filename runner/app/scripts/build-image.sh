#!/bin/bash
set -e

# Build script for Quarkus Flow Runner images
# Can be run from anywhere - automatically finds project root
# Usage: ./build-image.sh [variant]
#   variant: minimal (default), standard, messaging

VARIANT="${1:-minimal}"

# Find project root (where pom.xml exists)
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="${SCRIPT_DIR}/../../.."

# Verify we found the right directory
if [ ! -f "${PROJECT_ROOT}/pom.xml" ]; then
    echo "❌ Error: Could not find project root (pom.xml not found)"
    echo "   Script location: ${SCRIPT_DIR}"
    echo "   Expected root: ${PROJECT_ROOT}"
    exit 1
fi

echo "📂 Project root: ${PROJECT_ROOT}"
echo "🔨 Building runner app with Maven (${VARIANT} profile)..."
cd "${PROJECT_ROOT}"

# Extract Maven project version
VERSION=$(mvn help:evaluate -Dexpression=project.version -q -DforceStdout -pl runner/app)

echo "📌 Project version: ${VERSION}"

# Quarkus resolves extension deployment artifacts from the reactor model, but -am
# follows Maven dependency edges only. Select the deployment modules for this image.
case "${VARIANT}" in
    minimal)
        DEPLOYMENT_MODULES="runner/deployment,persistence/mvstore/deployment,oidc/deployment,scheduler/memory/deployment"
        ;;
    standard)
        DEPLOYMENT_MODULES="runner/deployment,persistence/jpa/deployment,durable-kubernetes/deployment,oidc/deployment,scheduler/quartz/deployment,scheduler/db-migration-quartz/deployment"
        ;;
    messaging)
        DEPLOYMENT_MODULES="runner/deployment,persistence/jpa/deployment,durable-kubernetes/deployment,messaging/deployment,oidc/deployment,scheduler/quartz/deployment,scheduler/db-migration-quartz/deployment"
        ;;
    *)
        echo "❌ Unknown variant: ${VARIANT}"
        exit 1
        ;;
esac

IMAGE_TAG="quay.io/quarkiverse/quarkus-flow-runner:${VERSION}-${VARIANT}"

# Build with variant profile and container,variant Quarkus profiles
mvn clean package -pl "${DEPLOYMENT_MODULES},runner/app" -am -P "image-${VARIANT}" -DskipTests -Dquarkus.profile="container,${VARIANT}"

echo ""
echo "📦 Building Docker image: ${IMAGE_TAG}..."
cd "${PROJECT_ROOT}/runner/app"
docker build --build-arg VARIANT="${VARIANT}" -f Dockerfile -t "${IMAGE_TAG}" .

# Also tag as 'latest' variant
docker tag "${IMAGE_TAG}" "quay.io/quarkiverse/quarkus-flow-runner:latest-${VARIANT}"

echo ""
echo "✅ Image built successfully!"
echo ""
echo "📊 Image details:"
docker images "quay.io/quarkiverse/quarkus-flow-runner" --filter "reference=*${VARIANT}"

echo ""
echo "🚀 To run the image:"
echo "   docker run -p 8080:8080 ${IMAGE_TAG}"
echo "   # or"
echo "   docker run -p 8080:8080 quay.io/quarkiverse/quarkus-flow-runner:latest-${VARIANT}"
echo ""
echo "🔍 To test the image:"
echo "   curl http://localhost:8080/q/health/ready"
echo "   curl http://localhost:8080/q/openapi"
