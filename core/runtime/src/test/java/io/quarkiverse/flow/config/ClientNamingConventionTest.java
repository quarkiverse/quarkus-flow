package io.quarkiverse.flow.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.serverlessworkflow.impl.WorkflowDefinitionId;

class ClientNamingConventionTest {

    private static final WorkflowDefinitionId ID = new WorkflowDefinitionId("acme", "orders", "1.0.0");

    @Test
    @DisplayName("taskKeyFull produces namespace:name:version.task.taskName")
    void task_key_full() {
        assertThat(ClientNamingConvention.taskKeyFull(ID, "payment"))
                .isEqualTo("acme:orders:1.0.0.task.payment");
    }

    @Test
    @DisplayName("taskKeyMedium produces namespace:name.task.taskName")
    void task_key_medium() {
        assertThat(ClientNamingConvention.taskKeyMedium(ID, "payment"))
                .isEqualTo("acme:orders.task.payment");
    }

    @Test
    @DisplayName("taskKeyShort produces name.task.taskName")
    void task_key_short() {
        assertThat(ClientNamingConvention.taskKeyShort(ID, "payment"))
                .isEqualTo("orders.task.payment");
    }

    @Test
    @DisplayName("workflowKeyFull produces namespace:name:version")
    void workflow_key_full() {
        assertThat(ClientNamingConvention.workflowKeyFull(ID))
                .isEqualTo("acme:orders:1.0.0");
    }

    @Test
    @DisplayName("workflowKeyMedium produces namespace:name")
    void workflow_key_medium() {
        assertThat(ClientNamingConvention.workflowKeyMedium(ID))
                .isEqualTo("acme:orders");
    }

    @Test
    @DisplayName("workflowKeyShort produces name")
    void workflow_key_short() {
        assertThat(ClientNamingConvention.workflowKeyShort(ID))
                .isEqualTo("orders");
    }

    @Test
    @DisplayName("null workflowId throws NullPointerException")
    void null_workflow_id_throws() {
        assertThatThrownBy(() -> ClientNamingConvention.taskKeyFull(null, "t"))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> ClientNamingConvention.workflowKeyFull(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("null taskName throws NullPointerException")
    void null_task_name_throws() {
        assertThatThrownBy(() -> ClientNamingConvention.taskKeyFull(ID, null))
                .isInstanceOf(NullPointerException.class);
    }
}
