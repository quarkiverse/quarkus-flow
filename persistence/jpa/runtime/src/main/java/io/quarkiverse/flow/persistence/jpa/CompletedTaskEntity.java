package io.quarkiverse.flow.persistence.jpa;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import io.serverlessworkflow.impl.WorkflowModel;

@Entity
@DiscriminatorValue("1")
public class CompletedTaskEntity extends TaskInfoEntity {

    @Column
    private Instant instant;
    @JdbcTypeCode(SqlTypes.LONGVARBINARY)
    private WorkflowModel model;
    @Column
    private WorkflowModel context;
    @Column(name = "is_end_node")
    private boolean isEndNode;
    @Column(name = "next_position")
    private String nextPosition;

    public CompletedTaskEntity() {
    }

    public CompletedTaskEntity(TaskInfoKey key, Instant instant, WorkflowModel model, WorkflowModel context,
            boolean isEndNode,
            String nextPosition) {
        super(key);
        this.instant = instant;
        this.model = model;
        this.context = context;
        this.isEndNode = isEndNode;
        this.nextPosition = nextPosition;
    }

    public Instant getInstant() {
        return instant;
    }

    public WorkflowModel getModel() {
        return model;
    }

    public WorkflowModel getContext() {
        return context;
    }

    public boolean isEndNode() {
        return isEndNode;
    }

    public String getNextPosition() {
        return nextPosition;
    }

}
