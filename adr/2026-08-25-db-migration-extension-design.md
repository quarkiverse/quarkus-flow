# Flyway-Based Schema Migration Extensions (quarkus-flow-db-migration)

**Status:** Proposed
**Date:** 2026-08-27
**Deciders:** Quarkus Flow Core Team

## Context

Quarkus Flow has two schema-bearing persistence concerns, and neither has a production-safe, versioned migration path:

**JPA runtime persistence** (`persistence/jpa`) maps workflow execution state to five entities — `WorkflowInstanceEntity`, `TaskInfoEntity`, `CloudEventEntity`, `CompletedTaskEntity`, `RetriedTaskEntity`. The only schema-management mechanism today is Hibernate `quarkus.hibernate-orm.database.generation=update`. The persistence guide already recommends Flyway for production ("prefer managing the schema explicitly with a migration tool such as Flyway") and documents hand-written SQL for five database kinds, but none of this is packaged or shipped — users must copy the docs' SQL into their own app.

**Quartz scheduler persistence** (`scheduler/quartz`) is further along: `quartz/runtime` already depends on `quarkus-flyway`/`quarkus-flyway-deployment` and ships a real script, `db/migration/V2.0.0__QuarkusQuartzTasks.sql`, creating the eleven `QRTZ_*` tables. But activation is entirely opt-in and manual — nothing in `FlowQuartzProcessor` registers a build item for it, and the only place `quarkus.flyway.migrate-at-start=true` is actually set is the module's own integration-tests `application.properties`. A consuming application gets no guidance or default; it must know to enable Flyway itself. That same config also sets `quarkus.flyway.table=flyway_quarkus_history` — a deliberate choice to keep the framework's own schema history separate from whatever Flyway history an application's business schema might use. That precedent is the right one to generalize.

More broadly, environments that deploy Quarkus Flow-based applications through an external orchestrator — a Kubernetes operator, a CI/CD pipeline, any tool that manages rollout as a distinct step from application startup — need to apply schema migrations exactly once, before application instances start, then report success or failure cleanly. Today there is nothing in Quarkus Flow such an orchestrator could run to do that: no packaged migration scripts for the JPA runtime schema, and no artifact that applies migrations and exits without booting the full workflow engine and HTTP listener.

## Problem

Concretely, a user building a production app on Quarkus Flow today faces:

1. **No supported path to a safe schema.** The only mechanism (`database.generation=update`) is explicitly a dev/test convenience in Hibernate's own documentation, not a production strategy — it never removes columns or tightens constraints, and offers no rollback. The persistence guide already tells users to use Flyway instead, but hands them raw SQL to copy-paste rather than something installable.
2. **No versioning story.** Nothing ties a schema version to a Quarkus Flow release. A user upgrading the framework has no way to know whether their database needs a change, short of diffing entity classes by hand.
3. **Inconsistent experience between JPA and Quartz.** Quartz already ships a migration script, but silently — a user has to discover `quarkus.flyway.migrate-at-start` themselves; JPA persistence doesn't even have that option today, packaged or not.
4. **Multi-replica races.** Any deployment running more than one pod against `update` risks concurrent DDL attempts against the same database, with no coordination mechanism.
5. **No headless invocation.** There is no artifact that applies migrations and exits — which blocks any external orchestrator (a CI/CD pipeline, a Kubernetes Job-based deployment tool, a Kubernetes operator) from owning migration timing independently of the application's own startup sequence.

## Industry Practice

Two patterns dominate schema migration for services deployed to Kubernetes, and Flyway supports both:

- **Migration coupled to application boot** — `migrate-at-start`, or an init container sharing the application image. Simple, and correct for a single-instance or developer deployment. It breaks down under replication: concurrent pods race on DDL, a slow migration can exceed the pod's start-up probe budget and trigger restart loops, and a failed migration surfaces as a crash-looping application rather than a clear "migration failed" signal.
- **Migration as a distinct, release-gated step** — a short-lived Job (or pipeline step, or operator-driven Job) that runs to completion *before* application pods roll out. This is the widely recommended shape for replicated production services: it gives a single well-defined execution, isolated logs, a clean exit code to gate the rollout on, an explicit point to decide rollback, and the option to run migrations under a higher-privilege database credential that the runtime never holds.

Flyway's `baseline-on-migrate` is the standard mechanism for adopting Flyway on a database that already has a schema (here, one previously managed by Hibernate `update`). Guidance across the ecosystem is consistent: use it deliberately and only for that onboarding case, take a backup first, and rehearse the baseline against a copy of the target database before running it for real.

Comparable engines treat schema lifecycle as a first-class concern that is invokable separately from the runtime — Temporal ships `temporal-sql-tool`, Camunda ships versioned SQL with a documented apply step, Keycloak runs embedded Liquibase but supports an explicit "migrate then exit" invocation. The common thread is a dedicated, minimal migration entry point rather than schema changes being solely a side effect of starting the application.

For a native migration artifact specifically, Flyway's classpath scan for migration scripts does not work under GraalVM without build-time metadata; Quarkus' Flyway extension registers the configured migration locations at build time, so an extension that declares its own location produces migrations that are discoverable in a native image.

Sources:
- [How to Implement Database Schema Migrations as Kubernetes Jobs with Flyway](https://oneuptime.com/blog/post/2026-02-09-database-migrations-flyway-kubernetes/view)
- [How to Run Database Migration Jobs Before Deployment Rollouts](https://oneuptime.com/blog/post/2026-02-09-database-migration-jobs-before-rollouts/view)
- [Database (Schema) migration to Kubernetes - initContainers vs k8s jobs](https://dev.to/ahmeddrawy/database-schema-migration-to-kubernetes-initcontainers-vs-k8s-jobs-4a4f)
- [Database migrations in Kubernetes applications with Flyway — Sebastian Daschner](https://blog.sebastian-daschner.com/entries/flyway-migrate-databases-managed-k8s)
- [Flyway Baseline On Migrate Setting — Redgate Flyway documentation](https://documentation.red-gate.com/fd/flyway-baseline-on-migrate-setting-277578974.html)
- [Flyway's Baseline Migrations Explained Simply — Redgate](https://www.red-gate.com/hub/product-learning/flyway/flyways-baseline-migrations-explained-simply/)
- [Using Flyway — Quarkus](https://quarkus.io/guides/flyway/)
- [GraalVM native images support · flyway/flyway#2927](https://github.com/flyway/flyway/issues/2927)

## User Experience

**Before:** A user adding PostgreSQL persistence to a Quarkus Flow app has exactly one documented option — set `quarkus.hibernate-orm.database.generation=update` and accept the risk, or hand-copy the guide's SQL into their own `src/main/resources/db/migration/` and wire up Flyway themselves with no framework support if something doesn't match the entity mappings. Enabling Quartz scheduling means separately discovering (undocumented, by reading the module's integration-test `application.properties`) that a Flyway script already exists on the classpath and that `migrate-at-start` needs to be set by hand. There's no signal, at upgrade time, whether a new Quarkus Flow version changed anything about the schema.

**After:** Managed migrations come from two thin, independently-installable extensions and a dedicated migration image built from them.

- A user running a **standalone deployment** (single instance, or a developer setup) adds `quarkus-flow-db-migration-runtime` and/or `quarkus-flow-db-migration-quartz` to their application and sets `migrate-at-start=true` on the corresponding Flyway config — today's `update` convenience, but with a real, auditable migration history.
- A user deploying through an **external orchestrator** (a Kubernetes operator, a Job-based rollout, a CI pipeline) runs the **dedicated `quarkus-flow-db-migration` image** as a short-lived step before application pods start. The image contains both extensions; each stream is switched on or off by its own environment variable. It applies the enabled migrations, reports an exit code, and stops — it never starts a workflow engine or an HTTP listener.
- Release notes and the migration scripts themselves (`V{version}.{sequence}__…sql`) say exactly what changed and when.
- Upgrading an existing, already-populated database from `update` to Flyway-managed is a documented, one-time baseline step (`baseline-on-migrate`), shipped with the extension rather than discovered through a GitHub issue.
- A user who wants none of this changes nothing — `update`/`none` keep working exactly as before.

## Decision

Ship **two extensions** plus **one dedicated migration image** built from them.

### Two extensions, co-located, with no dependency on the Quarkus Flow runtime

| Artifact | Module path | Owns | Depends on |
|---|---|---|---|
| `quarkus-flow-db-migration-runtime` | `persistence/db-migration-runtime/{runtime,deployment}` | JPA runtime schema (`workflow_instance`, `task_info`, `cloud_event`, `completed_task`, `retried_task`) | `quarkus-flyway` |
| `quarkus-flow-db-migration-quartz` | `scheduler/db-migration-quartz/{runtime,deployment}` | Quartz scheduler schema (`QRTZ_*`) | `quarkus-flyway` |

Each extension is a self-contained package: a set of versioned SQL scripts plus a dedicated named Flyway configuration (history table and classpath location). Neither depends on `quarkus-flow` core, the workflow engine, the persistence or scheduler runtime modules, or the HTTP layer. This is a hard constraint, not an incidental one — it is what allows the extensions to run inside a bare, minimal Quarkus application with nothing else on the classpath.

The runtime extension does not depend on `quarkus-flow-persistence-jpa`; it carries its own copy of the schema as SQL. A blocking CI check (see Consequences) keeps that SQL aligned with the JPA entity mappings, so the absence of a compile-time dependency does not create drift risk.

Each extension sits next to the module whose schema it migrates — `persistence/db-migration-runtime` alongside `persistence/jpa`, `scheduler/db-migration-quartz` alongside `scheduler/quartz` — rather than under a shared `db-migration/` parent, so each is discoverable from the module it relates to without implying a coupling between the two migration streams.

Both extensions are optional. A user who wants Hibernate `update`/`none` never adds either and nothing changes.

### Two independent Flyway streams, not one

The existing `flyway_quarkus_history` naming on the Quartz module isolates the framework's own migration history from an application's business-schema Flyway history. This design keeps that isolation and extends it: each extension gets its own Flyway configuration, independent of the other and of any Flyway configuration the consuming application defines for its own tables.

| Artifact | Flyway history table | Migration classpath location |
|---|---|---|
| `quarkus-flow-db-migration-runtime` | `flyway_flow_runtime_history` | `db/migration/flow-runtime` |
| `quarkus-flow-db-migration-quartz` | `flyway_quarkus_history` (unchanged) | `db/migration/flow-quartz` (moved from the current `db/migration` root in `scheduler/quartz/runtime`) |

Moving the Quartz script off the default `db/migration` location matters: Quarkus Flyway's default scan picks up everything under `db/migration` on the classpath, including a user's own scripts. A dedicated named Flyway configuration per extension, pointed at its own location, prevents cross-contamination between framework-owned and user-owned migrations, and between the two framework streams themselves — a user may install one stream without the other, and each must migrate independently.

The existing `V2.0.0__QuarkusQuartzTasks.sql` moves from `scheduler/quartz/runtime` into `quarkus-flow-db-migration-quartz` unchanged in content; only its classpath location and owning module change. `scheduler/quartz/runtime` drops its `quarkus-flyway` dependency, since migration ownership moves to the new extension.

### The dedicated migration image

Orchestrated deployments run migrations through a dedicated image, `quarkus-flow-db-migration`, published alongside each Quarkus Flow release:

- It is a **bare, minimal Quarkus application** whose only dependencies are `quarkus-flyway`, the supported JDBC drivers, and both migration extensions. It contains no workflow engine, no REST layer, no scheduler runtime.
- It is **compiled to native** and shipped **`FROM ubi9-minimal`**, keeping the image small and start-up near-instant so it works well as a Kubernetes Job, an init step, or a pipeline stage.
- **Both streams live on the one image** and are toggled independently, each by a standard Quarkus config property that Quarkus auto-maps to an environment variable:
  - `quarkus.flow.db-migration.runtime.enabled` → `QUARKUS_FLOW_DB_MIGRATION_RUNTIME_ENABLED`
  - `quarkus.flow.db-migration.quartz.enabled` → `QUARKUS_FLOW_DB_MIGRATION_QUARTZ_ENABLED`

  Setting one variable migrates only the JPA runtime schema; setting the other migrates only the Quartz schema; setting both migrates both, in either order (the streams are independent).
- On start, the image runs `migrate()` for each enabled stream, then exits — `0` on success, non-zero on the first migration failure. It never opens a socket.
- The datasource is supplied through standard Quarkus datasource configuration (`QUARKUS_DATASOURCE_JDBC_URL`, `QUARKUS_DATASOURCE_USERNAME`, `QUARKUS_DATASOURCE_PASSWORD`, …), so the migration step can run under a database credential the application runtime never uses.
- Runs are **idempotent**: re-running against an already-migrated database reports zero pending migrations and exits `0`, so a caller that retries the step is safe.

### migrate-at-start, for standalone deployments

Where rollout is *not* a distinct step from application startup — a single instance, a developer environment — a consuming application adds the relevant extension directly and sets `quarkus.flyway.<stream>.migrate-at-start=true`. This is the non-orchestrated option. Whenever more than one instance runs against the same database, or rollout is externally managed, the dedicated image is the correct choice for the reasons in Industry Practice above.

### Versioning convention

New runtime-schema scripts start at `V1.0.0__` and follow the `V{quarkus-flow version}.{sequence}__Description.sql` pattern already established by the Quartz script, so a script's version communicates which Quarkus Flow release introduced it. Quartz schema changes are dictated upstream by Quarkus Quartz's own bundled DDL; when Quarkus bumps that DDL, `quarkus-flow-db-migration-quartz` needs a new versioned script reflecting the delta. This is an ongoing tracking task — watch Quarkus Quartz release notes for DDL changes — not a one-time port.

### Interaction with existing modes

Nothing here changes default behavior. Without either extension on the classpath, `quarkus.hibernate-orm.database.generation=update`/`none` continues to work exactly as today. Adopting managed migrations is opt-in per stream.

### Compatibility with existing manual Quartz Flyway users

Moving `V2.0.0__QuarkusQuartzTasks.sql` off the default `db/migration` classpath location is a breaking change for any user who already enabled Flyway manually against `scheduler/quartz/runtime`'s old location. Rather than silently breaking that setup or shipping a shim, `scheduler/quartz/runtime` detects a manually-configured Flyway setup targeting the old location at startup and logs a warning pointing to `quarkus-flow-db-migration-quartz` and the upgrade docs. The migration still runs wherever the user already pointed Flyway — the warning is a deprecation nudge, not a functional shim, and is removed in a future major version once the deprecation window closes.

## Consequences

**Positive**
- Schema changes are documented, versioned, and released alongside the Quarkus Flow code that needs them, closing the gap the persistence guide already flags as a production concern.
- Generalizes a pattern (isolated Flyway history) already proven in the Quartz module rather than inventing a new one.
- The dedicated image gives any orchestrator — a Kubernetes operator, a Job-based rollout, a CI pipeline — a stable, minimal target to run, with a clean exit code and isolated logs, and without that orchestrator owning or vendoring SQL scripts itself.
- Because neither extension depends on the Quarkus Flow runtime, the migration image stays small, builds native cleanly, and its dependency surface is decoupled from the full runtime's.
- Migrations can run under a database credential scoped to DDL, separate from the runtime's credential.

**Negative / costs**
- Three artifacts to build and release in lockstep with each Quarkus Flow release: the two extensions and the migration image.
- Nothing intrinsically keeps Hibernate entity mappings and the runtime Flyway scripts in sync. A blocking CI job is required: on every PR, boot a test application with `quarkus.hibernate-orm.database.generation=validate` against a schema produced purely by the `quarkus-flow-db-migration-runtime` Flyway scripts (via Dev Services/Testcontainers, no Hibernate DDL) and fail the build on any mismatch. This must land with the runtime extension, not as a follow-up.
- Native packaging of the migration image requires each extension to register its Flyway migration location at build time so scripts are discoverable under GraalVM.
- Quartz schema tracking becomes an ongoing upstream-watching task.
- An application upgrading from `update` to Flyway-managed on an existing, already-populated database needs a Flyway baseline (`baseline-on-migrate=true`, matching the existing Quartz IT precedent of `baseline-version=1.0`). This upgrade path needs explicit documentation, including the "back up first, rehearse on a copy" guidance from industry practice.

## Alternatives Considered

- **Leave scripts ad hoc, no dedicated extension.** Rejected — no stable artifact for an orchestrator to run, and the JPA runtime schema has no migration path at all.
- **`migrate-at-start` only, no dedicated image.** Rejected as the sole mechanism — it races under replication, can blow the pod start-up budget, surfaces migration failure as a crash loop, and offers no separation between DDL and runtime credentials. Kept as the explicit option for standalone, non-orchestrated deployments.
- **Reuse the full Quarkus Flow runner image, gated by a "migrate-only" property.** Rejected — it drags the entire workflow engine and HTTP stack into the migration path, prevents a minimal native image, and couples the migration step's dependency surface to the full runtime's.
- **Extensions that depend on the persistence/scheduler runtime modules.** Rejected — that dependency would pull the Quarkus Flow runtime into any migration deployment and defeat the minimal-image goal. The CI drift check removes the need for a compile-time link.
- **Single combined Flyway stream for runtime + Quartz.** Rejected — the two are independently optional; one history table and location would force both to always be present together.
- **An external orchestrator owns and vendors the migration scripts itself.** Rejected — couples that orchestrator's release cadence to Quarkus Flow's schema evolution and duplicates schema knowledge Quarkus Flow already has.

## Open Questions

- **JDBC drivers in the migration image.** Whether to bundle all supported drivers in one image or publish per-database variants — a size-versus-simplicity trade-off to settle before the image is first published.
- **Image coordinates and publishing pipeline.** Registry, naming, and tagging scheme for `quarkus-flow-db-migration`, aligned with how Quarkus Flow publishes its other images.
