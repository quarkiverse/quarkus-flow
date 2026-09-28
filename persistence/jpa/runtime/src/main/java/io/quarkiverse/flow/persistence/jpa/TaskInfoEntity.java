package io.quarkiverse.flow.persistence.jpa;

import java.util.Collection;

import jakarta.persistence.CascadeType;
import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.DiscriminatorType;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinColumns;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;

@Entity
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "task_type", discriminatorType = DiscriminatorType.INTEGER)
public abstract class TaskInfoEntity implements MetadataSupport<TaskInfoKey, TaskMetadataEntity> {
    @EmbeddedId
    private TaskInfoKey taskInfoKey;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumns({
            @JoinColumn(name = "applicationId", referencedColumnName = "applicationId", insertable = false, updatable = false),
            @JoinColumn(name = "workflowInstanceId", referencedColumnName = "instanceId", insertable = false, updatable = false) })
    private WorkflowInstanceEntity workflowInstance;

    @OneToMany(fetch = FetchType.LAZY, cascade = CascadeType.ALL, mappedBy = "taskInstance")
    private Collection<TaskMetadataEntity> metadata;

    public TaskInfoEntity() {
    }

    public TaskInfoEntity(TaskInfoKey taskInfoKey) {
        this.taskInfoKey = taskInfoKey;
    }

    @Override
    public TaskInfoKey key() {
        return taskInfoKey;
    }

    public String jsonPointer() {
        return taskInfoKey.getJsonPointer();
    }

    public int iteration() {
        return taskInfoKey.getIteration();
    }

    @Override
    public Collection<TaskMetadataEntity> getMetadata() {
        return metadata;
    }
}
