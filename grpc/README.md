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
