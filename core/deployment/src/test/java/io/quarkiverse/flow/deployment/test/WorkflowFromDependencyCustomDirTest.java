package io.quarkiverse.flow.deployment.test;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.arc.Arc;
import io.quarkus.test.QuarkusUnitTest;
import io.serverlessworkflow.impl.WorkflowDefinition;
import io.smallrye.common.annotation.Identifier;

public class WorkflowFromDependencyCustomDirTest {

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
            .withApplicationRoot(jar -> {
            })
            .withAdditionalDependency(jar -> jar
                    .addAsResource(new StringAsset(DEP_WORKFLOW), "workflows/dep-hello.yaml"))
            .overrideConfigKey("quarkus.flow.definitions.dir", "workflows");

    @Test
    void dependency_workflow_is_discovered_in_custom_dir() {
        var handle = Arc.container().instance(WorkflowDefinition.class,
                Identifier.Literal.of("library:dep-hello:1.0.0"));
        assertTrue(handle.isAvailable());
    }
}
