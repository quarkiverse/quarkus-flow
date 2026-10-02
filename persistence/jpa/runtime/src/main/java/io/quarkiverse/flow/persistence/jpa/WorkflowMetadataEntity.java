package io.quarkiverse.flow.persistence.jpa;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinColumns;
import jakarta.persistence.ManyToOne;

@Entity
public class WorkflowMetadataEntity extends MetadataEntity {

    protected WorkflowMetadataEntity() {
    }

    public WorkflowMetadataEntity(WorkflowMetadataKey id) {
        this.id = id;
    }

    @EmbeddedId
    private WorkflowMetadataKey id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumns({
            @JoinColumn(name = "applicationId", referencedColumnName = "applicationId", insertable = false, updatable = false),
            @JoinColumn(name = "workflowInstanceId", referencedColumnName = "instanceId", insertable = false, updatable = false) })
    private WorkflowInstanceEntity workflowInstance;

    @Override
    public String getName() {
        return id.getMetaName();
    }
}
