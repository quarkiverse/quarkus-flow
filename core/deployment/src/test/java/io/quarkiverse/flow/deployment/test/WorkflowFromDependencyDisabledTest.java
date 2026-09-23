package io.quarkiverse.flow.deployment.test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.arc.Arc;
import io.quarkus.test.QuarkusUnitTest;
import io.serverlessworkflow.impl.WorkflowDefinition;
import io.smallrye.common.annotation.Identifier;

public class WorkflowFromDependencyDisabledTest {

    private static final String APP_WORKFLOW = """
            document:
              dsl: '1.0.0'
              namespace: app
              name: app-hello
              version: '1.0.0'
            do:
              - appTask:
                  set:
                    message: from-app
            """;

    private static final String DEP_WORKFLOW = """
            document:
              dsl: '1.0.0'
              namespace: library
              name: dep-hello
              version: '1.0.0'
            do:
              - depTask:
                  set:
                    message: from-dependency
            """;

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .withApplicationRoot(jar -> jar
                    .addAsResource(new StringAsset(APP_WORKFLOW), "flow/app-hello.yaml"))
            .withAdditionalDependency(jar -> jar
                    .addAsResource(new StringAsset(DEP_WORKFLOW), "flow/dep-hello.yaml"))
            .overrideConfigKey("quarkus.flow.definitions.scan-dependencies", "false");

    @Test
    void application_workflow_is_still_discovered() {
        var handle = Arc.container().instance(WorkflowDefinition.class,
                Identifier.Literal.of("app:app-hello:1.0.0"));
        assertTrue(handle.isAvailable());
    }

    @Test
    void dependency_workflow_is_not_discovered() {
        var handle = Arc.container().instance(WorkflowDefinition.class,
                Identifier.Literal.of("library:dep-hello:1.0.0"));
        assertFalse(handle.isAvailable());
    }
}
