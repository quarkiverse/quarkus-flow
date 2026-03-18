package io.quarkiverse.flow.langchain4j.workflow;

import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.client.ClientRequestFilter;
import jakarta.ws.rs.ext.Provider;

import org.eclipse.microprofile.config.ConfigProvider;
import org.slf4j.MDC;

import io.quarkiverse.flow.providers.MetadataPropagationRequestDecorator;

@Provider
@Priority(Priorities.HEADER_DECORATOR)
public final class FlowAgentCorrelationRequestFilter implements ClientRequestFilter {

    private final boolean enableMetadataPropagation;

    public FlowAgentCorrelationRequestFilter() {
        this.enableMetadataPropagation = ConfigProvider.getConfig()
                .getOptionalValue("quarkus.flow.http.client.enable-metadata-propagation", Boolean.class)
                .orElse(Boolean.TRUE);
    }

    @Override
    public void filter(ClientRequestContext requestContext) {
        if (!enableMetadataPropagation) {
            return;
        }

        String instanceId = MDC.get(FlowAgentCorrelation.MDC_INSTANCE);
        if (instanceId != null && !instanceId.isBlank()) {
            requestContext.getHeaders().putSingle(MetadataPropagationRequestDecorator.X_FLOW_INSTANCE_ID, instanceId);
        }

        String taskId = MDC.get(FlowAgentCorrelation.MDC_TASK_POS);
        if (taskId != null && !taskId.isBlank()) {
            requestContext.getHeaders().putSingle(MetadataPropagationRequestDecorator.X_FLOW_TASK_ID, taskId);
        }
    }
}
