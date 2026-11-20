package io.quarkiverse.flow.deployment.test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;

public class WorkflowFromDependencyConflictTest {

    private static final String DEP_A_WORKFLOW = """
            document:
              dsl: '1.0.0'
              namespace: library
              name: conflicting
              version: '1.0.0'
            do:
              - depATask:
                  set:
                    message: from-dependency-a
            """;

    private static final String DEP_B_WORKFLOW = """
            document:
              dsl: '1.0.0'
              namespace: library
              name: conflicting
              version: '1.0.0'
            do:
              - depBTask:
                  set:
                    message: from-dependency-b
            """;

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .withApplicationRoot(jar -> {
            })
            .withAdditionalDependency(jar -> jar
                    .addAsResource(new StringAsset(DEP_A_WORKFLOW), "flow/dep-a.yaml"))
            .withAdditionalDependency(jar -> jar
                    .addAsResource(new StringAsset(DEP_B_WORKFLOW), "flow/dep-b.yaml"))
            .assertException(t -> assertTrue(
                    t.getMessage().contains("Duplicate workflow 'library:conflicting:1.0.0'"),
                    () -> "Unexpected failure: " + t.getMessage()));

    @Test
    void build_fails_when_two_dependencies_provide_the_same_workflow() {
        fail("Deployment should have failed due to duplicate workflows across dependencies");
    }
}
