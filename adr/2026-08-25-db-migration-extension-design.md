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

**Before:** A user adding PostgreSQL persistence to a Quarkus Flow app has exactly one documented option — set `quarkus.hibernate-orm.database.generation=update` and accept the risk, or hand-copy the guide's SQL into their own `src/main/resources/db/migration/` and wire up Flyway themselves with no framework support if something doesn't match the entity mappings. Quartz scheduling is one step ahead — `docs/quartz.adoc` documents the exact `quarkus.flyway.*` properties to set, and the script itself already ships on the classpath — but the gap is packaging and isolation, not discovery: the documented setup uses a dedicated history table (`flyway_quarkus_history`) but still points Flyway at the default `db/migration` location, so the script's *history* is isolated from the user's own but its *script* sits unpackaged alongside whatever migrations the user's own application defines at that same path, and nothing turns it on by default. There's no signal, at upgrade time, whether a new Quarkus Flow version changed anything about either schema.

**After:** Managed migrations come from two thin, independently-installable extensions.

- A user running a **standalone deployment** (single instance, or a developer setup) adds `quarkus-flow-db-migration-runtime` and/or `quarkus-flow-db-migration-quartz` to their application, sets `migrate-at-start=true` on the corresponding Flyway config, and switches Hibernate off DDL duty (`quarkus.hibernate-orm.database.generation=none`, or `validate` to keep drift detection) — today's `update` convenience, but with a real, auditable migration history and no risk of Hibernate performing unmanaged DDL alongside it.
- A user deploying through an **external orchestrator** (a Kubernetes operator, a Job-based rollout, a CI pipeline) builds their own short-lived migration step — a bare Quarkus app bundling the enabled extension(s), since neither depends on the Quarkus Flow runtime — and runs it before application pods start, applying migrations via Flyway and gating rollout on its exit code. Quarkus Flow does not build or publish that image or app; the extensions are designed so building one is straightforward.
- Release notes and the migration scripts themselves (`V{version}.{sequence}__…sql`) say exactly what changed and when.
- Upgrading an existing, already-populated database from `update` to Flyway-managed is a documented, one-time baseline step (`baseline-on-migrate`), shipped with the extension rather than discovered through a GitHub issue.
- A user who wants none of this changes nothing — `update`/`none` keep working exactly as before.

## Decision

Ship **two extensions**.

### Two extensions, co-located, with no dependency on the Quarkus Flow runtime

| Artifact | Module path | Owns | Depends on |
|---|---|---|---|
| `quarkus-flow-db-migration-runtime` | `persistence/db-migration-runtime/{runtime,deployment}` | JPA runtime schema — three physical tables: `cloud_event_entity`, `workflow_instance_entity`, `task_info_entity` | `quarkus-flyway` |
| `quarkus-flow-db-migration-quartz` | `scheduler/db-migration-quartz/{runtime,deployment}` | Quartz scheduler schema (`QRTZ_*`) | `quarkus-flyway` |

`CompletedTaskEntity` and `RetriedTaskEntity` are not separate tables: both extend `TaskInfoEntity`, which is mapped `@Inheritance(strategy = SINGLE_TABLE)` with a `task_type` discriminator column, so both live in `task_info_entity`. The migration scripts must create exactly these three tables — matching the current five-database SQL already documented in the persistence guide — not five.

Each extension is a self-contained package: a set of versioned SQL scripts plus a dedicated named Flyway configuration (history table and classpath location). Neither depends on `quarkus-flow` core, the workflow engine, the persistence or scheduler runtime modules, or the HTTP layer. This is a hard constraint, not an incidental one — it is what allows the extensions to run inside a bare, minimal Quarkus application with nothing else on the classpath.

The runtime extension does not depend on `quarkus-flow-persistence-jpa`; it carries its own copy of the schema as SQL. A blocking CI check (see Consequences) keeps that SQL aligned with the JPA entity mappings, so the absence of a compile-time dependency does not create drift risk.

Each extension sits next to the module whose schema it migrates — `persistence/db-migration-runtime` alongside `persistence/jpa`, `scheduler/db-migration-quartz` alongside `scheduler/quartz` — rather than under a shared `db-migration/` parent, so each is discoverable from the module it relates to without implying a coupling between the two migration streams.

Both extensions are optional. A user who wants Hibernate `update`/`none` never adds either and nothing changes.

### Two independent Flyway streams, not one

The existing `flyway_quarkus_history` naming on the Quartz module isolates the framework's own migration history from an application's business-schema Flyway history. This design keeps that isolation and extends it: each extension gets its own Flyway configuration, independent of the other and of any Flyway configuration the consuming application defines for its own tables.

| Artifact | Named datasource | Flyway history table | Migration classpath location |
|---|---|---|---|
| `quarkus-flow-db-migration-runtime` | `flow-runtime` | `flyway_flow_runtime_history` | `db/flow-migration/runtime/<db-kind>` |
| `quarkus-flow-db-migration-quartz` | `flow-quartz` | `flyway_flow_quartz_history` | `db/flow-migration/quartz` (moved from the current `db/migration` root in `scheduler/quartz/runtime`) |

Two things had to change from an earlier version of this design to actually deliver isolation:

**Independent histories need independent named datasources, not an arbitrary "stream" label.** Quarkus's Flyway configuration is keyed by datasource name (`quarkus.flyway."<datasource-name>".*`) — a named Flyway configuration is not a free-standing migration stream, it's tied to a named `quarkus.datasource."<name>".*`. Running two independent histories against what is physically the same database therefore requires two named datasources, each pointed at the same JDBC connection details (optionally under different, DDL-scoped credentials — see Consequences). Each extension's deployment module contributes the config defaults for its own named datasource (`flow-runtime` / `flow-quartz`); the consuming application still supplies the actual connection details, the same way it does for its primary datasource today.

This is a manual binding, not an automatic one: `persistence/jpa` and `scheduler/quartz` both read the *default*, unnamed `quarkus.datasource.*` — they have no awareness of `flow-runtime`/`flow-quartz` and never will, since the extensions must stay independent of those runtime modules. A consuming application is responsible for pointing the named datasource(s) at the same JDBC URL (and, typically, the same or a DDL-scoped user) as its default datasource; nothing in either extension verifies that the two agree, so a misconfigured named datasource silently migrates a different database than the one JPA or Quartz actually connect to at runtime. The setup guide for each extension must call this out explicitly, with the exact property pairing to set.

**Neither location may be nested under the default `db/migration` root.** Quarkus Flyway's default scan (`classpath:db/migration`) is recursive, so a subpath like `db/migration/flow-runtime` is still visible to an application's own default Flyway configuration — that does not achieve isolation, it just relocates the collision. Both extensions instead use a sibling root, `db/flow-migration/...`, entirely outside the conventionally-scanned path, so an application's own default-datasource Flyway config cannot see either framework stream regardless of what it scans.

Vendor-specific SQL syntax also has to be handled explicitly: the persistence guide already ships five different scripts (H2, MySQL, PostgreSQL, Oracle, MSSQL) because column types and syntax diverge, and packaging all five under one Flyway location would make Flyway see either incompatible scripts or duplicate versions. `quarkus-flow-db-migration-runtime` ships one subdirectory per supported `db-kind` under its location; its deployment processor reads the configured `quarkus.datasource.db-kind` at build time and registers only the matching subdirectory as the effective Flyway location, so exactly one vendor's scripts ever reach a given build's classpath scan. `quarkus-flow-db-migration-quartz` needs the same treatment, not less: the existing `V2.0.0__QuarkusQuartzTasks.sql` is PostgreSQL-specific (`BYTEA`, `BOOL` column types throughout), not vendor-neutral, so it cannot be handed unchanged to a non-Postgres consumer. At launch, `quarkus-flow-db-migration-quartz` ships that one script under a `postgresql` subdirectory using the same per-`db-kind` selection mechanism, and supports only `db-kind=postgresql`; the deployment processor fails the build for any other configured `db-kind` rather than silently applying Postgres-only DDL against a different database. Scripts for other database kinds are follow-up work, added the same way the runtime extension already supports five.

The existing `V2.0.0__QuarkusQuartzTasks.sql` moves from `scheduler/quartz/runtime` into `quarkus-flow-db-migration-quartz`, with one content change: the leading `DROP TABLE IF EXISTS` statements for all eleven `QRTZ_*` tables are removed. As written, the script is a reset — a first run against a database that already has Quartz tables (from the previous manual setup, or from Quarkus Quartz's own runtime DDL) would drop and recreate them, discarding scheduler state. Without the drops, a genuinely fresh database is unaffected (`CREATE TABLE` with nothing pre-existing is safe), and a database that already has the tables must instead be baselined — `baseline-on-migrate=true` with `baseline-version` set to the script's own version, so Flyway treats it as already applied rather than re-running it — the same onboarding treatment this design already requires for the JPA runtime stream moving off Hibernate `update` (see Versioning convention and Consequences). `scheduler/quartz/runtime` keeps its `quarkus-flyway`/`quarkus-flyway-deployment` dependency through the deprecation window described below (see Compatibility with existing manual Quartz Flyway users) — dropping it now would silently stop applying migrations for users still pointed at the old location, which is exactly the breakage that window exists to avoid. The dependency is removed only in the future major version that also removes the compatibility shim.

### migrate-at-start, for standalone deployments

Where rollout is *not* a distinct step from application startup — a single instance, a developer environment — a consuming application adds the relevant extension directly and sets `quarkus.flyway."flow-runtime".migrate-at-start=true` and/or `quarkus.flyway."flow-quartz".migrate-at-start=true` for the streams it enables. This is the non-orchestrated option. Whenever more than one instance runs against the same database, or rollout is externally managed, a consumer-built minimal migration step (per Industry Practice above) is the correct choice, not `migrate-at-start`. Quarkus Flow does not build or publish that step itself — the extensions are designed with no runtime/HTTP dependency specifically so a consumer can bundle either (or both) into their own bare Quarkus app and run it however their orchestrator expects.

### Migrate-only mode, for orchestrator-driven deployments

The bare Quarkus app a consumer builds for the orchestrated path (per Industry Practice and User Experience above) gets "apply migrations, then exit" without any custom entry-point code, because of what it deliberately leaves out: with neither extension pulling in `quarkus-vertx-http` or any other extension that registers a long-running service, and no user-supplied `QuarkusApplication` main class that blocks, nothing tells the Quarkus runtime to stay up once startup completes. `migrate-at-start=true` runs Flyway during that startup sequence; once it finishes (or throws), startup completes (or fails) and the process exits on its own — cleanly with code `0` on success, non-zero if Flyway's migration failed and aborted Quarkus's own boot. An orchestrator gates rollout on that exit code directly, with no polling, health check, or custom shutdown logic required. This is the concrete mechanism behind the "bare Quarkus app... gating rollout on its exit code" described in User Experience, and is what satisfies issue #896's headless-invocation requirement without Quarkus Flow building or publishing an image itself.

### Versioning convention

New runtime-schema scripts start at `V1.0.0__` and follow the `V{quarkus-flow version}.{sequence}__Description.sql` pattern already established by the Quartz script, so a script's version communicates which Quarkus Flow release introduced it. Quartz schema changes are dictated upstream by Quarkus Quartz's own bundled DDL; when Quarkus bumps that DDL, `quarkus-flow-db-migration-quartz` needs a new versioned script reflecting the delta. This is an ongoing tracking task — watch Quarkus Quartz release notes for DDL changes — not a one-time port.

Baseline configuration depends on what's already in the target database, and the two cases need opposite guidance:

- **A genuinely empty database** (new adopter, nothing installed yet) needs no baseline at all — `migrate-at-start=true` alone runs `V1.0.0` (runtime) / `V2.0.0` (Quartz) as an ordinary first migration; Flyway does not require a baseline for a schema with nothing in it.
- **A database that already has the schema**, installed by something other than this extension's own Flyway history — Hibernate `update` for the runtime stream, or a prior manual Flyway setup or Quartz's own bootstrap for the Quartz stream — needs `baseline-on-migrate=true` with `baseline-version` set to the version of the migration that *matches the schema already there*, so Flyway records it as already applied and does not try to run it again: `baseline-version=1.0.0` for the runtime stream, `baseline-version=2.0.0` for Quartz. A `baseline-version` below the first script (as the existing Quartz IT setup's `baseline-version=1.0` effectively is, relative to `V2.0.0`) only works by accident, for a database empty enough that the baseline path never actually engages — against a database that already has the `QRTZ_*` tables, that same value has Flyway attempt to run `V2.0.0`'s `CREATE TABLE` statements a second time and fail on already-existing objects, since the destructive `DROP TABLE IF EXISTS` prefix is removed (see Two independent Flyway streams, not one).

### Interaction with existing modes

Nothing here changes default behavior. Without either extension on the classpath, `quarkus.hibernate-orm.database.generation=update`/`none` continues to work exactly as today. Adopting managed migrations is opt-in per stream — but adding `quarkus-flow-db-migration-runtime` does not, by itself, stop Hibernate from managing DDL: the two mechanisms are independent, and leaving `database.generation=update` in place means Hibernate can still alter the schema at boot alongside (or racing) Flyway's own migration, defeating the managed-migration and multi-replica guarantees this design exists for. The runtime extension's deployment processor therefore checks the configured `quarkus.hibernate-orm.database.generation` at build time and fails the build if it is anything other than `none` or `validate`, matching the requirement the persistence guide already documents for manual Flyway adoption.

### Compatibility with existing manual Quartz Flyway users

Moving `V2.0.0__QuarkusQuartzTasks.sql` off the default `db/migration` classpath location is a breaking change for any user who already enabled Flyway manually against `scheduler/quartz/runtime`'s old location: once the script's only copy lives under the new extension's `db/flow-migration/quartz` location, the old location is empty on the classpath, so a Flyway configuration still pointed at it finds nothing to run, regardless of any warning. Simply putting the old copy back under `scheduler/quartz/runtime`'s own `db/migration` would defeat the isolation this design exists for — every Quartz user, not just the ones who need the compatibility path, would again have a framework script sitting on the default Flyway scan path. Instead, the old copy moves to a small, separate, deprecation-window-only artifact — `quarkus-flow-db-migration-quartz-compat` — kept identical to the new extension's copy by the same blocking CI check that guards the JPA runtime schema against drift (see Consequences). A user who already had Flyway manually pointed at the old location adds this artifact explicitly to keep their existing setup running unchanged while they migrate; a user who never had manual Flyway configured never sees it, on the classpath or otherwise. The compat artifact logs a startup warning pointing to `quarkus-flow-db-migration-quartz` and the upgrade docs, and is removed, along with this whole compatibility path, in a future major version once the deprecation window closes.

The history **table name** is also changing, not just the location: the new extension's Flyway configuration uses `flyway_flow_quartz_history`, distinct from the `flyway_quarkus_history` name already established by the existing manual setup (see Context). A user migrating from that manual setup to the new extension therefore starts with an empty history table under the new name — Flyway has no record that `V2.0.0__QuarkusQuartzTasks.sql` already ran, and without the leading `DROP TABLE IF EXISTS` statements (removed above), re-running it against a database that already has the `QRTZ_*` tables fails on already-existing objects instead of silently resetting them. This needs the populated-database baseline treatment described above, not the empty-database path — `baseline-on-migrate=true` with `baseline-version=2.0.0`, matching the already-applied `V2.0.0` — and must be documented explicitly in the upgrade/migration guide for users coming from the manual setup, not only for users coming from Hibernate `update`.

## Consequences

**Positive**
- Schema changes are documented, versioned, and released alongside the Quarkus Flow code that needs them, closing the gap the persistence guide already flags as a production concern.
- Generalizes a pattern (isolated Flyway history) already proven in the Quartz module rather than inventing a new one.
- Because neither extension depends on the Quarkus Flow runtime or the HTTP layer, a consumer can bundle either (or both) into a minimal, non-serving Quarkus app for their own orchestrator — a Kubernetes operator, a Job-based rollout, a CI pipeline — to run as a distinct rollout step, with a clean exit code and isolated logs, without that orchestrator owning or vendoring SQL scripts itself.
- Migrations can run under a database credential scoped to DDL, separate from the runtime's credential.

**Negative / costs**
- Two artifacts to build and release in lockstep with each Quarkus Flow release: the two extensions — plus a third, `quarkus-flow-db-migration-quartz-compat`, for the duration of the Quartz deprecation window (see Compatibility with existing manual Quartz Flyway users).
- Nothing intrinsically keeps Hibernate entity mappings and the runtime Flyway scripts in sync. A blocking CI job is required: on every PR, boot a test application with `quarkus.hibernate-orm.database.generation=validate` against a schema produced purely by the `quarkus-flow-db-migration-runtime` Flyway scripts (via Dev Services/Testcontainers, no Hibernate DDL) and fail the build on any mismatch. This must land with the runtime extension, not as a follow-up.
- Each extension must register its Flyway migration location at build time so scripts are discoverable under GraalVM, since any consumer wanting a native migration step depends on that.
- Quartz schema tracking becomes an ongoing upstream-watching task.
- An application upgrading from `update` to Flyway-managed on an existing, already-populated database needs a Flyway baseline (`baseline-on-migrate=true`), with a stream-specific `baseline-version` matching the version of the schema already installed (`1.0.0` for the runtime stream, `2.0.0` for Quartz — see Versioning convention), not a value below the first script. This upgrade path needs explicit documentation, including the "back up first, rehearse on a copy" guidance from industry practice.

## Alternatives Considered

- **Leave scripts ad hoc, no dedicated extension.** Rejected — no stable artifact for an orchestrator to run, and the JPA runtime schema has no migration path at all.
- **`migrate-at-start` as the only supported mechanism.** Rejected as the sole mechanism — it races under replication, can blow the pod start-up budget, surfaces migration failure as a crash loop, and offers no separation between DDL and runtime credentials. Kept as the explicit option for standalone, non-orchestrated deployments; consumers needing the orchestrated path build their own minimal migration step from the extensions.
- **Bundling migrations into the full Quarkus Flow runner image, gated by a "migrate-only" property.** Not recommended for consumers building their own migration step — it drags the entire workflow engine and HTTP stack into the migration path, prevents a minimal native image, and couples the migration step's dependency surface to the full runtime's. This is why the extensions are designed with no dependency on the Quarkus Flow runtime.
- **Extensions that depend on the persistence/scheduler runtime modules.** Rejected — that dependency would pull the Quarkus Flow runtime into any migration deployment and defeat the goal of extensions being usable in a minimal, non-serving app. The CI drift check removes the need for a compile-time link.
- **Single combined Flyway stream for runtime + Quartz.** Rejected — the two are independently optional; one history table and location would force both to always be present together.
- **An external orchestrator owns and vendors the migration scripts itself.** Rejected — couples that orchestrator's release cadence to Quarkus Flow's schema evolution and duplicates schema knowledge Quarkus Flow already has.
