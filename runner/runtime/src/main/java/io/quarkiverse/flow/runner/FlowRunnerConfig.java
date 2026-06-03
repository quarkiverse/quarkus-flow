package io.quarkiverse.flow.runner;

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
         * @return the authentication type (default: {@code NONE})
         */
        @WithDefault("none")
        Type type();

        /**
         * API Key definitions (only used when {@code type=API_KEY}).
         * <p>
         * Each key is mapped to a set of roles. Example:
         *
         * <pre>
         * quarkus.flow.runner.security.api-keys."admin".secret=${ADMIN_KEY}
         * quarkus.flow.runner.security.api-keys."admin".roles=flow-admin
         *
         * quarkus.flow.runner.security.api-keys."invoker".secret=${INVOKER_KEY}
         * quarkus.flow.runner.security.api-keys."invoker".roles=flow-invoker
         * </pre>
         * <p>
         * Clients send the secret in the {@code Authorization: Bearer <secret>} header.
         * The key name (e.g., "admin", "invoker") is for configuration organization only
         * and is not sent by clients.
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
             * Namespaces allowed by these roles. An empty Set means all namespaces are allowed.
             *
             * @return set of namespaces
             */
            Set<String> namespaces();
        }

        /**
         * Namespace-level authorization (ABAC) configuration.
         * <p>
         * Controls access to workflows based on their namespace.
         * Users can only execute workflows in namespaces they are authorized for.
         */
        interface Namespace {
            /**
             * JWT claim name containing authorized namespace(s).
             * <p>
             * Used when {@code type=OIDC}. The claim can contain:
             * <ul>
             * <li>Single string value (e.g., {@code "my-namespace"})</li>
             * <li>Array of strings (e.g., {@code ["ns1", "ns2"]})</li>
             * </ul>
             * <p>
             * Example:
             *
             * <pre>
             * quarkus.flow.runner.security.namespace.claim=namespace
             * # or for multi-namespace support:
             * quarkus.flow.runner.security.namespace.claim=namespaces
             * </pre>
             *
             * @return the JWT claim name (default: {@code "namespace"})
             */
            @WithDefault("namespace")
            String claim();

            /**
             * Enable or disable namespace validation.
             * <p>
             * When {@code true}, requests are validated against the namespace in the
             * request path and the user's authorized namespaces (from JWT claim or
             * API key configuration).
             * <p>
             * When {@code false}, namespace validation is skipped (all users can
             * access all namespaces). Use only in development.
             *
             * @return {@code true} if namespace validation is enabled (default),
             *         {@code false} to disable
             */
            @WithDefault("true")
            boolean validate();
        }
    }

}
