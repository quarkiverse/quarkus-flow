package io.quarkiverse.flow.config;

import java.util.Map;
import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.serverlessworkflow.impl.TaskContext;
import io.serverlessworkflow.impl.WorkflowInstance;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/**
 * HTTP/OpenAPI client configuration for Quarkus Flow.
 * <p>
 * Prefix: {@code quarkus.flow.http.client}
 * <p>
 * Shapes:
 * <p>
 * Default client (inherits {@link HttpClientConfig}):
 *
 * <pre>
 * quarkus.flow.http.client.connect-timeout=5000
 * quarkus.flow.http.client.read-timeout=10000
 * quarkus.flow.http.client.logging.scope=request-response
 * </pre>
 * <p>
 * Named clients:
 *
 * <pre>
 * quarkus.flow.http.client.named.secureA.connect-timeout=3000
 * quarkus.flow.http.client.named.secureA.user-agent=MyCompanyBot/1.0
 * </pre>
 * <p>
 * Workflow-level routing:
 *
 * <pre>
 * quarkus.flow.http.client.workflow.myFlow.name = secureA
 * </pre>
 * <p>
 * Task-level routing (short key — dotted composite key):
 *
 * <pre>
 * quarkus.flow.http.client.workflow."myFlow.task.fetchCustomers".name = secureB
 * </pre>
 * <p>
 * Medium key (namespaced):
 *
 * <pre>
 * quarkus.flow.http.client.workflow."acme\:orders".name = secureA
 * </pre>
 * <p>
 * Full key (versioned):
 *
 * <pre>
 * quarkus.flow.http.client.workflow."acme\:orders\:1.0.0".name = secureA
 * </pre>
 */
@ConfigMapping(prefix = "quarkus.flow.http.client")
@ConfigRoot(phase = ConfigPhase.RUN_TIME)
public interface FlowHttpConfig extends HttpClientConfig {

    /**
     * Named HTTP clients, keyed by client name.
     * <p>
     * Each entry maps to:
     *
     * <pre>
     * quarkus.flow.http.client.named.&lt;name&gt;.&lt;property&gt;
     * </pre>
     *
     * For example:
     *
     * <pre>
     * quarkus.flow.http.client.named.secureA.connect-timeout=3000
     * quarkus.flow.http.client.named.secureA.user-agent=MyCompanyBot/1.0
     * </pre>
     *
     * @return the map of named HTTP client configurations
     */
    Map<String, HttpClientConfig> named();

    /**
     * Per-workflow/task HTTP client routing overrides, keyed by composite identifier.
     * <p>
     * Keys follow the unified naming convention:
     * <ul>
     * <li>{@code <name>} — workflow-level short (99% use case)</li>
     * <li>{@code "<namespace>:<name>"} — workflow-level medium</li>
     * <li>{@code "<namespace>:<name>:<version>"} — workflow-level full</li>
     * <li>{@code "<name>.task.<taskName>"} — task-level short</li>
     * <li>{@code "<namespace>:<name>.task.<taskName>"} — task-level medium</li>
     * <li>{@code "<namespace>:<name>:<version>.task.<taskName>"} — task-level full</li>
     * </ul>
     *
     * @return the map of workflow/task routing configurations
     */
    Map<String, ClientOverrideConfig> workflow();

    /**
     * Routes a workflow or task to a named HTTP client.
     */
    interface ClientOverrideConfig {

        /**
         * The named HTTP client to use, configured under {@code quarkus.flow.http.client.named.<name>}.
         *
         * @return the client name, if configured
         */
        Optional<String> name();
    }

    /**
     * Whether the HTTP Client should propagate through HTTP headers the correlation metadata.
     * <p>
     * The correlation metadata are:
     * <ul>
     * <li><code>X-Flow-Instance-Id</code> the instance ID see {@link WorkflowInstance#id()}</li>
     * <li><code>X-Flow-Task-Id</code> the task's position that where the request was made, see
     * {@link TaskContext#position()}</li>
     * </ul>
     */
    @WithDefault("true")
    Optional<Boolean> enableMetadataPropagation();

}
