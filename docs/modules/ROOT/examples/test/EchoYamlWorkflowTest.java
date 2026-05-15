package test;

// tag::should_echo_name_from_yaml_workflow[]
import static org.hamcrest.Matchers.is;

import java.util.Map;

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
    @Identifier("company:echo-name") // namespace:name from document section
    WorkflowDefinition definition;

    @Test
    void should_echo_name_from_yaml_workflow() throws Exception {
        Map<String, String> input = Map.of("name", "Joe");
        WorkflowModel result = definition.instance(input).start().join();

        MatcherAssert.assertThat(result.asMap().orElseThrow().get("message"), is("echo: Joe"));
    }
}
// end::should_echo_name_from_yaml_workflow[]
