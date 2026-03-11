package io.quarkiverse.flow.persistence.jpa;

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

@Entity
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "task_type", discriminatorType = DiscriminatorType.INTEGER)
public abstract class TaskInfoEntity {
    @EmbeddedId
    private TaskInfoKey taskInfoKey;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumns({
            @JoinColumn(name = "applicationId", referencedColumnName = "applicationId", insertable = false, updatable = false),
            @JoinColumn(name = "processInstanceId", referencedColumnName = "instanceId", insertable = false, updatable = false) })
    private ProcessInstanceEntity processInstance;

    public TaskInfoEntity() {
    }

    public TaskInfoEntity(TaskInfoKey taskInfoKey) {
        this.taskInfoKey = taskInfoKey;
    }

    public String jsonPointer() {
        return taskInfoKey.getJsonPointer();
    }
}
