package io.quarkiverse.flow.persistence.jpa;

import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class WorkflowInstanceKey implements Serializable {

    private static final long serialVersionUID = 1L;

    @Column(length = 26)
    private String instanceId;

    @Column(length = 26)
    private String applicationId;

    public WorkflowInstanceKey() {
    }

    public WorkflowInstanceKey(String instanceId, String applicationId) {
        super();
        this.instanceId = instanceId;
        this.applicationId = applicationId;
    }

    public String getInstanceId() {
        return instanceId;
    }

    public String getApplicationId() {
        return applicationId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(applicationId, instanceId);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        WorkflowInstanceKey other = (WorkflowInstanceKey) obj;
        return Objects.equals(applicationId, other.applicationId) && Objects.equals(instanceId, other.instanceId);
    }
}
