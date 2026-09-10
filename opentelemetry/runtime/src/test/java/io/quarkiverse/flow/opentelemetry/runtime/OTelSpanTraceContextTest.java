package io.quarkiverse.flow.opentelemetry.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.quarkiverse.flow.spi.observability.TraceCorrelationProvider.TraceContext;

@DisplayName("OTelWorkflowExecutionListener.toTraceContext span extraction")
class OTelSpanTraceContextTest {

    private static final String TRACE_ID = "0af7651916cd43dd8448eb211c80319c";
    private static final String SPAN_ID = "b7ad6b7169203331";

    @Test
    @DisplayName("null span yields no trace context")
    void null_span() {
        assertThat(OTelWorkflowExecutionListener.toTraceContext(null)).isNull();
    }

    @Test
    @DisplayName("an invalid span yields no trace context")
    void invalid_span() {
        assertThat(OTelWorkflowExecutionListener.toTraceContext(Span.getInvalid())).isNull();
    }

    @Test
    @DisplayName("a propagation-only span exposes its ids and an empty parent id")
    void wrapped_span() {
        Span span = Span.wrap(SpanContext.create(TRACE_ID, SPAN_ID, TraceFlags.getSampled(), TraceState.getDefault()));

        TraceContext tc = OTelWorkflowExecutionListener.toTraceContext(span);

        assertThat(tc).isEqualTo(new TraceContext(TRACE_ID, SPAN_ID, "true", ""));
    }

    @Test
    @DisplayName("an SDK child span exposes the sampled flag and the parent span id")
    void sdk_child_span() {
        Tracer tracer = SdkTracerProvider.builder().build().get("test");
        Span parent = tracer.spanBuilder("parent").startSpan();
        Span child = tracer.spanBuilder("child").setParent(Context.root().with(parent)).startSpan();
        try {
            TraceContext tc = OTelWorkflowExecutionListener.toTraceContext(child);

            assertThat(tc).isNotNull();
            assertThat(tc.traceId()).isEqualTo(child.getSpanContext().getTraceId());
            assertThat(tc.spanId()).isEqualTo(child.getSpanContext().getSpanId());
            assertThat(tc.sampled()).isEqualTo("true");
            assertThat(tc.parentId()).isEqualTo(parent.getSpanContext().getSpanId());
        } finally {
            child.end();
            parent.end();
        }
    }

    @Test
    @DisplayName("an unsampled span reports sampled=false")
    void unsampled_span() {
        Span span = Span.wrap(SpanContext.create(TRACE_ID, SPAN_ID, TraceFlags.getDefault(), TraceState.getDefault()));

        TraceContext tc = OTelWorkflowExecutionListener.toTraceContext(span);

        assertThat(tc.sampled()).isEqualTo("false");
    }
}