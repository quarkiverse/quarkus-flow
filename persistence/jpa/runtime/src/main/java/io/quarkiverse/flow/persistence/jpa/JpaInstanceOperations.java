package io.quarkiverse.flow.persistence.jpa;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.serverlessworkflow.impl.TaskContext;
import io.serverlessworkflow.impl.TaskContextData;
import io.serverlessworkflow.impl.WorkflowContextData;
import io.serverlessworkflow.impl.WorkflowDefinition;
import io.serverlessworkflow.impl.WorkflowDefinitionId;
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
public class JpaInstanceOperations implements PersistenceInstanceOperations {

    @Inject
    ProcessInstanceRepository repository;

    @Inject
    EntityManager em;

    @Override
    @Transactional
    public void writeInstanceData(WorkflowContextData workflowContext) {
        WorkflowInstanceData instance = workflowContext.instanceData();
        repository.persist(new ProcessInstanceEntity(workflowContext.definition().application().id(),
                workflowContext.definition().id(), instance.id(), instance.startedAt(), instance.input()));
    }

    @Override
    @Transactional
    public void writeRetryTask(WorkflowContextData workflowContext, TaskContextData taskContext) {
        em.persist(new RetriedTaskEntity(TaskInfoKey.from(workflowContext, taskContext),
                ((TaskContext) taskContext).retryAttempt()));
    }

    @Override
    @Transactional
    public void writeCompletedTask(WorkflowContextData workflowContext, TaskContextData taskContext) {
        TransitionInfo transition = ((TaskContext) taskContext).transition();
        AbstractTaskExecutor<?> next = (AbstractTaskExecutor<?>) transition.next();
        em.persist(
                new CompletedTaskEntity(
                        TaskInfoKey.from(workflowContext, taskContext), taskContext.completedAt(), taskContext.output(),
                        workflowContext.context(),
                        transition.isEndNode(), next == null ? null : next.position().jsonPointer()));
    }

    @Override
    @Transactional
    public void writeStatus(WorkflowContextData workflowContext, WorkflowStatus status) {
        find(workflowContext).setStatus(status);
    }

    @Override
    @Transactional
    public void removeProcessInstance(WorkflowContextData workflowContext) {
        repository.deleteById(toKey(workflowContext));
    }

    @Override
    @Transactional
    public void clearStatus(WorkflowContextData workflowContext) {
        find(workflowContext).setStatus(null);
    }

    @Override
    public Stream<PersistenceWorkflowInfo> scanAll(String applicationId, WorkflowDefinition definition) {
        QuarkusTransaction.begin();
        WorkflowDefinitionId id = definition.id();
        return repository.stream(
                "select x from ProcessInstanceEntity x where x.applicationId=?1 and x.workflowNamespace=?2 and x.workflowName=?3 and x.workflowVersion=?4",
                applicationId, id.namespace(), id.name(), id.version()).map(this::from)
                .onClose(() -> QuarkusTransaction.commit());
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
            return new CompletedTaskInfo(c.getInstant(), c.getModel(), c.getContext(), c.isEndNode(), c.getNextPosition(),
                    c.iteration());
        } else if (taskEntity instanceof RetriedTaskEntity r) {
            return new RetriedTaskInfo(r.getRetryAttempt());
        }
        throw new UnsupportedOperationException("Unsupported taskInfo type " + taskEntity.getClass());
    }

    @Override
    @Transactional
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
