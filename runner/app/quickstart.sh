#!/bin/bash
# Quarkus Flow Quickstart Script
# Usage: curl -fsSL https://raw.githubusercontent.com/quarkiverse/quarkus-flow/main/runner/app/quickstart.sh | bash

{ # This ensures the entire script is downloaded before execution

set -e

IMAGE="quay.io/quarkiverse/quarkus-flow-runner:latest-minimal"
PORT=8080
WORKFLOW_DIR="${HOME}/.quarkus-flow-quickstart/workflows"

echo "=================================================="
echo "  Quarkus Flow Quickstart"
echo "  Experience serverless workflows in seconds!"
echo "=================================================="
echo ""

# Check prerequisites
echo "Checking prerequisites..."

if ! command -v docker &> /dev/null; then
  echo "ERROR: Docker is not installed."
  echo "Please install Docker first: https://docs.docker.com/get-docker/"
  exit 1
fi

if ! docker info &> /dev/null; then
  echo "ERROR: Docker daemon is not running."
  exit 1
fi

echo "✓ Docker is ready"

# Check if port is available
if lsof -Pi :${PORT} -sTCP:LISTEN -t >/dev/null 2>&1 ; then
  echo "ERROR: Port ${PORT} is already in use."
  echo "Please free up port ${PORT} or stop the running container."
  exit 1
fi

echo "✓ Port ${PORT} is available"
echo ""

# Create workflow directory with example workflows
echo "Creating example workflows in ${WORKFLOW_DIR}..."
mkdir -p "${WORKFLOW_DIR}"

cat > "${WORKFLOW_DIR}/hello-world.yaml" << 'EOF'
document:
  dsl: '1.0.0'
  namespace: quickstart
  name: hello-world
  version: '1.0.0'
do:
  - greet:
      set:
        message: '${ "Hello, " + .name + "! Welcome to Quarkus Flow!" }'
EOF

cat > "${WORKFLOW_DIR}/math-calculator.yaml" << 'EOF'
document:
  dsl: '1.0.0'
  namespace: quickstart
  name: math-calculator
  version: '1.0.0'
do:
  - calculate:
      set:
        sum: '${ .a + .b }'
        difference: '${ .a - .b }'
        product: '${ .a * .b }'
        quotient: '${ .a / .b }'
EOF

echo "✓ Example workflows created"
echo ""

# Clean up any existing container
docker stop quarkus-flow-quickstart 2>/dev/null && echo "Stopped existing container" || true
docker rm quarkus-flow-quickstart 2>/dev/null || true

# Pull the image
echo "Pulling Quarkus Flow Runner image (this may take a minute)..."
docker pull "${IMAGE}" > /dev/null 2>&1
echo "✓ Image ready"
echo ""

# Start the container in the background temporarily for demo
echo "Starting Quarkus Flow Runner..."
CONTAINER_ID=$(docker run -d --name quarkus-flow-quickstart --rm \
  -p ${PORT}:8080 \
  -v "${WORKFLOW_DIR}:/deployments/workflows:ro" \
  "${IMAGE}")

echo "✓ Container started"
echo ""

# Wait for application to be ready
echo "Waiting for application to be ready..."
for i in {1..60}; do
  if curl -sf http://localhost:${PORT}/q/health/ready > /dev/null 2>&1; then
    echo "✓ Application is ready!"
    break
  fi
  echo -n "."
  sleep 1
  if [ $i -eq 60 ]; then
    echo ""
    echo "ERROR: Timeout waiting for application to start"
    echo "Check logs with: docker logs ${CONTAINER_ID}"
    docker stop ${CONTAINER_ID} 2>/dev/null || true
    exit 1
  fi
done

echo ""
echo "=================================================="
echo "  Quarkus Flow is running!"
echo "=================================================="
echo ""

# Show available workflows
echo "Available Workflows:"
if command -v jq &> /dev/null; then
  curl -s http://localhost:${PORT}/q/flow/definitions | jq -r '.[] | "  - \(.namespace)/\(.name)/\(.version)"'
else
  echo "  - quickstart/hello-world/1.0.0"
  echo "  - quickstart/math-calculator/1.0.0"
fi

echo ""
echo "=================================================="
echo "  Trying the workflows..."
echo "=================================================="
echo ""

# Example 1
echo "Example 1: Hello World"
echo "Command:"
echo "  curl -X POST \"http://localhost:${PORT}/q/flow/exec/quickstart/hello-world/1.0.0?wait=true\" \\"
echo "    -H 'Content-Type: application/json' \\"
echo "    -d '{\"name\": \"Alice\"}'"
echo ""
echo "Result:"
if command -v jq &> /dev/null; then
  curl -s -X POST "http://localhost:${PORT}/q/flow/exec/quickstart/hello-world/1.0.0?wait=true" \
    -H 'Content-Type: application/json' \
    -d '{"name": "Alice"}' | jq
else
  curl -s -X POST "http://localhost:${PORT}/q/flow/exec/quickstart/hello-world/1.0.0?wait=true" \
    -H 'Content-Type: application/json' \
    -d '{"name": "Alice"}'
fi

echo ""
echo "Example 2: Math Calculator"
echo "Command:"
echo "  curl -X POST \"http://localhost:${PORT}/q/flow/exec/quickstart/math-calculator/1.0.0?wait=true\" \\"
echo "    -H 'Content-Type: application/json' \\"
echo "    -d '{\"a\": 42, \"b\": 8}'"
echo ""
echo "Result:"
if command -v jq &> /dev/null; then
  curl -s -X POST "http://localhost:${PORT}/q/flow/exec/quickstart/math-calculator/1.0.0?wait=true" \
    -H 'Content-Type: application/json' \
    -d '{"a": 42, "b": 8}' | jq
else
  curl -s -X POST "http://localhost:${PORT}/q/flow/exec/quickstart/math-calculator/1.0.0?wait=true" \
    -H 'Content-Type: application/json' \
    -d '{"a": 42, "b": 8}'
fi

echo ""
echo "=================================================="
echo "  Useful Resources"
echo "=================================================="
echo ""
echo "  Dashboard:        http://localhost:${PORT}"
echo "  Swagger UI:       http://localhost:${PORT}/q/swagger-ui"
echo "  Health Check:     http://localhost:${PORT}/q/health"
echo "  Metrics:          http://localhost:${PORT}/q/metrics"
echo ""
echo "  Documentation:    https://docs.quarkiverse.io/quarkus-flow/dev/"
echo "  Examples:         https://github.com/quarkiverse/quarkus-flow/tree/main/examples"
echo "  GitHub:           https://github.com/quarkiverse/quarkus-flow"
echo ""

echo "=================================================="
echo "  Next Steps"
echo "=================================================="
echo ""
echo "1. View the dashboard:"
echo "   Open http://localhost:${PORT} in your browser"
echo ""
echo "2. Create your own workflow:"
echo "   Add YAML files to: ${WORKFLOW_DIR}"
echo "   Note: Restart the container to load new/modified workflows"
echo ""
echo "3. Run with your own workflows directory:"
echo ""
echo "   docker run --rm \\"
echo "     -p 8080:8080 \\"
echo "     -v /path/to/your/workflows:/deployments/workflows:ro \\"
echo "     ${IMAGE}"
echo ""
echo "4. Explore production variants:"
echo "   - PostgreSQL + HA:  quay.io/quarkiverse/quarkus-flow-runner:latest-standard"
echo "   - Kafka messaging:  quay.io/quarkiverse/quarkus-flow-runner:latest-messaging"
echo ""
echo "5. Build with Quarkus:"
echo "   Integrate Quarkus Flow into your Java application"
echo "   https://docs.quarkiverse.io/quarkus-flow/dev/"
echo ""

echo "=================================================="
echo "  Container Management"
echo "=================================================="
echo ""
echo "The demo container is running in the background."
echo ""
echo "To stop and remove it:"
echo "  docker stop quarkus-flow-quickstart"
echo ""
echo "To run interactively in foreground (Ctrl+C to stop):"
echo "  docker stop quarkus-flow-quickstart"
echo "  docker run --rm \\"
echo "    -p 8080:8080 \\"
echo "    -v ${WORKFLOW_DIR}:/deployments/workflows:ro \\"
echo "    ${IMAGE}"
echo ""
echo "Happy workflow orchestration!"
echo ""

} # End of script - ensures entire script downloads before execution
