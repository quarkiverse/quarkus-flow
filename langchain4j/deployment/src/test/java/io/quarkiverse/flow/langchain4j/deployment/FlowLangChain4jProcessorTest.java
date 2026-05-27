package io.quarkiverse.flow.langchain4j.deployment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.flow.internal.WorkflowNameUtils;
import io.quarkiverse.flow.langchain4j.Agents;
import io.quarkus.arc.Arc;
import io.quarkus.test.QuarkusUnitTest;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.impl.WorkflowApplication;
import io.serverlessworkflow.impl.WorkflowDefinition;
import io.serverlessworkflow.impl.WorkflowDefinitionId;

/**
 * Integration-style test for the FlowLangChain4jProcessor:
 * <p>
 * - An LC4J @AiService interface with a @SequenceAgent method is on the app classpath
 * - io.quarkiverse.langchain4j.agentic creates DetectedAiAgentBuildItem
 * - FlowLangChain4jProcessor turns that into FlowAgenticWorkflowBuildItem
 * - Generated AgenticFlow classes are registered as WorkflowDefinitions
 */
public class FlowLangChain4jProcessorTest {
    @RegisterExtension
    static final QuarkusUnitTest test = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(Agents.class)
                    .addAsResource(new StringAsset("quarkus.http.test-port=0"), "application.properties"));

    @Test
    void shouldRegisterPlaceholderWorkflowForSequenceAgent() {
        // The generated Flow uses WorkflowNameUtils.newId(iface) to name the workflow
        WorkflowDefinitionId expectedId = WorkflowNameUtils.newId(Agents.StoryCreatorWithConfigurableStyleEditor.class);

        WorkflowApplication app = Arc.container().instance(WorkflowApplication.class).get();

        WorkflowDefinition definition = app.workflowDefinitions().get(expectedId);
        assertTrue(definition != null, "Expected workflow definition to be registered for TestSequenceAgent");

        Workflow wf = definition.workflow();

        // Verify the basic identity matches what the generated Flow set
        assertEquals(expectedId.name(), wf.getDocument().getName(), "Workflow name should match WorkflowNameUtils");
        assertEquals(expectedId.namespace(), wf.getDocument().getNamespace(),
                "Workflow namespace should match WorkflowNameUtils");
        assertEquals(expectedId.version(), wf.getDocument().getVersion(),
                "Workflow version should match WorkflowNameUtils");

        // Ensure we actually got the "LC4J agent workflow for ..." summary from the generated Flow
        String expectedSummaryPrefix = "LC4J agent workflow for "
                + Agents.StoryCreatorWithConfigurableStyleEditor.class.getName() + ".";
        String summary = wf.getDocument().getSummary();
        assertTrue(summary != null && summary.startsWith(expectedSummaryPrefix),
                () -> "Summary should start with '" + expectedSummaryPrefix + "' but was '" + summary + "'");

        // And that MethodInputJsonSchema ran and attached an input section
        // (we don't over-specify the structure, just ensure it's there)
        // TODO: add the schema
        // assertNotNull(wf.getInput(), "Workflow input should not be null (schema should be attached)");
    }

}
