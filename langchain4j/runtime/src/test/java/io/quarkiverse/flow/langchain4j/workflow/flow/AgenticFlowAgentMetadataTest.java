package io.quarkiverse.flow.langchain4j.workflow.flow;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import jakarta.enterprise.inject.Vetoed;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.quarkiverse.flow.internal.WorkflowAgentMetadata;
import io.quarkiverse.flow.internal.WorkflowInvocationMetadata;
import io.serverlessworkflow.api.types.Workflow;

@DisplayName("AgenticFlow agent metadata")
class AgenticFlowAgentMetadataTest {

    interface TestAgent {
        String process(String email);
    }

    @Test
    @DisplayName("test_agent_metadata_is_set_without_devui_invoker_overrides")
    void test_agent_metadata_is_set_without_devui_invoker_overrides() {
        // Production shape: Gizmo only generates invokerMethodName()/invokerMethodParams() in dev and test mode
        var workflow = new TestSequentialAgenticFlow("process").descriptor();

        assertThat(WorkflowAgentMetadata.agentNameOf(workflow))
                .contains("process");
        assertThat(workflow.getDocument().getMetadata().getAdditionalProperties())
                .containsEntry(WorkflowAgentMetadata.META_AGENT_NAME, "process")
                .doesNotContainKeys(WorkflowInvocationMetadata.META_INVOKER_BEAN,
                        WorkflowInvocationMetadata.META_INVOKER_METHOD);
    }

    @Test
    @DisplayName("test_agent_metadata_is_kept_alongside_devui_invoker_metadata")
    void test_agent_metadata_is_kept_alongside_devui_invoker_metadata() {
        // Dev and test shape: Gizmo also generates the DevUI invoker methods
        var workflow = new DevUiTestSequentialAgenticFlow("process").descriptor();

        assertThat(workflow.getDocument().getMetadata().getAdditionalProperties())
                .containsEntry(WorkflowAgentMetadata.META_AGENT_NAME, "process")
                .containsEntry(WorkflowInvocationMetadata.META_INVOKER_BEAN, TestAgent.class.getName())
                .containsEntry(WorkflowInvocationMetadata.META_INVOKER_METHOD, "process");
    }

    @Test
    @DisplayName("test_default_agent_name_is_the_agent_class_simple_name")
    void test_default_agent_name_is_the_agent_class_simple_name() {
        var flow = new TestSequentialAgenticFlow(null);

        assertThat(flow.agentName())
                .isEqualTo("TestAgent");
        assertThat(WorkflowAgentMetadata.agentNameOf(flow.descriptor()))
                .contains("TestAgent");
    }

    @Test
    @DisplayName("test_agent_metadata_is_absent_on_plain_workflow")
    void test_agent_metadata_is_absent_on_plain_workflow() {
        assertThat(WorkflowAgentMetadata.agentNameOf(new Workflow()))
                .isEmpty();
        assertThat(WorkflowAgentMetadata.agentNameOf(null))
                .isEmpty();
    }

    // @Vetoed keeps Flow discovery from registering these test flows in the @QuarkusTest application
    @Vetoed
    private static class TestSequentialAgenticFlow extends SequentialAgenticFlow {

        private final String agentName;

        TestSequentialAgenticFlow(String agentName) {
            this.agentName = agentName;
        }

        @Override
        public String agentClassName() {
            return TestAgent.class.getName();
        }

        @Override
        public String agentName() {
            return (agentName != null) ? agentName : super.agentName();
        }

        @Override
        protected List<String> subAgentTaskNames() {
            return List.of("classify");
        }
    }

    @Vetoed
    private static class DevUiTestSequentialAgenticFlow extends TestSequentialAgenticFlow {

        DevUiTestSequentialAgenticFlow(String agentName) {
            super(agentName);
        }

        @Override
        protected String invokerMethodName() {
            return "process";
        }

        @Override
        protected String[] invokerMethodParams() {
            return new String[] { String.class.getName() };
        }
    }
}
