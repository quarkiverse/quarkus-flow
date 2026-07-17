package io.quarkiverse.flow.oidc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import io.serverlessworkflow.api.types.TaskItem;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.fluent.func.FuncWorkflowBuilder;
import io.serverlessworkflow.impl.WorkflowDefinitionId;

class OidcClientNameResolutionTest {

    @Test
    void clientName_generates_composite_name() {
        Workflow workflow = FuncWorkflowBuilder.workflow("orders", "acme", "1.0.0").build();
        TaskItem task = new TaskItem("payment", null);

        String result = OidcNamingConvention.clientName(workflow, task);

        assertThat(result).isEqualTo("acme:orders:1.0.0.task.payment");
    }

    @Test
    void clientName_handles_default_namespace() {
        Workflow workflow = FuncWorkflowBuilder.workflow("orders").build();
        TaskItem task = new TaskItem("shipping", null);

        String result = OidcNamingConvention.clientName(workflow, task);

        assertThat(result).isEqualTo("org-acme:orders:0.0.1.task.shipping");
    }

    @Test
    void clientName_uses_workflow_definition_id() {
        Workflow workflow = FuncWorkflowBuilder.workflow("inventory", "contoso", "3.5.1").build();
        TaskItem task = new TaskItem("check", null);

        String result = OidcNamingConvention.clientName(workflow, task);

        WorkflowDefinitionId id = WorkflowDefinitionId.of(workflow);
        String expected = id.toString(":") + ".task.check";
        assertThat(result).isEqualTo(expected);
    }

    @Test
    void clientName_null_workflow_throws() {
        TaskItem task = new TaskItem("payment", null);

        assertThatThrownBy(() -> OidcNamingConvention.clientName(null, task))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("workflow is null");
    }

    @Test
    void clientName_null_task_throws() {
        Workflow workflow = FuncWorkflowBuilder.workflow("orders").build();

        assertThatThrownBy(() -> OidcNamingConvention.clientName(workflow, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("taskItem is null");
    }
}
