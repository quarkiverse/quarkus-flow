package io.quarkiverse.flow.langchain4j.workflow;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Proxy;

import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import io.quarkiverse.flow.providers.MetadataPropagationRequestDecorator;

class FlowAgentCorrelationRequestFilterTest {

    @AfterEach
    void cleanup() {
        MDC.clear();
        System.clearProperty("quarkus.flow.http.client.enable-metadata-propagation");
    }

    @Test
    void withCorrelationSetsMdcDuringRunAndRestoresAfter() {
        MDC.put("pre", "existing");

        FlowAgentCorrelation.withCorrelation(null, "wf-1", "/task/1", "taskA", () -> {
            assertThat(MDC.get(FlowAgentCorrelation.MDC_INSTANCE)).isEqualTo("wf-1");
            assertThat(MDC.get(FlowAgentCorrelation.MDC_TASK_POS)).isEqualTo("/task/1");
            assertThat(MDC.get(FlowAgentCorrelation.MDC_TASK_NAME)).isEqualTo("taskA");
        });

        assertThat(MDC.get("pre")).isEqualTo("existing");
        assertThat(MDC.get(FlowAgentCorrelation.MDC_INSTANCE)).isNull();
        assertThat(MDC.get(FlowAgentCorrelation.MDC_TASK_POS)).isNull();
        assertThat(MDC.get(FlowAgentCorrelation.MDC_TASK_NAME)).isNull();

    }

    @Test
    void withCorrelationHandlesNullsWithoutMdcChanges() {
        FlowAgentCorrelation.withCorrelation(null, null, null, null, () -> {
            assertThat(MDC.get(FlowAgentCorrelation.MDC_INSTANCE)).isNull();
            assertThat(MDC.get(FlowAgentCorrelation.MDC_TASK_POS)).isNull();
            assertThat(MDC.get(FlowAgentCorrelation.MDC_TASK_NAME)).isNull();
        });

        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }

    @Test
    void requestFilterAddsHeadersWhenEnabledAndMdcPresent() {
        System.setProperty("quarkus.flow.http.client.enable-metadata-propagation", "true");
        MDC.put(FlowAgentCorrelation.MDC_INSTANCE, "wf-2");
        MDC.put(FlowAgentCorrelation.MDC_TASK_POS, "/task/2");

        FlowAgentCorrelationRequestFilter filter = new FlowAgentCorrelationRequestFilter();
        TestRequestContext ctx = new TestRequestContext();
        filter.filter(ctx.context());

        assertThat(ctx.headers.getFirst(MetadataPropagationRequestDecorator.X_FLOW_INSTANCE_ID)).isEqualTo("wf-2");
        assertThat(ctx.headers.getFirst(MetadataPropagationRequestDecorator.X_FLOW_TASK_ID)).isEqualTo("/task/2");
    }

    @Test
    void requestFilterSkipsWhenDisabled() {
        System.setProperty("quarkus.flow.http.client.enable-metadata-propagation", "false");
        MDC.put(FlowAgentCorrelation.MDC_INSTANCE, "wf-3");
        MDC.put(FlowAgentCorrelation.MDC_TASK_POS, "/task/3");

        FlowAgentCorrelationRequestFilter filter = new FlowAgentCorrelationRequestFilter();
        TestRequestContext ctx = new TestRequestContext();
        filter.filter(ctx.context());

        assertThat(ctx.headers).isEmpty();
    }

    @Test
    void requestFilterSkipsWhenMdcMissing() {
        System.setProperty("quarkus.flow.http.client.enable-metadata-propagation", "true");

        FlowAgentCorrelationRequestFilter filter = new FlowAgentCorrelationRequestFilter();
        TestRequestContext ctx = new TestRequestContext();
        filter.filter(ctx.context());

        assertThat(ctx.headers).isEmpty();
    }

    private static final class TestRequestContext {
        private final MultivaluedMap<String, Object> headers = new MultivaluedHashMap<>();

        ClientRequestContext context() {
            return (ClientRequestContext) Proxy.newProxyInstance(
                    ClientRequestContext.class.getClassLoader(),
                    new Class<?>[] { ClientRequestContext.class },
                    (proxy, method, args) -> {
                        if ("getHeaders".equals(method.getName())) {
                            return headers;
                        }
                        Class<?> returnType = method.getReturnType();
                        if (returnType == boolean.class) {
                            return false;
                        }
                        if (returnType == int.class) {
                            return 0;
                        }
                        return null;
                    });
        }
    }
}
