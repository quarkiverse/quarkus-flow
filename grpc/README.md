# Quarkus Flow :: gRPC

Named gRPC channel support for **Quarkus Flow**.

This module bridges the Serverless Workflow gRPC executor with **Quarkus gRPC clients** so you can route calls through:

```properties
quarkus.grpc.clients.<name>.host=...
quarkus.grpc.clients.<name>.port=...
quarkus.grpc.clients.<name>.plain-text=true
```

and select them per workflow or per task:

```properties
quarkus.flow.grpc.workflow.<workflowName>.name=<clientName>
quarkus.flow.grpc.workflow.<workflowName>.task.<taskName>.name=<clientName>
```

## Dependency

```xml
<dependency>
  <groupId>io.quarkiverse.flow</groupId>
  <artifactId>quarkus-flow-grpc</artifactId>
</dependency>
```

## What it does

When the gRPC executor asks for a channel, this module first checks Quarkus CDI for a named client channel and, if one is configured, returns it. If no route is configured, the SDK falls back to the host/port declared in the workflow definition.

## Example

See [`examples/grpc-client-routing`](../examples/grpc-client-routing/README.md).

## Development Prerequisites

### Protocol Buffers Compiler (protoc)

The gRPC integration tests require the `protoc` (Protocol Buffers compiler) to be installed on your system.

**Error you might see without it:**
```
java.io.IOException: Cannot run program "protoc": error=2, No such file or directory
```

**Installation:**

**macOS (Homebrew):**
```bash
brew install protobuf
```

**Linux (apt):**
```bash
sudo apt-get install -y protobuf-compiler
```

**Linux (yum):**
```bash
sudo yum install -y protobuf-compiler
```

**Windows (Chocolatey):**
```bash
choco install protoc
```

**Verify installation:**
```bash
protoc --version
# Should show: libprotoc 3.x.x or higher
```

**Alternative - Skip gRPC tests:**

If you don't need to run gRPC tests locally, you can skip them:
```bash
# Skip the entire grpc integration tests module
./mvnw test -pl '!grpc/integration-tests'

# Or skip the specific failing test
./mvnw test -Dtest='!GrpcGreetingFlowTest'
```
