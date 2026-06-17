#!/bin/bash
set -e

# Example script to run the Flow Runner with a workflow mounted

IMAGE="quay.io/quarkiverse/quarkus-flow-runner:latest-minimal"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
WORKFLOW_DIR="${SCRIPT_DIR}/../workflows"

echo "🚀 Starting Flow Runner with example workflow..."
echo "📂 Mounting workflows from: ${WORKFLOW_DIR}"
echo ""

# Clean up any existing container with the same name
docker stop flow-runner-example 2>/dev/null || true
docker rm flow-runner-example 2>/dev/null || true

docker run -d \
  --name flow-runner-example \
  --rm \
  -p 8080:8080 \
  -v "${WORKFLOW_DIR}:/deployments/workflows:ro" \
  "${IMAGE}"

echo "✅ Container started!"
echo ""
echo "⏳ Waiting for application to be ready..."
sleep 5

# Wait for health check
for i in {1..30}; do
  if curl -sf http://localhost:8080/q/health/ready > /dev/null 2>&1; then
    echo "✅ Application is ready!"
    break
  fi
  echo -n "."
  sleep 1
done

echo ""
echo "📋 Available workflows:"
curl -s http://localhost:8080/q/flow/definitions | jq -r '.[] | "  - \(.namespace)/\(.name)/\(.version)"'

echo ""
echo "🧪 Test the workflow:"
echo '  curl -X POST http://localhost:8080/q/flow/exec/examples/hello-world/1.0.0 \'
echo '    -H "Content-Type: application/json" \'
echo '    -d '"'"'{"name": "World"}'"'"' | jq'

echo ""
echo "📊 View OpenAPI docs:"
echo "  http://localhost:8080/q/swagger-ui"

echo ""
echo "🛑 Stop the container:"
echo "  docker stop flow-runner-example && docker rm flow-runner-example"
