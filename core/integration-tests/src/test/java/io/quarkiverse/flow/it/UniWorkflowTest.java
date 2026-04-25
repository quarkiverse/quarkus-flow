package io.quarkiverse.flow.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import io.quarkus.test.InjectMock;
import io.quarkus.test.component.QuarkusComponentTest;
import io.serverlessworkflow.impl.WorkflowDefinition;
import io.serverlessworkflow.impl.WorkflowInstance;
import io.serverlessworkflow.impl.WorkflowModel;
import io.smallrye.common.annotation.Identifier;

@QuarkusComponentTest({ UniWorkflow.class, Identifier.class })
public class UniWorkflowTest {

    @InjectMock
    @Identifier("io.quarkiverse.flow.it.UniWorkflow")
    WorkflowDefinition workflowDefinition;

    @Inject
    UniWorkflow def;

    @Test
    void testUni() {
        WorkflowInstance mockInstance = mock(WorkflowInstance.class);
        WorkflowModel mockModel = mock(WorkflowModel.class);

        when(workflowDefinition.instance(any())).thenReturn(mockInstance);
        when(mockInstance.start()).thenReturn(CompletableFuture.completedFuture(mockModel));
        when(mockModel.as(String.class)).thenReturn(Optional.of("Javierito"));

        assertThat(def.startInstance().await().indefinitely().as(String.class).orElseThrow()).isEqualTo("Javierito");
    }
}
