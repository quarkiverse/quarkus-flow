package test;

// tag::should_produce_hello_message[]
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import java.util.Map;

import jakarta.inject.Inject;

import org.acme.HelloWorkflow;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.serverlessworkflow.impl.WorkflowModel;

@QuarkusTest // <1>
class HelloWorkflowTest {

    @Inject
    HelloWorkflow workflow;

    @Test
    void should_produce_hello_message() {
        WorkflowModel result = workflow.instance(Map.of()).start().join();

        // assuming the workflow writes {"message":"hello world!"}
        assertThat(result.asMap().orElseThrow().get("message"), is("hello world!"));
    }
}
// end::should_produce_hello_message[]
