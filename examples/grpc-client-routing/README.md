# gRPC Client Routing Example

This example shows how to call a gRPC service from a Quarkus Flow workflow using a **named Quarkus gRPC client**.

## What it demonstrates

* A `GrpcGreetingFlow` workflow that uses the fluent `grpc()` DSL
* A Quarkus gRPC service running in the same application
* Routing the workflow through `quarkus.grpc.clients.greeter`
* Selecting that client with `quarkus.flow.grpc.workflow.grpcGreeting.name=greeter`

## Key configuration

```properties
quarkus.grpc.server.port=9000
quarkus.grpc.server.plain-text=true

quarkus.grpc.clients.greeter.host=localhost
quarkus.grpc.clients.greeter.port=9000
quarkus.grpc.clients.greeter.plain-text=true

quarkus.flow.grpc.workflow.grpcGreeting.name=greeter
```

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
