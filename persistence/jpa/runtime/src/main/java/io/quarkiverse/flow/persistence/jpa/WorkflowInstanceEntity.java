package io.quarkiverse.flow.persistence.jpa;

import java.time.Instant;
import java.util.Collection;

import jakarta.persistence.Basic;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;

import org.hibernate.annotations.DynamicUpdate;

import io.serverlessworkflow.impl.WorkflowDefinitionId;
import io.serverlessworkflow.impl.WorkflowModel;
import io.serverlessworkflow.impl.WorkflowStatus;

@Entity
@DynamicUpdate
public class WorkflowInstanceEntity implements MetadataSupport<WorkflowInstanceKey, WorkflowMetadataEntity> {

    @EmbeddedId
    WorkflowInstanceKey key;

    @Column(nullable = false)
    private String workflowNamespace;

    @Column(nullable = false)
    private String workflowName;

    @Column(nullable = false)
    private String workflowVersion;

    @Column(nullable = true)
    private WorkflowStatus status;

    @Column(nullable = false)
    private Instant startedAt;

    @Basic(fetch = FetchType.LAZY)
    private WorkflowModel input;

    @OneToMany(fetch = FetchType.LAZY, cascade = CascadeType.ALL, mappedBy = "workflowInstance")
    private Collection<TaskInfoEntity> tasks;

    @OneToMany(fetch = FetchType.LAZY, cascade = CascadeType.ALL, mappedBy = "workflowInstance")
    private Collection<WorkflowMetadataEntity> metadata;

    public WorkflowInstanceEntity() {
    }

    public WorkflowInstanceEntity(String applicationId, WorkflowDefinitionId definitionId, String instanceId, Instant startedAt,
            WorkflowModel input) {
        this.key = new WorkflowInstanceKey(instanceId, applicationId);
        this.workflowNamespace = definitionId.namespace();
        this.workflowName = definitionId.name();
        this.workflowVersion = definitionId.version();
        this.startedAt = startedAt;
        this.input = input;
    }

    public String getInstanceId() {
        return key.getInstanceId();
    }

    public String getApplicationId() {
        return key.getApplicationId();
    }

    public String getWorkflowNamespace() {
        return workflowNamespace;
    }

    public String getWorkflowName() {
        return workflowName;
    }

    public String getWorkflowVersion() {
        return workflowVersion;
    }

    public WorkflowStatus getStatus() {
        return status;
    }

    public void setStatus(WorkflowStatus status) {
        this.status = status;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public WorkflowModel getInput() {
        return input;
    }

    public Collection<TaskInfoEntity> getTasks() {
        return tasks;
    }

    public Collection<WorkflowMetadataEntity> getMetadata() {
        return metadata;
    }

    public WorkflowInstanceKey key() {
        return key;
    }
}
