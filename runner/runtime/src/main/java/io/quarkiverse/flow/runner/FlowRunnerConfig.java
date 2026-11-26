package io.quarkiverse.flow.runner;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/**
 * Runtime configuration for Quarkus Flow Runner.
 * <p>
 * The Flow Runner extension enables runtime workflow definition loading and
 * REST-based workflow execution. It targets development, testing, and cloud
 * deployment scenarios where workflows need to be loaded dynamically without
 * application rebuilds.
 * <p>
 * Configuration example for cloud deployment:
 *
 * <pre>
 * # Enable runner with path-based loading from ConfigMap
 * quarkus.flow.runner.enabled=true
 * quarkus.flow.runner.source.path=/deployments/workflows
 *
 * # Use API Key authentication
 * quarkus.flow.runner.security.type=api-key
 * quarkus.flow.runner.security.api-keys."invoker".secret=${FLOW_API_KEY}
 * quarkus.flow.runner.security.api-keys."invoker".roles=flow-invoker
 * quarkus.flow.runner.security.api-keys."invoker".namespaces=*
 * </pre>
 *
 * @see <a href="https://github.com/quarkiverse/quarkus-flow/issues/52">Issue #52</a>
 * @see <a href="https://docs.quarkiverse.io/quarkus-flow/dev/">Quarkus Flow Documentation</a>
 */

@ConfigRoot(phase = ConfigPhase.RUN_TIME)
@ConfigMapping(prefix = "quarkus.flow.runner")
public interface FlowRunnerConfig {

    /**
     * Enable or disable the Flow Runner feature.
     * <p>
     * When {@code false}, all runner REST endpoints are disabled and
     * workflow definitions are not loaded from the configured source.
     * <p>
     * This is the master switch for the entire runner functionality.
     *
     * @return {@code true} if runner is enabled (default when dependency added),
     *         {@code false} to disable all runner functionality
     */
    @WithDefault("true")
    boolean enabled();

    /**
     * Workflow definition source configuration.
     *
     * @return the source configuration
     */
    Source source();

    /**
     * Security configuration for authentication and authorization.
     *
     * @return the security configuration
     */
    Security security();

    /**
     * OpenAPI documentation configuration.
     *
     * @return the OpenAPI configuration
     */
    OpenApi openapi();

    /**
     * Workflow definition source configuration.
     * <p>
     * Specifies where workflow definitions should be loaded from at startup.
     * The source is read once during application startup and can be manually
     * reloaded via management endpoints.
     */
    interface Source {

        /**
         * Filesystem path to workflow definitions.
         * <p>
         * Required when {@code type=PATH}. The directory is scanned recursively
         * for all {@code .yaml}, {@code .yml}, and {@code .json} files.
         * <p>
         * In Kubernetes/OpenShift deployments, this typically points to a
         * ConfigMap or persistent volume mount:
         *
         * <pre>
         * quarkus.flow.runner.source.path=/deployments/workflows
         * </pre>
         * <p>
         * Workflows are uniquely identified by {@code namespace:name:version}.
         * Duplicate workflow identifiers cause application startup to fail.
         *
         * @return the filesystem path, or empty if not configured
         */
        Optional<String> path();

        /**
         * Whether to follow symbolic links when scanning for workflow definition files.
         * <p>
         * When {@code false} (default), symbolic links are excluded from the scan.
         * This prevents duplicate workflow detection failures on Kubernetes, where
         * ConfigMap and Secret volume mounts use an internal symlink structure
         * (a timestamped directory with symlinks pointing to the real files).
         * <p>
         * Set to {@code true} only if your workflow files are intentionally
         * provided via symbolic links outside of a Kubernetes volume mount.
         *
         * @return {@code true} to follow symlinks, {@code false} (default) to skip them
         * @see <a href="https://github.com/quarkiverse/quarkus-flow/issues/835">Issue #835</a>
         */
        @WithDefault("false")
        Boolean followSymlinks();

        /**
         * File watcher runtime configuration.
         *
         * @return the watch configuration
         */
        Watch watch();

        /**
         * Runtime configuration for the file watcher polling interval.
         * <p>
         * The watcher itself is enabled/disabled at build time via
         * {@code quarkus.flow.runner.source.watch.enabled} (see
         * {@link FlowRunnerSourceWatchConfig}).
         */
        interface Watch {

            /**
             * Polling interval for detecting new workflow files.
             * <p>
             * Uses the Quarkus duration format (e.g., {@code "5s"},
             * {@code "10s"}, {@code "1m"}, or an ISO-8601 duration).
             * <p>
             * This is a runtime property and can be changed without rebuilding
             * the application.
             *
             * @return the polling interval (default: 5s)
             */
            @WithDefault("5s")
            Duration interval();
        }
    }

    /**
     * Security configuration for authentication and authorization.
     * <p>
     * Supports three authentication modes:
     * <ul>
     * <li>{@code OIDC} - Enterprise JWT-based authentication using Quarkus OIDC extension</li>
     * <li>{@code API_KEY} - Simple bearer token authentication for M2M/webhook scenarios</li>
     * <li>{@code NONE} - No authentication (development only, logs warning on startup)</li>
     * </ul>
     * <p>
     * Authorization combines:
     * <ul>
     * <li>RBAC - Role-based access control ({@code flow-admin}, {@code flow-invoker})</li>
     * <li>ABAC - Namespace-level attribute-based access control</li>
     * </ul>
     */
    interface Security {

        /**
         * Authentication type.
         * <p>
         * Selects which authentication mechanism to use:
         * <ul>
         * <li>{@code OIDC} - Requires {@code quarkus-oidc} extension in classpath.
         * Uses standard Quarkus OIDC configuration ({@code quarkus.oidc.*}).
         * Roles extracted from JWT claims.</li>
         * <li>{@code API_KEY} - Custom filter validates {@code Authorization: Bearer <key>}
         * header against configured secrets. Maps keys to roles.</li>
         * <li>{@code NONE} - Endpoints are unprotected. Use only in development.</li>
         * </ul>
         *
         * @return the authentication type (required)
         */
        Type type();

        /**
         * API Key definitions (only used when {@code type=API_KEY}).
         * <p>
         * Each key is mapped to roles and authorized namespaces. Example:
         *
         * <pre>
         * # Administrators always have access to all namespaces
         * quarkus.flow.runner.security.api-keys."admin".secret=${ADMIN_KEY}
         * quarkus.flow.runner.security.api-keys."admin".roles=flow-admin
         *
         * # Explicit unrestricted access for a non-admin key
         * quarkus.flow.runner.security.api-keys."invoker".secret=${INVOKER_KEY}
         * quarkus.flow.runner.security.api-keys."invoker".roles=flow-invoker
         * quarkus.flow.runner.security.api-keys."invoker".namespaces=*
         *
         * # Restricted access
         * quarkus.flow.runner.security.api-keys."team-a".secret=${TEAM_A_KEY}
         * quarkus.flow.runner.security.api-keys."team-a".roles=flow-invoker
         * quarkus.flow.runner.security.api-keys."team-a".namespaces=team-a
         * </pre>
         * <p>
         * The {@code namespaces} property defaults to {@code "*"} when omitted.
         *
         * @return map of API key names to their configuration
         */
        Map<String, ApiKey> apiKeys();

        /**
         * Namespace authorization configuration.
         *
         * @return the namespace authorization settings
         */
        Namespace namespace();

        /**
         * Service-to-service authentication mechanism configuration.
         *
         * @return the service-to-service authentication configuration
         */
        Service service();

        /**
         * Authentication types.
         */
        enum Type {
            /**
             * OIDC/JWT authentication using Quarkus OIDC extension.
             * Requires {@code quarkus-oidc} dependency in classpath.
             */
            OIDC,

            /**
             * API Key authentication using bearer tokens.
             * Keys are configured in {@code quarkus.flow.runner.security.api-keys.*}.
             */
            API_KEY,

            /**
             * No authentication. All endpoints are unprotected.
             * Use only in development environments.
             */
            NONE;
        }

        /**
         * API Key configuration.
         * <p>
         * Defines a single API key with its secret and assigned roles.
         */
        interface ApiKey {
            /**
             * The API key secret value.
             * <p>
             * Should be loaded from environment variables or Kubernetes Secrets:
             *
             * <pre>
             * quarkus.flow.runner.security.api-keys."my-key".secret=${FLOW_API_KEY}
             * </pre>
             *
             * @return the secret value
             */
            String secret();

            /**
             * Roles assigned to this API key.
             * <p>
             * Predefined roles:
             * <ul>
             * <li>{@code flow-admin} - Full access (definition management + execution)</li>
             * <li>{@code flow-invoker} - Execution only (POST/GET /runner/exec/*)</li>
             * </ul>
             * <p>
             * Example:
             *
             * <pre>
             * quarkus.flow.runner.security.api-keys."admin-key".roles=flow-admin
             * quarkus.flow.runner.security.api-keys."webhook-key".roles=flow-invoker
             * </pre>
             *
             * @return set of role names
             */
            Set<String> roles();

            /**
             * Namespaces authorized for this API key.
             * <p>
             * The value {@code "*"} grants access to all namespaces. When omitted, this
             * property defaults to {@code "*"} for backward compatibility.
             *
             * @return the configured authorized namespaces
             */
            @WithDefault("*")
            Optional<Set<String>> namespaces();
        }

        /**
         * Namespace-level authorization (ABAC) configuration.
         * <p>
         * Controls access to workflows based on their namespace.
         * Users can only execute workflows in namespaces they are authorized for.
         */
        interface Namespace {
            /**
             * JWT claim containing the namespaces authorized for the current identity.
             * <p>
             * Used when {@code type=OIDC}. The claim can contain:
             * <ul>
             * <li>A single namespace, for example {@code "team-a"}</li>
             * <li>An array of namespaces, for example
             * {@code ["team-a", "team-b"]}</li>
             * <li>A comma-separated string, for example
             * {@code "team-a,team-b"}</li>
             * <li>The exact value {@code "*"} to authorize all current and future
             * namespaces</li>
             * </ul>
             * <p>
             * For a non-admin OIDC identity, a missing, null, empty, or blank claim grants
             * no namespace access when namespace validation is enabled. Identities with
             * the {@code flow-admin} role bypass namespace restrictions.
             * <p>
             * Namespace matching is exact and case-sensitive. Partial wildcard values
             * such as {@code "team-*"} are not supported.
             * <p>
             * Example:
             *
             * <pre>
             * quarkus.flow.runner.security.namespace.claim = namespace
             * </pre>
             *
             * @return the JWT claim name, defaulting to {@code "namespace"}
             */
            @WithDefault("namespace")
            String claim();

            /**
             * Enables or disables namespace authorization.
             * <p>
             * When enabled:
             * <ul>
             * <li>Identities with the {@code flow-admin} role can access all
             * namespaces.</li>
             * <li>The exact namespace value {@code "*"} authorizes all namespaces.</li>
             * <li>Explicit namespace values authorize only those namespaces.</li>
             * <li>A missing, null, empty, or blank namespace set grants no access to a
             * non-admin identity.</li>
             * </ul>
             * <p>
             * When disabled, namespace authorization is skipped and access is controlled
             * only through role-based authorization.
             *
             * @return {@code true} when namespace validation is enabled
             */
            @WithDefault("true")
            boolean validate();
        }

        /**
         * Service-to-service authentication mechanisms.
         * <p>
         * Groups authentication mechanisms for service-to-service
         * communication (workload identity), as opposed to external identity
         * providers (OIDC) or statically configured secrets (API keys).
         */
        interface Service {

            /**
             * Kubernetes ServiceAccount authentication configuration.
             *
             * @return the Kubernetes ServiceAccount configuration
             */
            Kubernetes kubernetes();

            /**
             * Kubernetes ServiceAccount authentication.
             * <p>
             * Authenticates requests made by Kubernetes pods using their
             * projected ServiceAccount tokens, validated against the
             * Kubernetes API server TokenReview endpoint. When active, this
             * mechanism complements the configured {@code type} mechanism.
             * <p>
             * Configuration example:
             *
             * <pre>
             * # Activate only when running inside Kubernetes (default)
             * quarkus.flow.runner.security.service.kubernetes.enabled=auto
             *
             * # Restrict which ServiceAccounts are trusted (full usernames)
             * quarkus.flow.runner.security.service.kubernetes.trusted-service-accounts=system:serviceaccount:flow-ns:workflow-invoker
             *
             * # Tune the TokenReview result cache
             * quarkus.flow.runner.security.service.kubernetes.cache-max-entries=256
             * </pre>
             *
             * @see <a href="https://github.com/quarkiverse/quarkus-flow/issues/1160">Issue #1160</a>
             */
            interface Kubernetes {
                /**
                 * Whether the Kubernetes ServiceAccount authentication mechanism
                 * is enabled.
                 * <p>
                 * Accepted values:
                 * <ul>
                 * <li>{@code auto} (default) - active only when running inside
                 * a Kubernetes cluster ({@code KUBERNETES_SERVICE_HOST} set and
                 * the ServiceAccount token file readable)</li>
                 * <li>{@code true} - always active, even outside a cluster
                 * (mainly for tests)</li>
                 * <li>{@code false} - never active</li>
                 * </ul>
                 *
                 * @return the enabled value: {@code auto}, {@code true}, or
                 *         {@code false}
                 */
                @WithDefault("auto")
                String enabled();

                /**
                 * ServiceAccounts trusted by this mechanism, as full
                 * Kubernetes usernames ({@code system:serviceaccount:<namespace>:<name>}).
                 * <p>
                 * Any ServiceAccount authenticated via TokenReview is accepted
                 * with the {@code flow-service-reader} role. Listing
                 * ServiceAccounts here additionally grants them the {@code *}
                 * (all) namespaces authorization.
                 *
                 * @return the set of trusted ServiceAccounts as full
                 *         usernames ({@code system:serviceaccount:<namespace>:<name>}),
                 *         or empty when unset
                 */
                Optional<Set<String>> trustedServiceAccounts();

                /**
                 * Maximum number of TokenReview results kept in the cache.
                 * <p>
                 * Validated token identities are cached to avoid hitting the
                 * Kubernetes API server on every request.
                 *
                 * @return the maximum number of cache entries (default: 256)
                 */
                @WithDefault("256")
                int cacheMaxEntries();

                /**
                 * Kubernetes API server URL.
                 * <p>
                 * Test seam: when unset, defaults to
                 * {@code https://$KUBERNETES_SERVICE_HOST:$KUBERNETES_SERVICE_PORT_HTTPS},
                 * the in-cluster API server address.
                 *
                 * @return the API server URL, or empty to use the in-cluster
                 *         default
                 */
                Optional<String> apiServerUrl();
            }
        }
    }

    /**
     * OpenAPI documentation configuration.
     * <p>
     * Controls dynamic OpenAPI document generation for registered workflows.
     */
    interface OpenApi {

        /**
         * Enable or disable dynamic workflow operations in OpenAPI document.
         * <p>
         * When {@code true}, the OpenAPI document will include a concrete operation
         * for each registered workflow definition. For example, a workflow
         * {@code test-namespace:hello-world:1.0.0} will generate:
         *
         * <pre>
         * POST /q/flow/exec/test-namespace/hello-world/1.0.0
         * </pre>
         * <p>
         * When {@code false}, only the generic parameterized endpoint is documented.
         * <p>
         * <strong>Security Note:</strong> The OpenAPI document ({@code /q/openapi}) is
         * publicly accessible and will show all registered workflows (namespace, name,
         * version, summary). This serves as a <strong>public workflow catalog</strong>.
         * Actual execution remains protected by authentication and namespace authorization,
         * returning 401/403 for unauthorized access attempts.
         * <p>
         * <strong>Exposed Information:</strong>
         * <ul>
         * <li>Workflow namespace, name, and version</li>
         * <li>Workflow summary/description (if configured)</li>
         * </ul>
         * <strong>NOT Exposed:</strong>
         * <ul>
         * <li>Workflow DSL or task definitions</li>
         * <li>Internal business logic or function implementations</li>
         * <li>Secrets, credentials, or sensitive data</li>
         * </ul>
         *
         * @return {@code true} to expand workflows in OpenAPI (default),
         *         {@code false} to use generic endpoint only
         */
        @WithDefault("true")
        boolean expandWorkflows();
    }

}
