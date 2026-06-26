# gRPC Client Routing Example

This example shows how to call a gRPC service from a Quarkus Flow workflow using a **named Quarkus gRPC client**.

## What it demonstrates

* A `GrpcGreetingFlow` workflow that uses the fluent `grpc()` DSL
* A Quarkus gRPC service running in the same application
* Routing the workflow through the default `flowGrpc` Quarkus gRPC client

## Key configuration

```properties
quarkus.grpc.server.port=9000
quarkus.grpc.server.plain-text=true

quarkus.grpc.clients.flowGrpc.host=localhost
quarkus.grpc.clients.flowGrpc.port=9000
quarkus.grpc.clients.flowGrpc.plain-text=true
```

When a client named `flowGrpc` exists, every workflow uses it automatically.

To route a specific workflow or task to a different client, see the
[Channel resolution](https://docs.quarkiverse.io/quarkus-flow/dev/grpc.html#_channel_resolution)
docs for the full priority order and override keys.

## Run it

```bash
./mvnw quarkus:dev
```

Then call:

```bash
curl 'http://localhost:8080/grpc-greeting?name=Quarkus'
```

You should get:

```text
Hello Quarkus
```
