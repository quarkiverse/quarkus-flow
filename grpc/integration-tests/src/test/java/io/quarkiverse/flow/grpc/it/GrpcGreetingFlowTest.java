package io.quarkiverse.flow.grpc.it;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.serverlessworkflow.impl.WorkflowModel;

@QuarkusTest
public class GrpcGreetingFlowTest {

    @Inject
    GrpcGreetingFlow flow;

    @Test
    void should_use_quarkus_named_grpc_client_channel() {
        WorkflowModel result = flow.startInstance(Map.of("name", "Quarkus")).await().indefinitely();

        assertThat(result).isNotNull();
        assertThat(result.asMap())
                .isPresent()
                .get()
                .extracting(m -> m.get("message"))
                .isEqualTo("Hello Quarkus");
    }
}
