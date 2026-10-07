package io.quarkiverse.flow.deployment.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.arc.Arc;
import io.quarkus.test.QuarkusUnitTest;
import io.serverlessworkflow.impl.WorkflowDefinition;
import io.smallrye.common.annotation.Identifier;

public class WorkflowFromDependencyOverrideTest {

    private static final String APP_WORKFLOW = """
            document:
              dsl: '1.0.0'
              namespace: default
              name: shared
              version: '1.0.0'
            do:
              - appTask:
                  set:
                    message: from-app
            """;

    private static final String DEP_WORKFLOW = """
            document:
              dsl: '1.0.0'
              namespace: default
              name: shared
              version: '1.0.0'
            do:
              - depTask:
                  set:
                    message: from-dependency
            """;

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .withApplicationRoot(jar -> jar
                    .addAsResource(new StringAsset(APP_WORKFLOW), "flow/shared.yaml"))
            .withAdditionalDependency(jar -> jar
                    .addAsResource(new StringAsset(DEP_WORKFLOW), "flow/dep-shared.yaml"));

    @Test
    void application_workflow_overrides_dependency_workflow() {
        var handle = Arc.container().instance(WorkflowDefinition.class,
                Identifier.Literal.of("default:shared:1.0.0"));
        assertTrue(handle.isAvailable());

        // The application's definition must win: its first task is named appTask
        var firstTask = handle.get().workflow().getDo().get(0);
        assertEquals("appTask", firstTask.getName());
    }
}
