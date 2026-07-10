package io.quarkiverse.flow.persistence.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

@Entity
@DiscriminatorValue("2")
public class RetriedTaskEntity extends TaskInfoEntity {

    public RetriedTaskEntity() {
    }

    @Column
    private int retryAttempt;

    public RetriedTaskEntity(TaskInfoKey key, int retryAttempt) {
        super(key);
        this.retryAttempt = retryAttempt;
    }

    public int getRetryAttempt() {
        return retryAttempt;
    }

}
