package io.quarkiverse.flow.config;

import java.util.Map;
import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;

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
 * Task-level routing:
 *
 * <pre>
 * quarkus.flow.http.client.workflow.myFlow.task.fetchCustomers.name = secureB
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
     * Workflow-level HTTP client routing configuration.
     * <p>
     * Each entry is keyed by the workflow id and maps to:
     *
     * <pre>
     * quarkus.flow.http.client.workflow.&lt;workflowId&gt;.name=&lt;clientName&gt;
     * quarkus.flow.http.client.workflow.&lt;workflowId&gt;.task.&lt;taskName&gt;.name=&lt;clientName&gt;
     * </pre>
     *
     * @return the map of workflow routing configurations
     */
    Map<String, WorkflowRoutingConfig> workflow();

    /**
     * Routing configuration for a single workflow.
     * <p>
     * Allows selecting a default client for the workflow and
     * overriding it on a per-task basis.
     */
    interface WorkflowRoutingConfig {

        /**
         * Client name to use for all HTTP/OpenAPI tasks in this workflow
         * when there is no task-level override.
         * <p>
         * Property:
         *
         * <pre>
         * quarkus.flow.http.client.workflow.&lt;workflowId&gt;.name=&lt;clientName&gt;
         * </pre>
         *
         * @return the client name for this workflow, if configured
         */
        Optional<String> name();

        /**
         * Per-task client overrides for this workflow.
         * <p>
         * Each entry is keyed by the task name and maps to:
         *
         * <pre>
         * quarkus.flow.http.client.workflow.&lt;workflowId&gt;.task.&lt;taskName&gt;.name=&lt;clientName&gt;
         * </pre>
         *
         * @return the map of per-task routing configurations
         */
        Map<String, TaskRoutingConfig> task();
    }

    /**
     * Routing configuration for a single workflow task.
     * <p>
     * Allows selecting a specific client for one task within
     * a workflow.
     */
    interface TaskRoutingConfig {

        /**
         * Client name to use for this specific task.
         * <p>
         * Property:
         *
         * <pre>
         * quarkus.flow.http.client.workflow.&lt;workflowId&gt;.task.&lt;taskName&gt;.name=&lt;clientName&gt;
         * </pre>
         *
         * @return the client name for this task, if configured
         */
        Optional<String> name();
    }
}
