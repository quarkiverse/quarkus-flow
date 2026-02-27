package io.quarkiverse.flow.persistence.jpa;

import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.DiscriminatorType;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;

@Entity
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "task_type", discriminatorType = DiscriminatorType.INTEGER)
public abstract class TaskInfoEntity {
    @Id
    private String jsonPointer;

    public TaskInfoEntity() {
    }

    public TaskInfoEntity(String jsonPointer) {
        this.jsonPointer = jsonPointer;
    }

    public String jsonPointer() {
        return jsonPointer;
    }
}
