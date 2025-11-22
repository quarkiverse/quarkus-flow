package test;

// tag::should_echo_name_from_yaml_workflow[]
import static org.hamcrest.Matchers.is;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import jakarta.inject.Inject;

import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.serverlessworkflow.impl.WorkflowDefinition;
import io.serverlessworkflow.impl.WorkflowModel;
import io.smallrye.common.annotation.Identifier;

@QuarkusTest
class EchoYamlWorkflowTest {

    @Inject
    @Identifier("flow:echo-name") // namespace:name from document section
    WorkflowDefinition definition;

    @Test
    void should_echo_name_from_yaml_workflow() throws Exception {
        WorkflowModel result = definition.instance(Map.of("name", "Joe"))
                .start()
                .toCompletableFuture()
                .get(5, TimeUnit.SECONDS);

        MatcherAssert.assertThat(result.asMap().orElseThrow().get("message"), is("echo: Joe"));
    }
}
// end::should_echo_name_from_yaml_workflow[]
