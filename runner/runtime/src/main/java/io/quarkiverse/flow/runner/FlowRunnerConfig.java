package io.quarkiverse.flow.runner;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

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
 * quarkus.flow.runner.source.type=path
 * quarkus.flow.runner.source.path=/deployments/workflows
 *
 * # Use API Key authentication
 * quarkus.flow.runner.security.type=api-key
 * quarkus.flow.runner.security.api-keys."invoker".secret=${FLOW_API_KEY}
 * quarkus.flow.runner.security.api-keys."invoker".roles=flow-invoker
 *
 * # Callback settings
 * quarkus.flow.runner.callback.timeout=30s
 * quarkus.flow.runner.callback.max-retries=5
 * quarkus.flow.runner.callback.require-https=true
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
     * Callback delivery configuration for asynchronous executions.
     *
     * @return the callback configuration
     */
    Callback callback();

    /**
     * Limits and quotas configuration.
     *
     * @return the limits configuration
     */
    Limits limits();

    /**
     * Workflow definition source configuration.
     * <p>
     * Specifies where workflow definitions should be loaded from at startup.
     * The source is read once during application startup and can be manually
     * reloaded via management endpoints.
     */
    interface Source {

        /**
         * Default path to search for workflows in runtime when {@code quarkus.flow.runner.source.type=CLASSPATH}
         */
        String DEFAULT_CLASSPATH = "META-INF/workflows/";

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
         * Workflow definition source type.
         * <p>
         * Determines where workflow definitions are loaded from:
         * <ul>
         * <li>{@code CLASSPATH} - Load from {@code META-INF/workflows/} (or configurable path)</li>
         * <li>{@code PATH} - Load from filesystem directory specified in {@code path}</li>
         * </ul>
         *
         * @return the source type (default: {@code CLASSPATH})
         */
        @WithDefault("classpath")
        Type type();

        /**
         * Workflow definition source types.
         */
        enum Type {
            /**
             * Load workflow definitions from filesystem path.
             * Requires {@code quarkus.flow.runner.source.path} to be configured.
             */
            PATH,

            /**
             * Load workflow definitions from classpath.
             * Scans {@code META-INF/workflows/} by default.
             */
            CLASSPATH;
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
             * @return list of role names
             */
            List<String> roles();
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

    /**
     * Callback configuration for asynchronous workflow executions.
     * <p>
     * When a workflow is executed with {@code wait=false} and a callback URL,
     * the result is delivered via HTTP POST to the specified URL when execution
     * completes, fails, or is aborted.
     * <p>
     * <strong>Note:</strong> Callback configurations are stored in-memory only.
     * Running workflows will complete across restarts, but callback delivery
     * cannot be guaranteed if the application restarts before delivery.
     */
    interface Callback {
        /**
         * HTTP timeout per callback delivery attempt.
         * <p>
         * Applies to each individual retry attempt, not the total delivery time.
         * <p>
         * Example values:
         * <ul>
         * <li>{@code 10s} - 10 seconds (default)</li>
         * <li>{@code 5s} - 5 seconds for fast-failing webhooks</li>
         * <li>{@code 30s} - 30 seconds for slower external services</li>
         * </ul>
         *
         * @return the timeout duration (default: {@code "10s"})
         */
        @WithDefault("10s")
        String timeout();

        /**
         * Maximum number of delivery retry attempts.
         * <p>
         * Retries use exponential backoff:
         * <ul>
         * <li>Attempt 1: immediate</li>
         * <li>Attempt 2: +1s</li>
         * <li>Attempt 3: +2s</li>
         * <li>Attempt 4: +4s</li>
         * </ul>
         * <p>
         * After exhausting retries, the error is logged and the callback
         * configuration is removed from memory. Callback failures do NOT
         * fail the workflow execution.
         *
         * @return max retry attempts (default: {@code 3})
         */
        @WithDefault("3")
        int maxRetries();

        /**
         * Require HTTPS for callback URLs in production.
         * <p>
         * When {@code true}, callback URLs must use HTTPS scheme.
         * HTTP URLs are rejected with {@code 400 Bad Request}.
         * <p>
         * Set to {@code false} only in development/testing environments.
         *
         * @return {@code true} to enforce HTTPS (default), {@code false} to allow HTTP
         */
        @WithDefault("true")
        boolean requiresHttps();

        /**
         * Allowed callback URL host patterns.
         * <p>
         * Comma-separated list of host patterns. Supports wildcards:
         * <ul>
         * <li>{@code "*"} - Allow all hosts (default)</li>
         * <li>{@code "example.com"} - Exact match</li>
         * <li>{@code "*.example.com"} - Subdomain match</li>
         * <li>{@code "example.com,api.partner.com"} - Multiple allowed hosts</li>
         * </ul>
         * <p>
         * Use this to restrict callbacks to known external services.
         *
         * @return list of allowed host patterns (default: {@code ["*"]})
         */
        @WithDefault("*")
        List<String> allowedHosts();

        /**
         * Blocked IP ranges for SSRF prevention.
         * <p>
         * List of CIDR blocks to reject for callback URLs. Used to prevent
         * Server-Side Request Forgery (SSRF) attacks targeting internal infrastructure.
         * <p>
         * Default blocked ranges (RFC 1918 private networks + loopback):
         * <ul>
         * <li>{@code 10.0.0.0/8}</li>
         * <li>{@code 172.16.0.0/12}</li>
         * <li>{@code 192.168.0.0/16}</li>
         * <li>{@code 127.0.0.0/8}</li>
         * </ul>
         * <p>
         * Override this if you need to allow callbacks to internal services:
         *
         * <pre>
         * # Allow callbacks to 10.0.0.0/8 but block others
         * quarkus.flow.runner.callback.blocked-ips=172.16.0.0/12,192.168.0.0/16,127.0.0.0/8
         * </pre>
         *
         * @return list of blocked CIDR blocks
         */
        @WithDefault("10.0.0.0/8,172.16.0.0/12,192.168.0.0/16,127.0.0.0/8")
        List<String> blockedIps();
    }

    /**
     * Limits and quotas configuration.
     * <p>
     * Provides guardrails to prevent resource exhaustion and abuse.
     */
    interface Limits {
        /**
         * Maximum number of workflow definitions allowed per namespace.
         * <p>
         * Prevents a single namespace from consuming excessive registry space.
         * <p>
         * Values:
         * <ul>
         * <li>{@code -1} - Unlimited (default)</li>
         * <li>{@code > 0} - Maximum definitions per namespace</li>
         * </ul>
         * <p>
         * When the limit is reached, attempts to create new definitions in that
         * namespace fail with {@code 429 Too Many Requests}.
         *
         * @return max definitions per namespace, or {@code -1} for unlimited (default)
         */
        @WithDefault("-1")
        int maxDefinitionsPerNamespace();

        /**
         * Rate limit for workflow executions per minute.
         * <p>
         * Applies to {@code POST /runner/exec/*} endpoints.
         * <p>
         * Values:
         * <ul>
         * <li>{@code -1} - Unlimited (default)</li>
         * <li>{@code > 0} - Maximum executions per minute</li>
         * </ul>
         * <p>
         * When exceeded, requests fail with {@code 429 Too Many Requests}.
         * <p>
         * <strong>Note:</strong> This is a global limit across all users/namespaces.
         * Per-user or per-namespace limits are planned for future releases.
         *
         * @return max executions per minute, or {@code -1} for unlimited (default)
         */
        @WithDefault("-1")
        @WithName("rate-limit.execution.per-minute")
        int rateLimitExecutionPerMinute();
    }

}
