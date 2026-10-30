package io.quarkiverse.flow.opentelemetry.runtime;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_AGENT_DESCRIPTION;
import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_AGENT_NAME;
import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_OPERATION_NAME;
import static io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GenAiOperationNameIncubatingValues.INVOKE_AGENT;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.quarkiverse.flow.internal.WorkflowAgentMetadata;
import io.serverlessworkflow.api.types.CallFunction;
import io.serverlessworkflow.api.types.Document;
import io.serverlessworkflow.api.types.TaskBase;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.api.types.WorkflowMetadata;
import io.serverlessworkflow.impl.TaskContext;
import io.serverlessworkflow.impl.WorkflowApplication;
import io.serverlessworkflow.impl.WorkflowDefinitionData;
import io.serverlessworkflow.impl.WorkflowDefinitionId;
import io.serverlessworkflow.impl.WorkflowPosition;

class SpanBuilderFactoryTest {

    private static final String INSTANCE_ID = "instance-1";

    private final InMemorySpanExporter exporter = InMemorySpanExporter.create();
    private final SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
            .addSpanProcessor(SimpleSpanProcessor.create(exporter))
            .build();

    private SpanBuilderFactory factory;

    @BeforeEach
    void setUp() {
        factory = new SpanBuilderFactory();
        factory.tracer = tracerProvider.get("test");
    }

    @AfterEach
    void tearDown() {
        tracerProvider.close();
    }

    @Test
    @DisplayName("test_execute_span_has_genai_attributes_when_agent_marker_present")
    void test_execute_span_has_genai_attributes_when_agent_marker_present() {
        var definition = definition(document()
                .withSummary("Process an incoming email")
                .withMetadata(agentMetadata("process")));

        assertThat(executeSpan(definition))
                .hasAttribute(GEN_AI_OPERATION_NAME, INVOKE_AGENT)
                .hasAttribute(GEN_AI_AGENT_NAME, "process")
                .hasAttribute(GEN_AI_AGENT_DESCRIPTION, "Process an incoming email");
    }

    @Test
    @DisplayName("test_execute_span_omits_agent_description_when_summary_is_blank")
    void test_execute_span_omits_agent_description_when_summary_is_blank() {
        var definition = definition(document()
                .withSummary(" ")
                .withMetadata(agentMetadata("process")));

        assertThat(executeSpan(definition))
                .hasAttribute(GEN_AI_OPERATION_NAME, INVOKE_AGENT)
                .hasAttribute(GEN_AI_AGENT_NAME, "process")
                .hasAttributesSatisfying(attributes -> assertThat(attributes)
                        .doesNotContainKey(GEN_AI_AGENT_DESCRIPTION));
    }

    @Test
    @DisplayName("test_execute_span_has_no_genai_attributes_for_plain_workflow")
    void test_execute_span_has_no_genai_attributes_for_plain_workflow() {
        var definition = definition(document()
                .withSummary("A plain workflow")
                .withMetadata(new WorkflowMetadata().withAdditionalProperty("some.key", "value")));

        assertThat(executeSpan(definition))
                .hasAttributesSatisfying(attributes -> assertThat(attributes)
                        .doesNotContainKey(GEN_AI_OPERATION_NAME)
                        .doesNotContainKey(GEN_AI_AGENT_NAME)
                        .doesNotContainKey(GEN_AI_AGENT_DESCRIPTION));
    }

    @Test
    @DisplayName("test_execute_span_has_no_genai_attributes_without_metadata")
    void test_execute_span_has_no_genai_attributes_without_metadata() {
        assertThat(executeSpan(definition(document())))
                .hasAttributesSatisfying(attributes -> assertThat(attributes)
                        .doesNotContainKey(GEN_AI_OPERATION_NAME)
                        .doesNotContainKey(GEN_AI_AGENT_NAME));
    }

    @Test
    @DisplayName("test_blank_agent_marker_is_ignored")
    void test_blank_agent_marker_is_ignored() {
        var definition = definition(document().withMetadata(agentMetadata("  ")));

        assertThat(executeSpan(definition))
                .hasAttributesSatisfying(attributes -> assertThat(attributes)
                        .doesNotContainKey(GEN_AI_OPERATION_NAME)
                        .doesNotContainKey(GEN_AI_AGENT_NAME));
    }

    @Test
    @DisplayName("test_non_string_agent_marker_is_ignored")
    void test_non_string_agent_marker_is_ignored() {
        var definition = definition(document()
                .withMetadata(new WorkflowMetadata().withAdditionalProperty(WorkflowAgentMetadata.META_AGENT_NAME, 42)));

        assertThat(executeSpan(definition))
                .hasAttributesSatisfying(attributes -> assertThat(attributes)
                        .doesNotContainKey(GEN_AI_OPERATION_NAME)
                        .doesNotContainKey(GEN_AI_AGENT_NAME));
    }

    @Test
    @DisplayName("test_resumed_execute_span_has_genai_attributes")
    void test_resumed_execute_span_has_genai_attributes() {
        var definition = definition(document().withMetadata(agentMetadata("process")));

        factory.newWorkflowExecuteSpan(definition, INSTANCE_ID, Context.root(), true)
                .startSpan()
                .end();

        assertThat(finishedSpan())
                .hasAttribute(AttributeKey.booleanKey(SpanConstants.FLOW_WF_EXECUTION_IS_RESUMPTION_ATTR), true)
                .hasAttribute(GEN_AI_OPERATION_NAME, INVOKE_AGENT)
                .hasAttribute(GEN_AI_AGENT_NAME, "process");
    }

    @Test
    @DisplayName("test_create_span_is_never_stamped")
    void test_create_span_is_never_stamped() {
        var definition = definition(document().withMetadata(agentMetadata("process")));

        factory.newWorkflowCreateSpan(definition, INSTANCE_ID, Context.root())
                .startSpan()
                .end();

        assertThat(finishedSpan())
                .hasName("workflow.create my-agent")
                .hasAttributesSatisfying(attributes -> assertThat(attributes)
                        .doesNotContainKey(GEN_AI_OPERATION_NAME)
                        .doesNotContainKey(GEN_AI_AGENT_NAME));
    }

    @Test
    @DisplayName("test_agent_task_span_is_typed_invoke_agent")
    void test_agent_task_span_is_typed_invoke_agent() {
        var task = new CallFunction();
        WorkflowAgentMetadata.markAgentTask(task);

        assertThat(taskSpan(task, "triageAgent"))
                .hasName("task.execute triageAgent")
                .hasAttribute(GEN_AI_OPERATION_NAME, INVOKE_AGENT)
                .hasAttribute(GEN_AI_AGENT_NAME, "triageAgent")
                .hasAttributesSatisfying(attributes -> assertThat(attributes)
                        .doesNotContainKey(GEN_AI_AGENT_DESCRIPTION));
    }

    @Test
    @DisplayName("test_plain_task_span_is_not_typed")
    void test_plain_task_span_is_not_typed() {
        assertThat(taskSpan(new CallFunction(), "plainTask"))
                .hasAttributesSatisfying(attributes -> assertThat(attributes)
                        .doesNotContainKey(GEN_AI_OPERATION_NAME)
                        .doesNotContainKey(GEN_AI_AGENT_NAME));
    }

    private SpanData taskSpan(TaskBase task, String taskName) {
        var position = mock(WorkflowPosition.class);
        when(position.jsonPointer()).thenReturn("/do/0/" + taskName);

        var taskContext = mock(TaskContext.class);
        when(taskContext.task()).thenReturn(task);
        when(taskContext.taskName()).thenReturn(taskName);
        when(taskContext.position()).thenReturn(position);

        factory.newTaskSpan("task.execute " + taskName, definition(document()), INSTANCE_ID, taskContext, Context.root())
                .startSpan()
                .end();
        return finishedSpan();
    }

    private SpanData executeSpan(WorkflowDefinitionData definition) {
        factory.newWorkflowExecuteSpan(definition, INSTANCE_ID, Context.root(), false)
                .startSpan()
                .end();
        return finishedSpan();
    }

    private SpanData finishedSpan() {
        return exporter.getFinishedSpanItems()
                .stream()
                .findFirst()
                .orElseThrow();
    }

    private static Document document() {
        return new Document()
                .withNamespace("org-example-agents")
                .withName("my-agent")
                .withVersion("1.0.0");
    }

    private static WorkflowMetadata agentMetadata(String agentName) {
        return new WorkflowMetadata().withAdditionalProperty(WorkflowAgentMetadata.META_AGENT_NAME, agentName);
    }

    private static WorkflowDefinitionData definition(Document document) {
        var application = mock(WorkflowApplication.class);
        when(application.id()).thenReturn("test-app");

        var definition = mock(WorkflowDefinitionData.class);
        when(definition.workflow()).thenReturn(new Workflow().withDocument(document));
        when(definition.application()).thenReturn(application);
        when(definition.id()).thenReturn(new WorkflowDefinitionId(document.getNamespace(), document.getName(),
                document.getVersion()));
        return definition;
    }
}
