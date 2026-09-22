package io.quarkiverse.flow.persistence.redis;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.cloudevents.CloudEvent;
import io.cloudevents.CloudEventData;
import io.cloudevents.SpecVersion;
import io.cloudevents.core.builder.CloudEventBuilder;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.hash.HashCommands;
import io.quarkus.redis.datasource.hash.TransactionalHashCommands;
import io.quarkus.redis.datasource.keys.KeyCommands;
import io.quarkus.redis.datasource.keys.KeyScanArgs;
import io.quarkus.redis.datasource.keys.KeyScanCursor;
import io.quarkus.redis.datasource.keys.TransactionalKeyCommands;
import io.quarkus.redis.datasource.set.SetCommands;
import io.quarkus.redis.datasource.set.TransactionalSetCommands;
import io.quarkus.redis.datasource.transactions.TransactionResult;
import io.quarkus.redis.datasource.transactions.TransactionalRedisDataSource;
import io.quarkus.redis.datasource.value.SetArgs;
import io.quarkus.redis.datasource.value.ValueCommands;
import io.serverlessworkflow.impl.TaskContext;
import io.serverlessworkflow.impl.TaskContextData;
import io.serverlessworkflow.impl.WorkflowContextData;
import io.serverlessworkflow.impl.WorkflowDefinition;
import io.serverlessworkflow.impl.WorkflowDefinitionData;
import io.serverlessworkflow.impl.WorkflowInstanceData;
import io.serverlessworkflow.impl.WorkflowStatus;
import io.serverlessworkflow.impl.executors.AbstractTaskExecutor;
import io.serverlessworkflow.impl.executors.TransitionInfo;
import io.serverlessworkflow.impl.marshaller.MarshallingUtils;
import io.serverlessworkflow.impl.marshaller.TaskStatus;
import io.serverlessworkflow.impl.marshaller.WorkflowBufferFactory;
import io.serverlessworkflow.impl.marshaller.WorkflowInputBuffer;
import io.serverlessworkflow.impl.marshaller.WorkflowOutputBuffer;
import io.serverlessworkflow.impl.persistence.CompletedTaskInfo;
import io.serverlessworkflow.impl.persistence.PersistenceInstanceTransaction;
import io.serverlessworkflow.impl.persistence.PersistenceTaskInfo;
import io.serverlessworkflow.impl.persistence.PersistenceWorkflowInfo;
import io.serverlessworkflow.impl.persistence.RetriedTaskInfo;
import io.serverlessworkflow.impl.persistence.hashing.*;

public class RedisInstanceTransaction implements PersistenceInstanceTransaction {

    private final static String DATE = "date";
    private final static String STATUS = "status";
    private final static String INPUT = "input";
    private final static String OUTPUT = "output";
    private final static String CONTEXT = "context";

    private final static String BLOB = "blob";
    private final static String META = "meta_";
    private final static String IDX = "_idx";
    private final static String RETRY_ATTEMPT = "retryAttempt";
    private final static String END_NODE = "endNode";
    private final static String NEXT = "next";
    private final static String ITERATION = "iteration";
    private final static String SCHEMA_VERSION = "schemaVersion";
    private final static byte[] SCHEMA_VERSION_2 = { 2 };
    private final static String SEPARATOR = ":";

    private static final String CE_SOURCE = "source";
    private static final String CE_TYPE = "type";
    private static final String CE_VERSION = "version";
    private static final String CE_SUBJECT = "subject";
    private static final String CE_SCHEMA = "schema";
    private static final String CE_CONTENT_TYPE = "contentType";
    private static final String CE_DATA = "data";
    private static final String CE_EXTENSIONS = "extensions";
    private static final String CE_PREFIX = "CE" + SEPARATOR;
    private static final String CE_TIME = "time";
    private static final String PROCESSED_FLAG = "processed";
    private static final byte[] PROCESSED_VALUE = new byte[] { 1 };
    private static final String CORRELATION_LOCK_KEY = "lock:correlations";
    private static final int CORRELATION_LOCK_TIMEOUT = 10;
    private static final Logger logger = LoggerFactory.getLogger(RedisInstanceTransaction.class);
    private static final String TASK_PREFIX = "task" + SEPARATOR;

    private final RedisDataSource ds;
    private final WorkflowBufferFactory factory;
    private final KeyCommands<String> keyCommands;
    private final HashCommands<String, String, byte[]> hashCommands;
    private final ValueCommands<String, String> valueCommands;
    private final SetCommands<String, String> setCommands;
    private final HashFactory hashFactory;
    private final HashMappingCoordinator hashCoordinator;

    private final List<Consumer<TransactionalRedisDataSource>> operations;

    private TransactionalHashCommands<String, String, byte[]> txHashCommands;
    private TransactionalSetCommands<String, String> txSetCommands;
    private TransactionalKeyCommands<String> txKeyCommands;

    private String correlationLockUUID;

    public RedisInstanceTransaction(RedisDataSource ds, KeyCommands<String> keyCommands,
            HashCommands<String, String, byte[]> hashCommands,
            ValueCommands<String, String> valueCommands,
            SetCommands<String, String> setCommands,
            WorkflowBufferFactory factory,
            HashFactory hashFactory) {
        this.ds = ds;
        this.keyCommands = keyCommands;
        this.hashCommands = hashCommands;
        this.valueCommands = valueCommands;
        this.setCommands = setCommands;
        this.operations = new ArrayList<>();
        this.factory = factory;
        this.hashFactory = hashFactory;
        this.hashCoordinator = HashMappingCoordinator.build(hashFactory, this::retrieveBlobData, this::writeBlobData);
    }

    private Map<String, Map<HashIndex, byte[]>> retrieveBlobData(String instanceId) {
        Map<String, Map<HashIndex, byte[]>> result = new HashMap<>();
        setCommands.smembers(blobSetKey(instanceId)).forEach(s -> hashCommands.hgetall(s)
                .forEach((k, v) -> result.computeIfAbsent(k, __ -> new HashMap<>()).put(hashFactory.indexFromString(k), v)));
        return result;
    }

    private void writeBlobData(Map<String, List<HashMappingInfo>> writeInfo) {
        for (Entry<String, List<HashMappingInfo>> entry : writeInfo.entrySet()) {
            String setKey = blobSetKey(entry.getKey());
            for (HashMappingInfo item : entry.getValue()) {
                String key = blobKey(setKey, item.key());
                operations.add(tx -> setCommands(tx).sadd(setKey, key));
                operations.add(
                        tx -> hashCommands(tx).hset(key, item.index().toString(), item.bytes()));
            }
        }
    }

    @Override
    public void commit(WorkflowDefinitionData definition) {
        hashCoordinator.persist();
        if (!operations.isEmpty()) {
            ds.withTransaction(tx -> {
                operations.forEach(x -> x.accept(tx));
            });
        }
        hashCoordinator.afterCommit();
        releaseLock();
    }

    @Override
    public void rollback(WorkflowDefinitionData definition) {
        hashCoordinator.afterRollback();
        releaseLock();
    }

    @Override
    public void writeInstanceData(WorkflowContextData workflowContext) {
        String instanceId = key(workflowContext);
        operations.add(tx -> hashCommands(tx).hset(instanceId, DATE,
                MarshallingUtils.writeInstant(factory, workflowContext.instanceData().startedAt())));
        operations.add(tx -> hashCommands(tx).hset(instanceId, INPUT,
                MarshallingUtils.writeModel(factory, workflowContext.instanceData().input())));
        operations.add(tx -> hashCommands(tx).hset(instanceId, SCHEMA_VERSION, SCHEMA_VERSION_2));
        writeMetadata(workflowContext.instanceData(), instanceId, k -> k);

    }

    @Override
    public void writeRetryTask(WorkflowContextData workflowContext, TaskContextData taskContext) {
        String key = key(workflowContext);
        operations.add(tx -> hashCommands(tx).hset(key, taskId(taskContext, STATUS),
                MarshallingUtils.writeEnum(factory, TaskStatus.RETRIED)));
        operations.add(tx -> hashCommands(tx).hset(key, taskId(taskContext, RETRY_ATTEMPT),
                MarshallingUtils.writeInt(factory, ((TaskContext) taskContext).retryAttempt())));
        writeMetadata(workflowContext.instanceData(), key, k -> taskId(taskContext, k));
    }

    @Override
    public void writeCompletedTask(WorkflowContextData workflowContext,
            TaskContextData taskContext) {
        String key = key(workflowContext);
        operations.add(tx -> hashCommands(tx).hset(key, taskId(taskContext, STATUS),
                MarshallingUtils.writeEnum(factory, TaskStatus.COMPLETED)));
        operations.add(
                tx -> hashCommands(tx).hset(key, taskId(taskContext, DATE),
                        MarshallingUtils.writeInstant(factory, taskContext.completedAt())));
        writeLargeByteArray(key, OUTPUT, k -> taskId(taskContext, k),
                MarshallingUtils.writeModel(factory, taskContext.output()));
        if (workflowContext.context() != null) {
            writeLargeByteArray(key, CONTEXT, k -> taskId(taskContext, k),
                    MarshallingUtils.writeModel(factory, workflowContext.context()));
        }
        writeMetadata(workflowContext.instanceData(), key, k -> taskId(taskContext, k));
        TransitionInfo transition = ((TaskContext) taskContext).transition();
        operations.add(
                tx -> hashCommands(tx).hset(key, taskId(taskContext, END_NODE),
                        MarshallingUtils.writeBoolean(factory, transition.isEndNode())));
        AbstractTaskExecutor<?> next = (AbstractTaskExecutor<?>) transition.next();
        if (next != null) {
            operations.add(tx -> hashCommands(tx).hset(key, taskId(taskContext, NEXT),
                    MarshallingUtils.writeString(factory, next.position().jsonPointer())));
        }
        operations.add(
                tx -> hashCommands(tx).hset(key, taskId(taskContext, ITERATION),
                        MarshallingUtils.writeInt(factory, taskContext.iteration())));
    }

    private void writeMetadata(WorkflowInstanceData instanceData, String instanceKey, Function<String, String> keySupplier) {
        instanceData.metadata()
                .forEach((k, v) -> writeLargeByteArray(instanceKey, metaKey(k), keySupplier,
                        MarshallingUtils.writeObject(factory, v)));
    }

    private Map<String, Object> readMetadata(Map<String, byte[]> taskInfo, String instanceKey) {
        Map<String, Object> metadata = new HashMap<>();
        for (Entry<String, byte[]> entry : taskInfo.entrySet()) {
            if (entry.getKey().startsWith(META) && !entry.getKey().endsWith(IDX)) {
                String metaKey = entry.getKey().substring(META.length());
                metadata.put(metaKey, MarshallingUtils.readObject(factory, readLargeByteArray(instanceKey,
                        entry.getValue(), taskInfo.get(idxKey(metaKey)))));
            }
        }
        return metadata;
    }

    @Override
    public void writeStatus(WorkflowContextData workflowContext, WorkflowStatus status) {
        operations.add(tx -> hashCommands(tx).hset(key(workflowContext), STATUS, MarshallingUtils.writeEnum(factory, status)));
    }

    @Override
    public void removeProcessInstance(WorkflowContextData workflowContext) {
        String key = key(workflowContext);
        if (hashCommands.hexists(key, SCHEMA_VERSION)) {
            operations.add(tx -> keyCommands(tx).del(key(workflowContext)));
            setCommands.smembers(blobSetKey(key)).forEach(k -> operations.add(tx -> keyCommands(tx).del(k)));
            hashCoordinator.afterRemove(workflowContext.instanceData().id());
        } else {
            legacyRemoveProcessInstance(workflowContext);
        }
    }

    private void legacyRemoveProcessInstance(WorkflowContextData workflowContext) {
        KeyScanCursor<String> keysCursor = keyCommands
                .scan(new KeyScanArgs().match(legacyTaskPrefix(workflowContext.instanceData().id()) + "*"));
        Collection<String> toDelete = new ArrayList<>();
        toDelete.add(key(workflowContext));
        while (keysCursor.hasNext()) {
            keysCursor.next().forEach(toDelete::add);
        }
        operations.add(tx -> keyCommands(tx).del(toDelete.toArray(new String[toDelete.size()])));
    }

    @Override
    public void clearStatus(WorkflowContextData workflowContext) {
        operations.add(tx -> hashCommands(tx).hdel(key(workflowContext), STATUS));
    }

    @Override
    public Stream<PersistenceWorkflowInfo> scanAll(String applicationId, WorkflowDefinition definition) {
        PersistenceWorkflowInfoGenerator generator = new PersistenceWorkflowInfoGenerator(keyCommands
                .scan(new KeyScanArgs().match(prefixId(applicationId, definition) + "*")));
        return Stream.generate(generator::next).takeWhile(Objects::nonNull);
    }

    @Override
    public void storeEvent(String regId, CloudEvent event) {
        String key = ceKey(regId, event.getId());
        operations.add(tx -> hashCommands(tx).hset(key, CE_SOURCE,
                MarshallingUtils.writeURI(factory, event.getSource())));
        operations.add(tx -> hashCommands(tx).hset(key, CE_TYPE, MarshallingUtils.writeString(factory, event.getType())));
        operations.add(tx -> hashCommands(tx).hset(key, CE_VERSION,
                MarshallingUtils.writeEnum(factory, event.getSpecVersion())));
        String subject = event.getSubject();
        if (subject != null) {
            operations.add(tx -> hashCommands(tx).hset(key, CE_SUBJECT, MarshallingUtils.writeString(factory, subject)));
        }
        URI dataSchema = event.getDataSchema();
        if (dataSchema != null) {
            operations.add(
                    tx -> hashCommands(tx).hset(key, CE_SCHEMA, MarshallingUtils.writeURI(factory, dataSchema)));
        }
        String contentType = event.getDataContentType();
        if (contentType != null) {
            operations
                    .add(tx -> hashCommands(tx).hset(key, CE_CONTENT_TYPE, MarshallingUtils.writeString(factory, contentType)));
        }
        OffsetDateTime time = event.getTime();
        if (time != null) {
            operations
                    .add(tx -> hashCommands(tx).hset(key, CE_TIME, MarshallingUtils.writeOffsetDateTime(factory, time)));
        }
        CloudEventData data = event.getData();
        if (data != null) {
            operations.add(tx -> hashCommands(tx).hset(key, CE_DATA, data.toBytes()));
        }
        Set<String> extensionNames = event.getExtensionNames();
        if (!extensionNames.isEmpty()) {
            operations.add(tx -> hashCommands(tx).hset(key, CE_EXTENSIONS,
                    MarshallingUtils.writeCloudEventExtensions(factory, event)));
        }
    }

    private boolean acquireLock() {
        if (correlationLockUUID != null) {
            return true;
        }

        String uuid = UUID.randomUUID().toString();
        logger.debug("Trying to acquire lock with uuid {}", uuid);
        short attempCounter = 20;
        String result;
        do {
            result = valueCommands.setGet(CORRELATION_LOCK_KEY, uuid, new SetArgs().nx().ex(CORRELATION_LOCK_TIMEOUT));
            if (result != null) {
                logger.trace("Failed to acquire lock with uuid {}, lock already acquired with uuid {}", uuid, result);
                try {
                    Thread.sleep(100);
                } catch (InterruptedException in) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        } while (result != null && attempCounter-- > 0);
        if (result == null) {
            correlationLockUUID = uuid;
            logger.debug("Correlation lock acquired with uuid {}", uuid);
            return true;
        } else {
            logger.warn(
                    "it was not possible to acquire the lock with uuid {} because it is hold by uuid {}, skipping correlation calculation",
                    uuid, result);
            return false;
        }
    }

    private void releaseLock() {
        if (correlationLockUUID != null) {
            logger.debug("try to release lock {}", correlationLockUUID);
            String currentUUID = valueCommands.get(CORRELATION_LOCK_KEY);
            if (correlationLockUUID.equals(currentUUID)) {
                logger.debug("releasing lock {}", correlationLockUUID);
                TransactionResult txResult = ds.withTransaction(tx -> {
                    tx.key(String.class).del(CORRELATION_LOCK_KEY);
                }, CORRELATION_LOCK_KEY);
                if (txResult.discarded()) {
                    logger.warn("Error releasing lock associated to UUID {}", correlationLockUUID);
                }
            } else {
                logger.info("current uuid {} does not match to be released uuid {}", currentUUID, correlationLockUUID);
            }
        }
    }

    @Override
    public void retrieveEvents(Map<String, Collection<CloudEvent>> result) {
        if (acquireLock()) {
            result.entrySet().forEach(e -> {
                String targetRegId = e.getKey();
                KeyScanCursor<String> cursor = keyCommands
                        .scan(new KeyScanArgs().match(CE_PREFIX + targetRegId + SEPARATOR + "*"));
                while (cursor.hasNext()) {
                    for (String key : cursor.next()) {
                        Map<String, byte[]> storedInfo = hashCommands.hgetall(key);
                        if (!storedInfo.containsKey(PROCESSED_FLAG)) {
                            e.getValue().add(readCloudEvent(lastChunk(key), storedInfo));
                        }
                    }
                }
            });
        }
    }

    private static String ceKey(String regId, String ceId) {
        return CE_PREFIX + regId + SEPARATOR + ceId;
    }

    private CloudEvent readCloudEvent(String id, Map<String, byte[]> storedInfo) {
        CloudEventBuilder builder = CloudEventBuilder
                .fromSpecVersion(MarshallingUtils.readEnum(factory, storedInfo.get(CE_VERSION), SpecVersion.class))
                .withType(MarshallingUtils.readString(factory, storedInfo.get(CE_TYPE)))
                .withSource(MarshallingUtils.readURI(factory, storedInfo.get(CE_SOURCE))).withId(id);
        byte[] value = storedInfo.get(CE_DATA);
        if (value != null) {
            builder.withData(value);
        }
        value = storedInfo.get(CE_SUBJECT);
        if (value != null) {
            builder.withSubject(MarshallingUtils.readString(factory, value));
        }
        value = storedInfo.get(CE_TIME);
        if (value != null) {
            builder.withTime(MarshallingUtils.readOffsetDateTime(factory, value));
        }
        value = storedInfo.get(CE_CONTENT_TYPE);
        if (value != null) {
            builder.withDataContentType(MarshallingUtils.readString(factory, value));
        }
        value = storedInfo.get(CE_SCHEMA);
        if (value != null) {
            builder.withDataSchema(MarshallingUtils.readURI(factory, value));
        }
        MarshallingUtils.readCloudEventExtensions(factory, storedInfo.get(CE_EXTENSIONS), builder);
        return builder.build();
    }

    @Override
    public void markAsProcessed(Map<String, Collection<String>> regCeIds) {
        Collection<String> keys = new HashSet<>();
        for (Map.Entry<String, Collection<String>> entry : regCeIds.entrySet()) {
            String regId = entry.getKey();
            entry.getValue().forEach(ceId -> keys.add(ceKey(regId, ceId)));
        }
        keys.forEach(k -> operations.add(tx -> hashCommands(tx).hset(k, PROCESSED_FLAG, PROCESSED_VALUE)));
    }

    @Override
    public void clearProcessed() {
        if (acquireLock()) {
            KeyScanCursor<String> cursor = keyCommands.scan(new KeyScanArgs().match(CE_PREFIX + "*"));
            while (cursor.hasNext()) {
                cursor.next().forEach(k -> operations.add(tx -> hashCommands(tx).hdel(k, PROCESSED_FLAG)));
            }
        }
    }

    @Override
    public void removeCloudEvents(Map<String, String> ids) {
        if (!ids.isEmpty()) {
            operations.add(tx -> keyCommands(tx)
                    .del(ids.entrySet().stream().map(e -> ceKey(e.getKey(), e.getValue())).toArray(String[]::new)));
        }
    }

    private class PersistenceWorkflowInfoGenerator {

        private final KeyScanCursor<String> cursor;
        private Iterator<String> keys;

        public PersistenceWorkflowInfoGenerator(KeyScanCursor<String> cursor) {
            this.cursor = cursor;
            this.keys = Collections.emptyIterator();
        }

        public PersistenceWorkflowInfo next() {
            if (!keys.hasNext()) {
                if (!cursor.hasNext()) {
                    return null;
                }
                this.keys = cursor.next().iterator();
                if (!keys.hasNext()) {
                    return null;
                }
            }
            String key = keys.next();
            return readPersistenceInfo(key, lastChunk(key));
        }
    }

    private void writeLargeByteArray(String instanceKey, String itemKey, Function<String, String> keySupplier, byte[] bytes) {
        hashFactory.fromData(bytes).ifPresentOrElse(item -> writeLargeByteArray(item, instanceKey, itemKey, keySupplier, bytes),
                () -> operations.add(tx -> hashCommands(tx).hset(instanceKey, keySupplier.apply(itemKey), bytes)));
    }

    private void writeLargeByteArray(HashItem hashItem, String instanceKey, String itemKey,
            Function<String, String> keySupplier, byte[] bytes) {
        HashIndex index = hashCoordinator.calculateIndex(instanceKey, hashItem, bytes);
        ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        try (WorkflowOutputBuffer out = factory.output(byteStream)) {
            hashItem.writeKey(out);
            out.writeBytes(index.toBytes());
        }
        operations
                .add(tx -> hashCommands(tx).hset(instanceKey, keySupplier.apply(idxKey(itemKey)),
                        new byte[] { hashItem.id() }));
        operations.add(tx -> hashCommands(tx).hset(instanceKey, keySupplier.apply(itemKey), byteStream.toByteArray()));
    }

    private byte[] readLargeByteArray(String key, byte[] data, byte[] idByte) {
        if (idByte == null) {
            return data;
        }
        ByteArrayInputStream byteStream = new ByteArrayInputStream(data);
        try (WorkflowInputBuffer input = factory.input(byteStream)) {
            return hashFactory.fromBuffer(idByte[0], input).map(item -> {
                return hashCoordinator.readBytes(key, item, hashFactory.indexFromBytes(input.readBytes())).orElseThrow();
            }).orElse(data);
        }
    }

    private String lastChunk(String key) {
        return key.substring(key.lastIndexOf(SEPARATOR) + 1);
    }

    private PersistenceWorkflowInfo readPersistenceInfo(String key, String instanceId) {
        Map<String, byte[]> instanceData = hashCommands.hgetall(key);
        byte[] schemaVersion = instanceData.get(SCHEMA_VERSION);
        Map<String, PersistenceTaskInfo> tasksInfo = readTasksInfo(instanceData, key, schemaVersion);
        if (schemaVersion == null) {
            tasksInfo = legacyReadTasksInfo(instanceId, tasksInfo);
        }
        return instanceData.isEmpty() ? null
                : new PersistenceWorkflowInfo(instanceId, MarshallingUtils.readInstant(factory,
                        instanceData.get(DATE)),
                        MarshallingUtils.readModel(factory, instanceData.get(INPUT)),
                        MarshallingUtils.readEnum(factory, instanceData.get(STATUS), WorkflowStatus.class), tasksInfo,
                        readMetadata(instanceData, instanceId));
    }

    private Map<String, PersistenceTaskInfo> readTasksInfo(Map<String, byte[]> instanceData, String instanceKey,
            byte[] schemaVersion) {
        Map<String, Map<String, byte[]>> taskMap = new HashMap<>();
        for (Entry<String, byte[]> item : instanceData.entrySet()) {
            if (item.getKey().startsWith(TASK_PREFIX)) {
                String[] tokens = item.getKey().split(SEPARATOR);
                taskMap.computeIfAbsent(tokens[1], k -> new HashMap<>()).put(tokens[2], item.getValue());
            }
        }
        return taskMap.entrySet().stream()
                .collect(Collectors.toMap(Entry::getKey, e -> readTaskInfo(e.getValue(), instanceKey)));
    }

    private Map<String, PersistenceTaskInfo> legacyReadTasksInfo(String instanceId,
            Map<String, PersistenceTaskInfo> tasksInfo) {
        // scan key:* for task keys and then hgetall for each one of them
        KeyScanCursor<String> cursor = keyCommands.scan(new KeyScanArgs().match(legacyTaskPrefix(instanceId) + "*"));
        Map<String, PersistenceTaskInfo> result = new HashMap<>(tasksInfo);
        while (cursor.hasNext()) {
            cursor.next().forEach(s -> result.put(lastChunk(s), readTaskInfo(hashCommands.hgetall(s), null)));
        }
        return result;
    }

    private PersistenceTaskInfo readTaskInfo(Map<String, byte[]> data, String instanceKey) {
        TaskStatus status = MarshallingUtils.readEnum(factory, data.get(STATUS), TaskStatus.class);
        if (status == TaskStatus.COMPLETED) {
            return new CompletedTaskInfo(MarshallingUtils.readInstant(factory, data.get(DATE)),
                    MarshallingUtils.readModel(factory,
                            readLargeByteArray(instanceKey, data.get(OUTPUT), data.get(idxKey(OUTPUT)))),
                    MarshallingUtils.readModel(factory,
                            readLargeByteArray(instanceKey, data.get(CONTEXT), data.get(idxKey(CONTEXT)))),
                    MarshallingUtils.readBoolean(factory, data.get(END_NODE)),
                    MarshallingUtils.readString(factory, data.get(NEXT)),
                    MarshallingUtils.readInt(factory, data.get(ITERATION)), readMetadata(data, instanceKey));
        } else if (status == TaskStatus.RETRIED) {
            byte[] retryBytes = data.get(RETRY_ATTEMPT);
            return new RetriedTaskInfo(retryBytes.length == 4 ? MarshallingUtils.readInt(factory, retryBytes)
                    : MarshallingUtils.readShort(factory, retryBytes), readMetadata(data, instanceKey));
        } else {
            throw new IllegalArgumentException("Unsupported status " + status);
        }
    }

    @Override
    public Optional<PersistenceWorkflowInfo> readWorkflowInfo(WorkflowDefinition definition, String instanceId) {
        return Optional.ofNullable(readPersistenceInfo(key(definition, instanceId), instanceId));
    }

    private TransactionalHashCommands<String, String, byte[]> hashCommands(TransactionalRedisDataSource tx) {
        if (txHashCommands == null) {
            txHashCommands = tx.hash(byte[].class);
        }
        return txHashCommands;
    }

    private TransactionalKeyCommands<String> keyCommands(TransactionalRedisDataSource tx) {
        if (txKeyCommands == null) {
            txKeyCommands = tx.key(String.class);
        }
        return txKeyCommands;
    }

    private TransactionalSetCommands<String, String> setCommands(TransactionalRedisDataSource tx) {
        if (txSetCommands == null) {
            txSetCommands = tx.set(String.class);
        }
        return txSetCommands;
    }

    private String key(WorkflowContextData workflowContext) {
        return key(workflowContext.definition(), workflowContext.instanceData().id());
    }

    private String key(WorkflowDefinitionData definition, String instanceId) {
        return prefixId(definition.application().id(), definition)
                + instanceId;
    }

    private String prefixId(String applicationId, WorkflowDefinitionData definition) {
        return applicationId + SEPARATOR + definition.id().toString(SEPARATOR) + SEPARATOR;
    }

    private String taskId(TaskContextData taskContext, String name) {
        return taskId(taskContext.position().jsonPointer(), name);
    }

    private String taskId(String position, String name) {
        return TASK_PREFIX + position + SEPARATOR + name;
    }

    private static String idxKey(String itemName) {
        return itemName + IDX;
    }

    private static String metaKey(String metaKey) {
        return META + metaKey;
    }

    private static String blobSetKey(String key) {
        return BLOB + SEPARATOR + key;
    }

    private String blobKey(String setKey, String key) {
        return setKey + SEPARATOR + key;
    }

    private String legacyTaskPrefix(String instanceId) {
        return instanceId + SEPARATOR;
    }
}
