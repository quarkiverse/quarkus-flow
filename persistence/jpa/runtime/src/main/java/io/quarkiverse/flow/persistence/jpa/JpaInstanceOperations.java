package io.quarkiverse.flow.persistence.jpa;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import io.serverlessworkflow.impl.TaskContext;
import io.serverlessworkflow.impl.TaskContextData;
import io.serverlessworkflow.impl.WorkflowContextData;
import io.serverlessworkflow.impl.WorkflowDefinition;
import io.serverlessworkflow.impl.WorkflowInstanceData;
import io.serverlessworkflow.impl.WorkflowStatus;
import io.serverlessworkflow.impl.executors.AbstractTaskExecutor;
import io.serverlessworkflow.impl.executors.TransitionInfo;
import io.serverlessworkflow.impl.persistence.CompletedTaskInfo;
import io.serverlessworkflow.impl.persistence.PersistenceInstanceOperations;
import io.serverlessworkflow.impl.persistence.PersistenceTaskInfo;
import io.serverlessworkflow.impl.persistence.PersistenceWorkflowInfo;
import io.serverlessworkflow.impl.persistence.RetriedTaskInfo;

@ApplicationScoped
@Transactional
public class JpaInstanceOperations implements PersistenceInstanceOperations {

    @Inject
    ProcessInstanceRepository repository;

    @Override
    public void writeInstanceData(WorkflowContextData workflowContext) {
        WorkflowInstanceData instance = workflowContext.instanceData();
        repository.persist(new ProcessInstanceEntity(workflowContext.definition().application().id(), instance.id(),
                instance.startedAt(), instance.input()));
    }

    @Override
    public void writeRetryTask(WorkflowContextData workflowContext, TaskContextData taskContext) {
        find(workflowContext).getTasks()
                .add(new RetriedTaskEntity(taskContext.position().jsonPointer(), ((TaskContext) taskContext).retryAttempt()));
    }

    @Override
    public void writeCompletedTask(WorkflowContextData workflowContext, TaskContextData taskContext) {
        TransitionInfo transition = ((TaskContext) taskContext).transition();
        AbstractTaskExecutor<?> next = (AbstractTaskExecutor<?>) transition.next();
        find(workflowContext).getTasks().add(new CompletedTaskEntity(
                taskContext.position().jsonPointer(), taskContext.completedAt(), taskContext.output(),
                workflowContext.context(),
                transition.isEndNode(), next == null ? null : next.position().jsonPointer()));

    }

    @Override
    public void writeStatus(WorkflowContextData workflowContext, WorkflowStatus suspended) {
        find(workflowContext).setStatus(suspended);
    }

    @Override
    public void removeProcessInstance(WorkflowContextData workflowContext) {
        repository.deleteById(toKey(workflowContext));
    }

    @Override
    public void clearStatus(WorkflowContextData workflowContext) {
        find(workflowContext).setStatus(null);
    }

    @Override
    public Stream<PersistenceWorkflowInfo> scanAll(String applicationId, WorkflowDefinition definition) {
        return repository.stream("select x from ProcessInstanceEntity where x.applicationId=?1", applicationId).map(this::from);
    }

    private PersistenceWorkflowInfo from(ProcessInstanceEntity x) {
        return new PersistenceWorkflowInfo(x.getInstanceId(), x.getStartedAt(), x.getInput(), x.getStatus(),
                from(x.getTasks()));
    }

    private Map<String, PersistenceTaskInfo> from(Collection<TaskInfoEntity> taskEntities) {
        return taskEntities.stream().collect(Collectors.toMap(e -> e.jsonPointer(), this::from));
    }

    private PersistenceTaskInfo from(TaskInfoEntity taskEntity) {
        if (taskEntity instanceof CompletedTaskEntity c) {
            return new CompletedTaskInfo(c.getInstant(), c.getModel(), c.getContext(), c.isEndNode(), c.getNextPosition());
        } else if (taskEntity instanceof RetriedTaskEntity r) {
            return new RetriedTaskInfo(r.getRetryAttempt());
        }
        throw new UnsupportedOperationException("Unsupported taskInfo type " + taskEntity.getClass());
    }

    @Override
    public Optional<PersistenceWorkflowInfo> readWorkflowInfo(WorkflowDefinition definition, String instanceId) {
        return repository.findByIdOptional(new ProcessInstanceKey(instanceId, definition.application().id())).map(this::from);
    }

    private ProcessInstanceEntity find(WorkflowContextData workflowContext) {
        return repository.findById(toKey(workflowContext));
    }

    private ProcessInstanceKey toKey(WorkflowContextData workflowContext) {
        return new ProcessInstanceKey(workflowContext.instanceData().id(), workflowContext.definition().application().id());
    }
}
