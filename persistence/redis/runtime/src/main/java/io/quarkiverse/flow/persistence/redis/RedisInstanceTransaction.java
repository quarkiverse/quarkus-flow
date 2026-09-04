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
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
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

public class RedisInstanceTransaction implements PersistenceInstanceTransaction {

    private final static String DATE = "date";
    private final static String STATUS = "status";
    private final static String INPUT = "input";
    private final static String OUTPUT = "output";
    private final static String CONTEXT = "context";
    private final static String RETRY_ATTEMPT = "retryAttempt";
    private final static String END_NODE = "endNode";
    private final static String NEXT = "next";
    private final static String ITERATION = "iteration";
    private final static String SEPARATOR = ":";
    private final static String INDEX_PREFIX = "idx" + SEPARATOR;

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
    private final static String CORRELATION_LOCK_KEY = "lock:correlations";
    private final static int CORRELATION_LOCK_TIMEOUT = 10;
    private final static Logger logger = LoggerFactory.getLogger(RedisInstanceTransaction.class);

    private final RedisDataSource ds;
    private final WorkflowBufferFactory factory;
    private final KeyCommands<String> keyCommands;
    private final HashCommands<String, String, byte[]> hashCommands;
    private final SetCommands<String, String> setCommands;
    private final ValueCommands<String, String> lockCommands;

    private final List<Consumer<TransactionalRedisDataSource>> operations;

    private TransactionalHashCommands<String, String, byte[]> txHashCommands;
    private TransactionalKeyCommands<String> txKeyCommands;
    private TransactionalSetCommands<String, String> txSetCommands;

    private String correlationLockUUID;

    public RedisInstanceTransaction(RedisDataSource ds, KeyCommands<String> keyCommands,
            HashCommands<String, String, byte[]> hashCommands,
            SetCommands<String, String> setCommands,
            WorkflowBufferFactory factory) {
        this.ds = ds;
        this.keyCommands = keyCommands;
        this.hashCommands = hashCommands;
        this.setCommands = setCommands;
        this.lockCommands = ds.value(String.class);
        this.operations = new ArrayList<>();
        this.factory = factory;
    }

    @Override
    public void commit(WorkflowDefinitionData definition) {
        if (!operations.isEmpty()) {
            ds.withTransaction(tx -> {
                operations.forEach(x -> x.accept(tx));
            });
        }
        releaseLock();
    }

    @Override
    public void rollback(WorkflowDefinitionData definition) {
        releaseLock();
    }

    @Override
    public void writeInstanceData(WorkflowContextData workflowContext) {
        String instanceKey = key(workflowContext);
        operations.add(tx -> hashCommands(tx).hset(instanceKey, DATE,
                MarshallingUtils.writeInstant(factory, workflowContext.instanceData().startedAt())));
        operations.add(tx -> hashCommands(tx).hset(instanceKey, INPUT,
                MarshallingUtils.writeModel(factory, workflowContext.instanceData().input())));
        indexMember(workflowContext, instanceKey);
    }

    @Override
    public void writeRetryTask(WorkflowContextData workflowContext, TaskContextData taskContext) {
        String key = taskId(workflowContext, taskContext);
        operations.add(tx -> hashCommands(tx).hset(key, STATUS, MarshallingUtils.writeEnum(factory, TaskStatus.RETRIED)));
        operations.add(tx -> hashCommands(tx).hset(key, RETRY_ATTEMPT,
                MarshallingUtils.writeInt(factory, ((TaskContext) taskContext).retryAttempt())));
        indexMember(workflowContext, key);
    }

    @Override
    public void writeCompletedTask(WorkflowContextData workflowContext,
            TaskContextData taskContext) {
        String key = taskId(workflowContext, taskContext);
        operations.add(tx -> hashCommands(tx).hset(key, STATUS, MarshallingUtils.writeEnum(factory, TaskStatus.COMPLETED)));
        operations.add(
                tx -> hashCommands(tx).hset(key, DATE, MarshallingUtils.writeInstant(factory, taskContext.completedAt())));
        operations.add(tx -> hashCommands(tx).hset(key, OUTPUT, MarshallingUtils.writeModel(factory, taskContext.output())));
        if (workflowContext.context() != null) {
            operations.add(
                    tx -> hashCommands(tx).hset(key, CONTEXT, MarshallingUtils.writeModel(factory, workflowContext.context())));
        }
        TransitionInfo transition = ((TaskContext) taskContext).transition();
        operations.add(
                tx -> hashCommands(tx).hset(key, END_NODE, MarshallingUtils.writeBoolean(factory, transition.isEndNode())));
        AbstractTaskExecutor<?> next = (AbstractTaskExecutor<?>) transition.next();
        if (next != null) {
            operations.add(tx -> hashCommands(tx).hset(key, NEXT,
                    MarshallingUtils.writeString(factory, next.position().jsonPointer())));
        }
        operations.add(tx -> hashCommands(tx).hset(key, ITERATION, writeInt(factory, taskContext.iteration())));
        indexMember(workflowContext, key);
    }

    @Override
    public void writeStatus(WorkflowContextData workflowContext, WorkflowStatus status) {
        operations.add(tx -> hashCommands(tx).hset(key(workflowContext), STATUS, MarshallingUtils.writeEnum(factory, status)));
    }

    @Override
    public void removeProcessInstance(WorkflowContextData workflowContext) {
        String instanceId = workflowContext.instanceData().id();
        String indexKey = indexKey(instanceId);
        Set<String> members = setCommands.smembers(indexKey);
        Set<String> toDelete = new HashSet<>(members);
        toDelete.add(key(workflowContext));
        toDelete.add(indexKey);
        if (members.isEmpty()) {
            // fall back to a one-time keyspace SCAN
            KeyScanCursor<String> keysCursor = keyCommands
                    .scan(new KeyScanArgs().match(taskPrefix(instanceId) + "*"));
            while (keysCursor.hasNext()) {
                keysCursor.next().forEach(toDelete::add);
            }
        }
        String[] keys = toDelete.toArray(new String[0]);
        operations.add(tx -> keyCommands(tx).unlink(keys));
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
            result = lockCommands.setGet(CORRELATION_LOCK_KEY, uuid, new SetArgs().nx().ex(CORRELATION_LOCK_TIMEOUT));
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
            String currentUUID = lockCommands.get(CORRELATION_LOCK_KEY);
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

    private String lastChunk(String key) {
        return key.substring(key.lastIndexOf(SEPARATOR) + 1);
    }

    private PersistenceWorkflowInfo readPersistenceInfo(String key, String instanceId) {
        Map<String, byte[]> instanceData = hashCommands.hgetall(key);
        return instanceData.isEmpty() ? null
                : new PersistenceWorkflowInfo(instanceId, MarshallingUtils.readInstant(factory,
                        instanceData.get(DATE)), MarshallingUtils.readModel(factory, instanceData.get(INPUT)),
                        MarshallingUtils.readEnum(factory, instanceData.get(STATUS), WorkflowStatus.class),
                        readTasksInfo(instanceId));
    }

    private Map<String, PersistenceTaskInfo> readTasksInfo(String instanceId) {
        Map<String, PersistenceTaskInfo> result = new HashMap<>();
        Set<String> members = setCommands.smembers(indexKey(instanceId));
        if (members.isEmpty()) {
            // fall back to a keyspace SCAN for task keys and then hgetall for each one of them
            KeyScanCursor<String> cursor = keyCommands.scan(new KeyScanArgs().match(taskPrefix(instanceId) + "*"));
            while (cursor.hasNext()) {
                cursor.next().forEach(s -> result.put(lastChunk(s), readTaskInfo(s)));
            }
            return result;
        }
        String taskPrefix = taskPrefix(instanceId);
        for (String key : members) {
            if (key.startsWith(taskPrefix)) {
                result.put(lastChunk(key), readTaskInfo(key));
            }
        }
        return result;
    }

    private PersistenceTaskInfo readTaskInfo(String key) {
        Map<String, byte[]> data = hashCommands.hgetall(key);
        TaskStatus status = MarshallingUtils.readEnum(factory, data.get(STATUS), TaskStatus.class);
        if (status == TaskStatus.COMPLETED) {
            return new CompletedTaskInfo(MarshallingUtils.readInstant(factory, data.get(DATE)),
                    MarshallingUtils.readModel(factory, data.get(OUTPUT)),
                    MarshallingUtils.readModel(factory, data.get(CONTEXT)),
                    MarshallingUtils.readBoolean(factory, data.get(END_NODE)),
                    MarshallingUtils.readString(factory, data.get(NEXT)),
                    readInt(factory, data.get(ITERATION)));
        } else if (status == TaskStatus.RETRIED) {
            byte[] retryBytes = data.get(RETRY_ATTEMPT);
            return new RetriedTaskInfo(retryBytes.length == 4 ? MarshallingUtils.readInt(factory, retryBytes)
                    : MarshallingUtils.readShort(factory, retryBytes));
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
            txSetCommands = tx.set(String.class, String.class);
        }
        return txSetCommands;
    }

    private void indexMember(WorkflowContextData workflowContext, String memberKey) {
        String indexKey = indexKey(workflowContext.instanceData().id());
        operations.add(tx -> setCommands(tx).sadd(indexKey, memberKey));
    }

    private String indexKey(String instanceId) {
        return INDEX_PREFIX + instanceId;
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

    private String taskId(WorkflowContextData workflowContext, TaskContextData taskContext) {
        return taskPrefix(workflowContext.instanceData().id()) + taskContext.position().jsonPointer();
    }

    private String taskPrefix(String instanceId) {
        return instanceId + SEPARATOR;
    }

    private static byte[] writeInt(WorkflowBufferFactory factory, int iteration) {
        ByteArrayOutputStream bytesOut = new ByteArrayOutputStream();
        try (WorkflowOutputBuffer buffer = factory.output(bytesOut)) {
            buffer.writeInt(iteration);
        }
        return bytesOut.toByteArray();
    }

    private int readInt(WorkflowBufferFactory factory, byte[] bytes) {
        if (bytes == null) {
            return 0;
        }
        ByteArrayInputStream bytesIn = new ByteArrayInputStream(bytes);
        try (WorkflowInputBuffer buffer = factory.input(bytesIn)) {
            return buffer.readInt();
        }
    }
}
