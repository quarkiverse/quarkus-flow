package io.quarkiverse.flow.deployment.test;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;

import jakarta.enterprise.util.AnnotationLiteral;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

// Qualifier + SDK types
import io.quarkiverse.flow.FlowDefinition;
import io.quarkus.arc.Arc;
import io.quarkus.test.QuarkusUnitTest;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.impl.WorkflowDefinition;
import io.serverlessworkflow.impl.WorkflowModel;

public class FlowDefinitionInjectionTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClass(ExampleWorkflows.class));

    static final class FlowDefLiteral extends AnnotationLiteral<FlowDefinition> implements FlowDefinition {
        private final String value;

        FlowDefLiteral(String value) {
            this.value = value;
        }

        @Override
        public String value() {
            return value;
        }
    }

    @Test
    public void helloWorld_definition_is_produced_and_runs() {
        var helloHandle = Arc.container().instance(WorkflowDefinition.class, new FlowDefLiteral("helloWorld"));
        assertTrue(helloHandle.isAvailable(), "@FlowDefinition(\"helloWorld\") should be available");

        WorkflowDefinition hello = helloHandle.get();

        // run: helloWorld sets the state to the input `.message` value
        WorkflowModel model = hello.instance(Map.of("message", "hello unit"))
                .start()
                .join();

        String out = model.as(String.class).orElseThrow();
        assertEquals("hello unit", out, "helloWorld should echo the input message");
    }

    @Test
    public void greetings_definition_is_produced_and_runs() {
        var greetHandle = Arc.container().instance(WorkflowDefinition.class, new FlowDefLiteral("greetings"));
        assertTrue(greetHandle.isAvailable(), "@FlowDefinition(\"greetings\") should be available");

        WorkflowDefinition greetings = greetHandle.get();

        // run: pick the english branch
        WorkflowModel model = greetings.instance(Map.of("language", "english", "name", "Ada"))
                .start()
                .join();

        Workflow wf = greetings.workflow();
        assertNotNull(wf);

        // greetings builds an object like { message = "Howdy " + name }
        Map<String, Object> out = model.asMap().orElseThrow();
        assertEquals("Howdy Ada", out.get("message"));
    }

    @Test
    public void unknown_definition_is_not_available() {
        var missing = Arc.container().instance(WorkflowDefinition.class, new FlowDefLiteral("doesNotExist"));
        assertFalse(missing.isAvailable(), "unknown @FlowDefinition should not be resolvable");
    }
}
