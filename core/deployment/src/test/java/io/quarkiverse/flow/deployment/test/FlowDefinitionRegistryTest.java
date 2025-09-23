package io.quarkiverse.flow.deployment.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.flow.FlowRegistry;
import io.quarkus.arc.Arc;
import io.quarkus.test.QuarkusUnitTest;
import io.serverlessworkflow.api.types.Workflow;

public class FlowDefinitionRegistryTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClass(ExampleWorkflows.class));

    @Test
    public void registry_is_populated_with_hello_world_workflow() {
        FlowRegistry registry = Arc.container().instance(FlowRegistry.class).get();
        assertNotNull(registry);

        Workflow wf = registry.get("hello-world");
        assertNotNull(wf);
        assertEquals("hello-world", wf.getDocument().getName());
    }

    @Test
    public void registry_is_populated_with_greetings_workflow() {
        FlowRegistry registry = Arc.container().instance(FlowRegistry.class).get();
        assertNotNull(registry);

        Workflow wf = registry.get("io.quarkiverse.flow.deployment.test.ExampleWorkflows", "greetings");
        assertNotNull(wf);
        assertEquals("greetings", wf.getDocument().getName());
    }
}
