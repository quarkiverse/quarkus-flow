package io.quarkiverse.flow.deployment.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.arc.Arc;
import io.quarkus.test.QuarkusUnitTest;
import io.serverlessworkflow.impl.WorkflowDefinition;
import io.smallrye.common.annotation.Identifier;

/**
 * Different archives may ship workflow files under the same classpath path. Each workflow must still be
 * loaded from its own definition at runtime, not from whichever file wins on the classpath.
 */
public class WorkflowFromDependencySamePathTest {

    private static final String APP_WORKFLOW = workflow("app", "shared-app", "from-app");
    private static final String DEP_A_WORKFLOW = workflow("library-a", "shared-a", "from-dependency-a");
    private static final String DEP_B_WORKFLOW = workflow("library-b", "shared-b", "from-dependency-b");

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .withApplicationRoot(jar -> jar
                    .addAsResource(new StringAsset(APP_WORKFLOW), "flow/shared.yaml"))
            .withAdditionalDependency(jar -> jar
                    .addAsResource(new StringAsset(DEP_A_WORKFLOW), "flow/shared.yaml"))
            .withAdditionalDependency(jar -> jar
                    .addAsResource(new StringAsset(DEP_B_WORKFLOW), "flow/shared.yaml"));

    @Test
    @DisplayName("application_workflow_runs_its_own_definition")
    void application_workflow_runs_its_own_definition() {
        assertThat(run("app:shared-app:1.0.0")).containsEntry("message", "from-app");
    }

    @Test
    @DisplayName("dependency_workflows_sharing_a_path_run_their_own_definitions")
    void dependency_workflows_sharing_a_path_run_their_own_definitions() {
        assertThat(run("library-a:shared-a:1.0.0")).containsEntry("message", "from-dependency-a");
        assertThat(run("library-b:shared-b:1.0.0")).containsEntry("message", "from-dependency-b");
    }

    private static Map<String, Object> run(String identifier) {
        var handle = Arc.container().instance(WorkflowDefinition.class, Identifier.Literal.of(identifier));
        assertThat(handle.isAvailable()).as("workflow bean %s", identifier).isTrue();
        return handle.get().instance(Map.of()).start().join().asMap().orElseThrow();
    }

    private static String workflow(String namespace, String name, String message) {
        return """
                document:
                  dsl: '1.0.0'
                  namespace: %s
                  name: %s
                  version: '1.0.0'
                do:
                  - setMessage:
                      set:
                        message: %s
                """.formatted(namespace, name, message);
    }
}
