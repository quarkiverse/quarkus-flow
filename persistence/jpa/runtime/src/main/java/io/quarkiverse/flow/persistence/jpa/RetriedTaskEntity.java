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
    private short retryAttempt;

    public RetriedTaskEntity(TaskInfoKey key, short retryAttempt) {
        super(key);
        this.retryAttempt = retryAttempt;
    }

    public short getRetryAttempt() {
        return retryAttempt;
    }

}
