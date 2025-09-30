package io.quarkiverse.flow.deployment.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.arc.Arc;
import io.quarkus.test.QuarkusUnitTest;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.impl.WorkflowDefinition;
import io.serverlessworkflow.impl.WorkflowModel;
import io.smallrye.common.annotation.Identifier;

public class FlowDefinitionInjectionTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClass(HelloWorldWorkflow.class)
                    .addClass(GreetingsWorkflow.class));

    @Test
    public void helloWorld_definition_is_produced_and_runs() {
        var helloHandle = Arc.container().instance(WorkflowDefinition.class,
                Identifier.Literal.of(HelloWorldWorkflow.class.getName()));
        assertTrue(helloHandle.isAvailable());

        WorkflowDefinition hello = helloHandle.get();

        // run: helloWorld sets the state to the input `.message` value
        WorkflowModel model = hello.instance(Map.of("message", "hello unit"))
                .start()
                .join();

        String out = model.as(String.class).orElseThrow();
        assertEquals("hello unit", out);
    }

    @Test
    public void greetings_definition_is_produced_and_runs() {
        var greetHandle = Arc.container().instance(WorkflowDefinition.class,
                Identifier.Literal.of(GreetingsWorkflow.class.getName()));
        assertTrue(greetHandle.isAvailable());

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
        var missing = Arc.container().instance(WorkflowDefinition.class, Identifier.Literal.of("doesNotExist"));
        assertFalse(missing.isAvailable());
    }
}
