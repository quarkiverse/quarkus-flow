package io.quarkiverse.flow.messaging.it;

import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.quarkus.runtime.LaunchMode;
import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.StartupEvent;

/**
 * Application handler to start the workflow when the application starts.
 * It will keep listening to the event and once it does, it runs the workflow and exit gracefully.
 * <p/>
 * To keep creating workflow instances per event, we should use the `on`, as
 * <a href="https://github.com/serverlessworkflow/specification/blob/main/dsl-reference.md#schedule">described in the spec</a>:
 * <p/>
 * "Schedule: Specifies the events that trigger the workflow execution."
 * <p/>
 * The "Schedule" implementation is not yet ready on our core CNCF engine. Once it does, we can rely on it instead of the
 * Application Lifecycle. See: <a href="https://github.com/serverlessworkflow/sdk-java/issues/847">https://github.com/serverlessworkflow/sdk-java/issues/847</a>
 * <p/>
 * The example below, works great for functions/serverless environments where the system process once and terminates.
 */
@ApplicationScoped
public class HelloMessagingWorkflowStarter {

    private static final Logger LOG = LoggerFactory.getLogger(HelloMessagingWorkflowStarter.class.getName());

    @Inject
    HelloMessagingFlow workflow;

    void onStart(@Observes StartupEvent ev) {
        workflow.instance(Map.of()).start().whenComplete((result, err) -> {
            if (err != null)
                LOG.error("Workflow failed", err);
            else
                LOG.info("Workflow completed output is {}", result.asMap());

            if (LaunchMode.NORMAL.isProduction()) {
                Quarkus.asyncExit();
            }
        });
    }

}
