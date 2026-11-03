package io.quarkiverse.flow.deployment.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;

/**
 * The same workflow identifier defined twice inside a single dependency must report that dependency,
 * not claim that multiple dependencies are involved.
 */
public class WorkflowFromDependencySameArchiveConflictTest {

    private static final String WORKFLOW = """
            document:
              dsl: '1.0.0'
              namespace: library
              name: duplicated
              version: '1.0.0'
            do:
              - setMessage:
                  set:
                    message: %s
            """;

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .withApplicationRoot(jar -> {
            })
            .withAdditionalDependency(jar -> jar
                    .addAsResource(new StringAsset(WORKFLOW.formatted("first")), "flow/first.yaml")
                    .addAsResource(new StringAsset(WORKFLOW.formatted("second")), "flow/second.yaml"))
            .assertException(t -> assertThat(t.getMessage())
                    .contains("Duplicate workflow 'library:duplicated:1.0.0' defined twice in dependency")
                    .doesNotContain("multiple dependencies"));

    @Test
    @DisplayName("build_fails_when_one_dependency_defines_the_same_workflow_twice")
    void build_fails_when_one_dependency_defines_the_same_workflow_twice() {
        fail("Deployment should have failed due to a duplicated workflow inside the same dependency");
    }
}
