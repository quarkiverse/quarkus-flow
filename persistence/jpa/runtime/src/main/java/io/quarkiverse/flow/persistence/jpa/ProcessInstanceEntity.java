package io.quarkiverse.flow.persistence.jpa;

import java.time.Instant;
import java.util.Collection;

import jakarta.persistence.Basic;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinColumns;
import jakarta.persistence.OneToMany;

import org.hibernate.annotations.DynamicUpdate;

import io.serverlessworkflow.impl.WorkflowModel;
import io.serverlessworkflow.impl.WorkflowStatus;

@Entity
@DynamicUpdate
@IdClass(ProcessInstanceKey.class)
public class ProcessInstanceEntity {

    @Id
    private String instanceId;

    @Id
    private String applicationId;

    @Basic(fetch = FetchType.LAZY, optional = true)
    private WorkflowStatus status;

    @Basic(fetch = FetchType.LAZY)
    private Instant startedAt;

    @Basic(fetch = FetchType.LAZY)
    private WorkflowModel input;

    @OneToMany(fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @JoinColumns({ @JoinColumn(name = "processInstanceId"), @JoinColumn(name = "applicationId") })
    private Collection<TaskInfoEntity> tasks;

    public ProcessInstanceEntity() {
    }

    public ProcessInstanceEntity(String applicationId, String instanceId, Instant startedAt, WorkflowModel input) {
        this.applicationId = applicationId;
        this.instanceId = instanceId;
        this.startedAt = startedAt;
        this.input = input;
    }

    public String getInstanceId() {
        return instanceId;
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

    public void setTasks(Collection<TaskInfoEntity> tasks) {
        this.tasks = tasks;
    }
}
