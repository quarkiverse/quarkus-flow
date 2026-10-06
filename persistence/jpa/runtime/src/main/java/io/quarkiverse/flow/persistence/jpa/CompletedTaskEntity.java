package io.quarkiverse.flow.persistence.jpa;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Transient;

import org.hibernate.annotations.EmbeddedColumnNaming;

import io.serverlessworkflow.impl.WorkflowModel;

@Entity
@DiscriminatorValue("1")
public class CompletedTaskEntity extends TaskInfoEntity {

    @Column
    private Instant instant;
    @Column
    private byte[] model;
    @Transient
    private WorkflowModel modelPOJO;

    @Embedded
    @EmbeddedColumnNaming("model_%s")
    private JPAHashMappingInfo modelHash;

    @Column
    private byte[] context;

    @Embedded
    @EmbeddedColumnNaming("context_%s")
    private JPAHashMappingInfo contextHash;

    @Transient
    private WorkflowModel contextPOJO;

    @Column
    private boolean isEndNode;
    @Column
    private String nextPosition;

    public CompletedTaskEntity() {
    }

    public CompletedTaskEntity(TaskInfoKey key, Instant instant, WorkflowModel model, WorkflowModel context,
            boolean isEndNode,
            String nextPosition) {
        super(key);
        this.instant = instant;
        this.modelPOJO = model;
        this.contextPOJO = context;
        this.isEndNode = isEndNode;
        this.nextPosition = nextPosition;

    }

    public Instant getInstant() {
        return instant;
    }

    public WorkflowModel getModel() {
        return modelPOJO;
    }

    public byte[] getContextBytes() {
        return context;
    }

    public byte[] getModelBytes() {
        return model;
    }

    public WorkflowModel getContext() {
        return contextPOJO;
    }

    public void setContext(byte[] data) {
        this.context = data;
    }

    public void setModel(byte[] data) {
        this.model = data;
    }

    public boolean isEndNode() {
        return isEndNode;
    }

    public String getNextPosition() {
        return nextPosition;
    }

    public JPAHashMappingInfo getModelHash() {
        return modelHash;
    }

    public void setModelHash(JPAHashMappingInfo modelHash) {
        this.modelHash = modelHash;
    }

    public JPAHashMappingInfo getContextHash() {
        return contextHash;
    }

    public void setContextHash(JPAHashMappingInfo contextHash) {
        this.contextHash = contextHash;
    }
}
