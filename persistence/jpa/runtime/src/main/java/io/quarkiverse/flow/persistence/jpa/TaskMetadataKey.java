package io.quarkiverse.flow.persistence.jpa;

import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Embeddable;
import jakarta.persistence.Embedded;

@Embeddable
public class TaskMetadataKey implements Serializable {

    private static final long serialVersionUID = 1L;

    private String metaName;

    @Embedded
    private TaskInfoKey taskKey;

    public TaskMetadataKey(String metaName, TaskInfoKey taskKey) {
        this.metaName = metaName;
        this.taskKey = taskKey;
    }

    public String getMetaName() {
        return metaName;
    }

    public TaskInfoKey getTaskKey() {
        return taskKey;
    }

    @Override
    public int hashCode() {
        return Objects.hash(metaName, taskKey);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        TaskMetadataKey other = (TaskMetadataKey) obj;
        return Objects.equals(metaName, other.metaName) && Objects.equals(taskKey, other.taskKey);
    }
}
