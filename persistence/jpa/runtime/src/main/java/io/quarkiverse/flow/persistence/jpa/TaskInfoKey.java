package io.quarkiverse.flow.persistence.jpa;

import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import io.serverlessworkflow.impl.TaskContextData;
import io.serverlessworkflow.impl.WorkflowContextData;

@Embeddable
public class TaskInfoKey implements Serializable {

    private static final long serialVersionUID = 1L;

    public static TaskInfoKey from(WorkflowContextData workflow, TaskContextData task) {
        TaskInfoKey key = new TaskInfoKey();
        key.jsonPointer = task.position().jsonPointer();
        key.applicationId = workflow.definition().application().id();
        key.processInstanceId = workflow.instanceData().id();
        return key;
    }

    @Column
    private String jsonPointer;

    @Column
    private String applicationId;

    @Column
    private String processInstanceId;

    public String getJsonPointer() {
        return jsonPointer;
    }

    public void setJsonPointer(String jsonPointer) {
        this.jsonPointer = jsonPointer;
    }

    public String getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(String applicationId) {
        this.applicationId = applicationId;
    }

    public String getProcessInstanceId() {
        return processInstanceId;
    }

    public void setProcessInstanceId(String processInstanceId) {
        this.processInstanceId = processInstanceId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(applicationId, jsonPointer, processInstanceId);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        TaskInfoKey other = (TaskInfoKey) obj;
        return Objects.equals(applicationId, other.applicationId) && Objects.equals(jsonPointer, other.jsonPointer)
                && Objects.equals(processInstanceId, other.processInstanceId);
    }
}
