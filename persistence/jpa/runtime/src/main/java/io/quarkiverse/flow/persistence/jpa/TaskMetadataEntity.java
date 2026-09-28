package io.quarkiverse.flow.persistence.jpa;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinColumns;
import jakarta.persistence.ManyToOne;

@Entity
public class TaskMetadataEntity extends MetadataEntity {

    public TaskMetadataEntity(TaskMetadataKey id) {
        this.id = id;
    }

    @EmbeddedId
    private TaskMetadataKey id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumns({
            @JoinColumn(name = "workflowInstanceId", referencedColumnName = "workflowInstanceId", insertable = false, updatable = false),
            @JoinColumn(name = "applicationId", referencedColumnName = "applicationId", insertable = false, updatable = false),
            @JoinColumn(name = "jsonPointer", referencedColumnName = "jsonPointer", insertable = false, updatable = false),
            @JoinColumn(name = "iteration", referencedColumnName = "iteration", insertable = false, updatable = false) })
    private TaskInfoEntity taskInstance;

    @Override
    public String getName() {
        return id.getMetaName();
    }
}
