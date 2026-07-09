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

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;
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
import io.serverlessworkflow.impl.marshaller.MarshallingUtils;
import io.serverlessworkflow.impl.marshaller.WorkflowBufferFactory;
import io.serverlessworkflow.impl.persistence.CompletedTaskInfo;
import io.serverlessworkflow.impl.persistence.PersistenceInstanceOperations;
import io.serverlessworkflow.impl.persistence.PersistenceTaskInfo;
import io.serverlessworkflow.impl.persistence.PersistenceWorkflowInfo;
import io.serverlessworkflow.impl.persistence.RetriedTaskInfo;

@ApplicationScoped
public class JpaInstanceOperations implements PersistenceInstanceOperations {

    @Inject
    WorkflowInstanceRepository repository;

    @Inject
    CloudEventRepository ceRepository;

    @Inject
    WorkflowBufferFactory factory;

    @Inject
    EntityManager em;

    @Override
    public void writeInstanceData(WorkflowContextData workflowContext) {
        WorkflowInstanceData instance = workflowContext.instanceData();
        repository.persist(new WorkflowInstanceEntity(workflowContext.definition().application().id(),
                workflowContext.definition().id(), instance.id(), instance.startedAt(), instance.input()));
    }

    @Override
    public void writeRetryTask(WorkflowContextData workflowContext, TaskContextData taskContext) {
        em.persist(new RetriedTaskEntity(TaskInfoKey.from(workflowContext, taskContext),
                ((TaskContext) taskContext).retryAttempt()));
    }

    @Override
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
    public void writeStatus(WorkflowContextData workflowContext, WorkflowStatus status) {
        find(workflowContext).setStatus(status);
    }

    @Override
    public void removeProcessInstance(WorkflowContextData workflowContext) {
        repository.deleteById(toKey(workflowContext));
    }

    public void retrieveEvents(Map<String, Collection<CloudEvent>> reg2EventsMap) {
        ceRepository.findByRegId(reg2EventsMap.keySet())
                .forEach(entity -> reg2EventsMap.get(entity.getRegId()).add(from(entity)));
    }

    private CloudEvent from(CloudEventEntity entity) {
        CloudEventBuilder builder = CloudEventBuilder.fromSpecVersion(entity.getVersion()).withType(entity.getType())
                .withSource(entity.getSource()).withId(entity.getId()).withTime(entity.getTime())
                .withSubject(entity.getSubject()).withDataSchema(entity.getDataSchema())
                .withDataContentType(entity.getDataContentType()).withData(entity.getData());
        MarshallingUtils.readCloudEventExtensions(factory, entity.getExtensions(), builder);
        return builder.build();
    }

    @Override
    public void storeEvent(String regId, CloudEvent event) {
        ceRepository.persist(new CloudEventEntity(regId, event, MarshallingUtils.writeCloudEventExtensions(factory, event)));
    }

    @Override
    public void markAsProcessed(Map<String, Collection<String>> regCeIds) {
        ceRepository.setProcessed(regCeIds.values().stream().flatMap(c -> c.stream()).toList());
    }

    @Override
    public void clearProcessed() {
        ceRepository.clearProcessed();
    }

    @Override
    public void removeCloudEvents(Map<String, String> ids) {
        ceRepository.deleteByIds(ids.values());
    }

    @Override
    public void clearStatus(WorkflowContextData workflowContext) {
        find(workflowContext).setStatus(null);
    }

    @Override
    public Stream<PersistenceWorkflowInfo> scanAll(String applicationId, WorkflowDefinition definition) {
        QuarkusTransaction.begin();
        WorkflowDefinitionId id = definition.id();
        return repository.stream(
                "select x from WorkflowInstanceEntity x where x.applicationId=?1 and x.workflowNamespace=?2 and x.workflowName=?3 and x.workflowVersion=?4",
                applicationId, id.namespace(), id.name(), id.version()).map(this::from)
                .onClose(() -> QuarkusTransaction.commit());
    }

    private PersistenceWorkflowInfo from(WorkflowInstanceEntity x) {
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
        return repository.findByIdOptional(new WorkflowInstanceKey(instanceId, definition.application().id())).map(this::from);
    }

    private WorkflowInstanceEntity find(WorkflowContextData workflowContext) {
        return repository.findById(toKey(workflowContext));
    }

    private WorkflowInstanceKey toKey(WorkflowContextData workflowContext) {
        return new WorkflowInstanceKey(workflowContext.instanceData().id(), workflowContext.definition().application().id());
    }
}
