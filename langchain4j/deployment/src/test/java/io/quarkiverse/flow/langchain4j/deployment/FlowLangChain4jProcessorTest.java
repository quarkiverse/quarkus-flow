package io.quarkiverse.flow.langchain4j.deployment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.flow.internal.WorkflowNameUtils;
import io.quarkiverse.flow.internal.WorkflowRegistry;
import io.quarkiverse.flow.langchain4j.Agents;
import io.quarkus.test.QuarkusUnitTest;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.impl.WorkflowDefinitionId;

/**
 * Integration-style test for the FlowLangChain4jProcessor + recorder:
 * <p>
 * - An LC4J @AiService interface with a @SequenceAgent method is on the app classpath
 * - io.quarkiverse.langchain4j.agentic creates DetectedAiAgentBuildItem
 * - FlowLangChain4jProcessor turns that into FlowAgenticWorkflowBuildItem
 * - FlowLangChain4jWorkflowRecorder registers a placeholder Workflow in WorkflowRegistry
 */
public class FlowLangChain4jProcessorTest {
    @RegisterExtension
    static final QuarkusUnitTest test = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(Agents.class));

    @Test
    void shouldRegisterPlaceholderWorkflowForSequenceAgent() {
        // The recorder uses WorkflowNameUtils.newId(iface) to name the workflow
        WorkflowDefinitionId expectedId = WorkflowNameUtils.newId(Agents.StoryCreatorWithConfigurableStyleEditor.class);

        WorkflowRegistry registry = WorkflowRegistry.current();

        Optional<Workflow> maybeWorkflow = registry.lookupDescriptor(expectedId);

        assertTrue(maybeWorkflow.isPresent(), "Expected placeholder workflow to be registered for TestSequenceAgent");

        Workflow wf = maybeWorkflow.get();

        // Verify the basic identity matches what the recorder set
        assertEquals(expectedId.name(), wf.getDocument().getName(), "Workflow name should match WorkflowNameUtils");
        assertEquals(expectedId.namespace(), wf.getDocument().getNamespace(),
                "Workflow namespace should match WorkflowNameUtils");
        assertEquals(expectedId.version(), wf.getDocument().getVersion(),
                "Workflow version should match WorkflowNameUtils");

        // Ensure we actually got the "LC4J agent workflow for ..." summary from the recorder
        String expectedSummaryPrefix = "LC4J agent workflow for "
                + Agents.StoryCreatorWithConfigurableStyleEditor.class.getSimpleName() + ".";
        String summary = wf.getDocument().getSummary();
        assertTrue(summary != null && summary.startsWith(expectedSummaryPrefix),
                () -> "Summary should start with '" + expectedSummaryPrefix + "' but was '" + summary + "'");

        // And that MethodInputJsonSchema ran and attached an input section
        // (we don't over-specify the structure, just ensure it's there)
        assertNotNull(wf.getInput(), "Workflow input should not be null (schema should be attached)");
    }

}
