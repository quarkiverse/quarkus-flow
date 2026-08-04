# Workflows From JARs

This example shows how a Quarkus Flow application can consume workflow
definitions shipped inside **plain JAR dependencies** — the "workflow library"
pattern: each team publishes its workflows as a versioned Maven artifact, and
an orchestrator application composes them by declaring dependencies. No copying
of files, no `maven-dependency-plugin` unpack tricks.

## Layout

```
workflows-from-jars/
├── payments-workflows/            # Plain JAR: no Quarkus, no Jandex, no beans.xml
│   └── src/main/resources/flow/
│       ├── payment-authorization.yaml   # payments:authorize:1.0.0
│       └── discount.yaml                # payments:discount:1.0.0 (5% standard policy)
├── shipping-workflows/            # A second, independent library
│   └── src/main/resources/flow/
│       └── shipping-quote.yaml          # shipping:quote:1.0.0
└── orchestrator-app/              # Quarkus app that depends on both libraries
    ├── src/main/resources/flow/
    │   └── discount.yaml                # Redefines payments:discount:1.0.0 (10% promo)
    └── src/main/java/org/acme/OrderResource.java
```

The libraries only ship YAML files under the conventional `flow/` directory.
The application declares them as regular Maven dependencies:

```xml
<dependency>
    <groupId>org.acme</groupId>
    <artifactId>payments-workflows</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
<dependency>
    <groupId>org.acme</groupId>
    <artifactId>shipping-workflows</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

That is all it takes. At **build time**, Quarkus Flow scans dependency JARs
containing the configured definitions directory (default `flow/`), validates
the workflows, and generates the same CDI beans it would for application-owned
files:

```java
@Inject
@Identifier("payments:authorize")
Flow authorizePayment;
```

## Application override

`payments-workflows` ships `payments:discount:1.0.0` with a standard 5% policy.
The orchestrator redefines the *same* workflow (same `namespace:name:version`)
in its own `src/main/resources/flow/discount.yaml` with a promotional 10%
policy. The application definition always wins over a dependency-provided one —
useful for customizing a shared workflow locally without forking the library.
The override is logged during augmentation (visible in dev mode and in builds
with build-time logging enabled):

```
Workflow payments:discount:1.0.0: application definition at 'flow/discount.yaml'
overrides dependency-provided definition at 'flow/discount.yaml' (org.acme:payments-workflows)
```

## Why build time matters

- A malformed workflow in a library breaks `mvn package`, not production.
- The same workflow identifier shipped by two *different* dependencies fails
  the build instead of silently picking one by classpath order.
- Native image and container builds work out of the box, since the files are
  registered as classpath resources during the build.

## Running

```bash
# From this directory: builds the libraries, then the app (tests included)
mvn install

# Dev mode
cd orchestrator-app
mvn quarkus:dev
```

Then:

```bash
curl 'localhost:8080/orders/authorize?orderId=42&amount=100'
# {"orderId":"42","status":"AUTHORIZED","fee":2}

curl 'localhost:8080/orders/shipping-quote?weightKg=10'
# {"carrier":"FlowExpress","weightKg":10,"cost":20}

curl 'localhost:8080/orders/discount?amount=100'
# {"policy":"promotional","amount":100,"total":90}   <- the app's 10% policy, not the library's 5%
```

## Opting out

To restrict discovery to the application's own resources:

```properties
quarkus.flow.definitions.scan-dependencies=false
```

See the [Define workflows from YAML files](https://docs.quarkiverse.io/quarkus-flow/dev/workflow-definitions.html)
guide for the full set of precedence rules.
