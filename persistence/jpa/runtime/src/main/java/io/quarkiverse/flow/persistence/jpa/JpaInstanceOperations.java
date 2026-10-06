package io.quarkiverse.flow.persistence.jpa;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
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
import io.serverlessworkflow.impl.WorkflowModel;
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
import io.serverlessworkflow.impl.persistence.hashing.HashFactory;
import io.serverlessworkflow.impl.persistence.hashing.HashIndex;
import io.serverlessworkflow.impl.persistence.hashing.HashMappingCoordinator;
import io.serverlessworkflow.impl.persistence.hashing.HashMappingInfo;
import io.serverlessworkflow.impl.persistence.metadata.PersistenceMetaUtils;

@ApplicationScoped
public class JpaInstanceOperations implements PersistenceInstanceOperations {

    @Inject
    WorkflowInstanceRepository repository;

    @Inject
    HashMappingInfoRepository hashRepository;

    @Inject
    CloudEventRepository ceRepository;

    @Inject
    WorkflowBufferFactory bufferFactory;

    @Inject
    HashFactory hashFactory;

    @Inject
    Event<HashMappingCoordinator> hashEvents;

    @Inject
    EntityManager em;

    @Override
    public void writeInstanceData(WorkflowContextData workflowContext) {
        HashMappingCoordinator coordinator = hashFactory.mapCoordinator(this::retrieveBlobData, this::writeBlobData);
        WorkflowInstanceData instance = workflowContext.instanceData();
        WorkflowInstanceEntity entity = new WorkflowInstanceEntity(workflowContext.definition().application().id(),
                workflowContext.definition().id(), instance.id(), instance.startedAt(), instance.input());
        repository.persist(entity);
        writeWorkflowMetadata(coordinator, instance, entity);
        hashEvents.fire(coordinator);
    }

    @Override
    public void writeRetryTask(WorkflowContextData workflowContext, TaskContextData taskContext) {
        HashMappingCoordinator coordinator = hashFactory.mapCoordinator(this::retrieveBlobData, this::writeBlobData);
        RetriedTaskEntity entity = new RetriedTaskEntity(TaskInfoKey.from(workflowContext, taskContext),
                ((TaskContext) taskContext).retryAttempt());
        em.persist(entity);
        writeTaskMetadata(coordinator, workflowContext.instanceData(), entity);
        hashEvents.fire(coordinator);
    }

    @Override
    public void writeCompletedTask(WorkflowContextData workflowContext, TaskContextData taskContext) {
        HashMappingCoordinator coordinator = hashFactory.mapCoordinator(this::retrieveBlobData, this::writeBlobData);
        TransitionInfo transition = ((TaskContext) taskContext).transition();
        AbstractTaskExecutor<?> next = (AbstractTaskExecutor<?>) transition.next();
        CompletedTaskEntity entity = new CompletedTaskEntity(
                TaskInfoKey.from(workflowContext, taskContext), taskContext.completedAt(), taskContext.output(),
                workflowContext.context(),
                transition.isEndNode(), next == null ? null : next.position().jsonPointer());
        writeLargeBytes(coordinator, workflowContext.instanceData(),
                MarshallingUtils.writeObject(bufferFactory, entity.getModel()), entity::setModelHash, entity::setModel);
        writeLargeBytes(coordinator, workflowContext.instanceData(),
                MarshallingUtils.writeObject(bufferFactory, entity.getContext()), entity::setContextHash,
                entity::setContext);
        em.persist(entity);
        writeTaskMetadata(coordinator, workflowContext.instanceData(), entity);
        hashEvents.fire(coordinator);
    }

    private void writeTaskMetadata(HashMappingCoordinator coordinator, WorkflowInstanceData instance, TaskInfoEntity entity) {
        writeMetadata(coordinator, instance, entity, (k, v) -> new TaskMetadataEntity(new TaskMetadataKey(k, v.key())));
    }

    private void writeWorkflowMetadata(HashMappingCoordinator coordinator, WorkflowInstanceData instance,
            WorkflowInstanceEntity entity) {
        writeMetadata(coordinator, instance, entity, (k, v) -> new WorkflowMetadataEntity(new WorkflowMetadataKey(k, v.key())));
    }

    private <K, E extends MetadataEntity> void writeMetadata(HashMappingCoordinator coordinator, WorkflowInstanceData instance,
            MetadataSupport<K, E> entity, BiFunction<String, MetadataSupport<K, E>, E> function) {
        PersistenceMetaUtils.durableMetadataAsStream(instance).forEach(entry -> {
            E metadataEntity = function.apply(entry.getKey(), entity);
            writeLargeBytes(coordinator, instance, MarshallingUtils.writeObject(bufferFactory, entry.getValue()),
                    metadataEntity::setHashValue, metadataEntity::setRawValue);
            em.persist(metadataEntity);
        });
    }

    private Map<String, Object> readMetadata(HashMappingCoordinator coordinator, String instanceId,
            MetadataSupport<?, ?> entity) {
        return entity.getMetadata().stream()
                .collect(Collectors.toMap(x -> x.getName(), x -> readObject(readLargeBytes(instanceId, coordinator,
                        x.getRawValue(), x.getHashValue()))));
    }

    @Override
    public void writeStatus(WorkflowContextData workflowContext, WorkflowStatus status) {
        find(workflowContext).setStatus(status);
    }

    @Override
    public void removeProcessInstance(WorkflowContextData workflowContext) {
        HashMappingCoordinator coordinator = hashFactory.mapCoordinator(this::retrieveBlobData, this::writeBlobData);
        repository.deleteById(toKey(workflowContext));
        hashRepository.deleteByInstance(workflowContext.instanceData().id());
        coordinator.afterRemove(workflowContext.instanceData().id());
        hashEvents.fire(coordinator);
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
        MarshallingUtils.readCloudEventExtensions(bufferFactory, entity.getExtensions(), builder);
        return builder.build();
    }

    @Override
    public void storeEvent(String regId, CloudEvent event) {
        ceRepository
                .persist(new CloudEventEntity(regId, event, MarshallingUtils.writeCloudEventExtensions(bufferFactory, event)));
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
        HashMappingCoordinator coordinator = hashFactory.mapCoordinator(this::retrieveBlobData, this::writeBlobData);
        QuarkusTransaction.begin();
        WorkflowDefinitionId id = definition.id();
        return repository.stream(
                "select x from WorkflowInstanceEntity x where x.key.applicationId=?1 and x.workflowNamespace=?2 and x.workflowName=?3 and x.workflowVersion=?4",
                applicationId, id.namespace(), id.name(), id.version()).map(i -> from(coordinator, i))
                .onClose(() -> QuarkusTransaction.commit());
    }

    private PersistenceWorkflowInfo from(HashMappingCoordinator coordinator, WorkflowInstanceEntity x) {
        return new PersistenceWorkflowInfo(x.getInstanceId(), x.getStartedAt(), x.getInput(), x.getStatus(),
                from(coordinator, x.getInstanceId(), x.getTasks()), readMetadata(coordinator, x.getInstanceId(), x));
    }

    private Map<String, PersistenceTaskInfo> from(HashMappingCoordinator coordinator, String instanceId,
            Collection<TaskInfoEntity> taskEntities) {
        return taskEntities.stream().collect(Collectors.toMap(e -> e.jsonPointer(), t -> from(coordinator, instanceId, t)));
    }

    private PersistenceTaskInfo from(HashMappingCoordinator coordinator, String instanceId, TaskInfoEntity taskEntity) {
        if (taskEntity instanceof CompletedTaskEntity c) {
            return new CompletedTaskInfo(c.getInstant(), readModel(readLargeBytes(instanceId, coordinator,
                    c.getModelBytes(), c.getModelHash())), readModel(
                            readLargeBytes(instanceId, coordinator,
                                    c.getContextBytes(), c.getContextHash())),
                    c.isEndNode(), c.getNextPosition(),
                    c.iteration(), readMetadata(coordinator, instanceId, taskEntity));
        } else if (taskEntity instanceof RetriedTaskEntity r) {
            return new RetriedTaskInfo(r.getRetryAttempt(), readMetadata(coordinator, instanceId, taskEntity));
        }
        throw new UnsupportedOperationException("Unsupported taskInfo type " + taskEntity.getClass());
    }

    @Override
    @Transactional
    public Optional<PersistenceWorkflowInfo> readWorkflowInfo(WorkflowDefinition definition, String instanceId) {
        HashMappingCoordinator coordinator = hashFactory.mapCoordinator(this::retrieveBlobData, this::writeBlobData);
        return repository.findByIdOptional(new WorkflowInstanceKey(instanceId, definition.application().id()))
                .map(i -> from(coordinator, i));
    }

    private WorkflowInstanceEntity find(WorkflowContextData workflowContext) {
        return repository.findById(toKey(workflowContext));
    }

    private WorkflowInstanceKey toKey(WorkflowContextData workflowContext) {
        return new WorkflowInstanceKey(workflowContext.instanceData().id(), workflowContext.definition().application().id());
    }

    private Map<String, Map<HashIndex, byte[]>> retrieveBlobData(String instanceId) {
        return hashRepository.stream("instance = ?1", instanceId)
                .collect(Collectors.groupingBy(e -> e.getKey().key(), Collectors.toMap(
                        v -> hashFactory.indexFromString(v.getId()), HashMappingInfoEntity::getData)));
    }

    private void writeBlobData(Map<String, List<HashMappingInfo>> writeInfo) {
        writeInfo.entrySet().stream()
                .flatMap(e -> e.getValue().stream()
                        .map(item -> new HashMappingInfoEntity(item.index(), e.getKey(), item.item(), item.bytes())))
                .forEach(em::persist);
        em.flush();
    }

    private Object readObject(byte[] rawData) {
        return MarshallingUtils.readObject(bufferFactory, rawData);
    }

    private WorkflowModel readModel(byte[] rawData) {
        return MarshallingUtils.readModel(bufferFactory, rawData);
    }

    private byte[] readLargeBytes(String instanceId, HashMappingCoordinator coordinator, byte[] rawData,
            JPAHashMappingInfo mappingInfo) {
        return rawData == null
                ? coordinator.readBytes(instanceId,
                        Objects.requireNonNull(mappingInfo, "mappingInfo must not be null if rawData is also null")
                                .getHashItem(),
                        mappingInfo.getHashIndex()).orElse(null)
                : rawData;
    }

    private void writeLargeBytes(HashMappingCoordinator coordinator, WorkflowInstanceData instanceData, byte[] data,
            Consumer<JPAHashMappingInfo> hashConsumer,
            Consumer<byte[]> byteConsumer) {
        hashFactory.fromData(data)
                .map(item -> new JPAHashMappingInfo(item,
                        coordinator.calculateIndex(instanceData.id(), item, data)))
                .ifPresentOrElse(hashConsumer, () -> byteConsumer.accept(data));
    }
}
