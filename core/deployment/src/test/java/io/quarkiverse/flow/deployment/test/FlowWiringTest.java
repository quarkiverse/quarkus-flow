package io.quarkiverse.flow.deployment.test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Optional;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.flow.FlowRegistry;
import io.quarkiverse.flow.FlowRunner;
import io.quarkus.arc.Arc;
import io.quarkus.test.QuarkusUnitTest;

public class FlowWiringTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClass(ExampleWorkflows.class));

    @Test
    void engine_and_registry_work() {
        FlowRegistry registry = Arc.container().instance(FlowRegistry.class).get();
        assertNotNull(registry.get("hello-world"));

        FlowRunner runner = Arc.container().instance(FlowRunner.class).get();
        assertNotNull(runner);

        Optional<String> out = assertDoesNotThrow(() -> runner.start("hello-world", Map.of("message", "hello world!"))
                .toCompletableFuture()
                .join()
                .asText());
        assertTrue(out.isPresent());
        assertEquals("hello world!", out.get());
    }

}
