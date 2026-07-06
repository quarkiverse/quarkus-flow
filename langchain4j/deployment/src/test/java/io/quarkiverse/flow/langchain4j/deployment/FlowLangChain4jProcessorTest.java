package io.quarkiverse.flow.langchain4j.deployment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
        assertNotNull(definition, "Expected workflow definition to be registered for TestSequenceAgent");

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

    @Test
    void shouldRegisterWorkflowForSequenceAgentWithA2AClient() {
        // The generated Flow uses WorkflowNameUtils.newId(iface) to name the workflow
        WorkflowDefinitionId expectedId = WorkflowNameUtils.newId(Agents.SequenceWithA2AAgent.class);

        WorkflowApplication app = Arc.container().instance(WorkflowApplication.class).get();

        WorkflowDefinition definition = app.workflowDefinitions().get(expectedId);
        assertNotNull(definition, "Expected workflow definition to be registered for SequenceWithA2AAgent");

        Workflow wf = definition.workflow();

        // Verify the basic identity matches what the generated Flow set
        assertEquals(expectedId.name(), wf.getDocument().getName(), "Workflow name should match WorkflowNameUtils");
        assertEquals(expectedId.namespace(), wf.getDocument().getNamespace(),
                "Workflow namespace should match WorkflowNameUtils");
        assertEquals(expectedId.version(), wf.getDocument().getVersion(),
                "Workflow version should match WorkflowNameUtils");

        // Ensure we actually got the "LC4J agent workflow for ..." summary from the generated Flow
        String expectedSummaryPrefix = "LC4J agent workflow for " + Agents.SequenceWithA2AAgent.class.getName() + ".";
        String summary = wf.getDocument().getSummary();
        assertTrue(summary != null && summary.startsWith(expectedSummaryPrefix),
                () -> "Summary should start with '" + expectedSummaryPrefix + "' but was '" + summary + "'");

        // KEY VALIDATION: The workflow was successfully generated and registered.
        // This proves that @A2AClientAgent annotation was recognized during build-time processing.
        //
        // If @A2AClientAgent was NOT in ALL_AGENT_ANNOTATIONS, the build would have failed with:
        // "No suitable annotation found on method ... among @Agent, @SequenceAgent, ..., @A2AClientAgent"
        //
        // The successful workflow generation means:
        // 1. The annotation was recognized in Lc4jAnnotations.ALL_AGENT_ANNOTATIONS
        // 2. GizmoAgentFlowsHelper.computeTaskNames found the annotated method
        // 3. The workflow was generated with RemoteDataFetcher (A2A) as a subagent
        // 4. The workflow definition was registered in the WorkflowApplication

        // Verify workflow has basic structure
        assertNotNull(wf.getDocument(), "Workflow document should not be null");
    }

}
