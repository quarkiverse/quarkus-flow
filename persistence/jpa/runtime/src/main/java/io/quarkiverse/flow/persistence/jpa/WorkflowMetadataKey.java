package io.quarkiverse.flow.persistence.jpa;

import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Embeddable;
import jakarta.persistence.Embedded;

@Embeddable
public class WorkflowMetadataKey implements Serializable {

    private static final long serialVersionUID = 1L;

    private String metaName;

    @Embedded
    private WorkflowInstanceKey workflowKey;

    protected WorkflowMetadataKey() {
    }

    public WorkflowMetadataKey(String metaName, WorkflowInstanceKey workflowKey) {
        this.metaName = metaName;
        this.workflowKey = workflowKey;
    }

    public String getMetaName() {
        return metaName;
    }

    public WorkflowInstanceKey getWorkflowKey() {
        return workflowKey;
    }

    public static long getSerialversionuid() {
        return serialVersionUID;
    }

    @Override
    public int hashCode() {
        return Objects.hash(metaName, workflowKey);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        WorkflowMetadataKey other = (WorkflowMetadataKey) obj;
        return Objects.equals(metaName, other.metaName) && Objects.equals(workflowKey, other.workflowKey);
    }
}
