package io.quarkiverse.flow.persistence.redis;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.hash.HashCommands;
import io.quarkus.redis.datasource.hash.TransactionalHashCommands;
import io.quarkus.redis.datasource.keys.KeyCommands;
import io.quarkus.redis.datasource.keys.KeyScanArgs;
import io.quarkus.redis.datasource.keys.KeyScanCursor;
import io.quarkus.redis.datasource.keys.TransactionalKeyCommands;
import io.quarkus.redis.datasource.transactions.TransactionalRedisDataSource;
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
    private final static String SEPARATOR = ":";

    private final RedisDataSource ds;
    private final WorkflowBufferFactory factory;
    private final KeyCommands<String> keyCommands;
    private final HashCommands<String, String, byte[]> hashCommands;

    private final List<Consumer<TransactionalRedisDataSource>> operations;

    private TransactionalHashCommands<String, String, byte[]> txHashCommands;
    private TransactionalKeyCommands<String> txKeyCommands;

    public RedisInstanceTransaction(RedisDataSource ds, KeyCommands<String> keyCommands,
            HashCommands<String, String, byte[]> hashCommands,
            WorkflowBufferFactory factory) {
        this.ds = ds;
        this.keyCommands = keyCommands;
        this.hashCommands = hashCommands;
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
    }

    @Override
    public void rollback(WorkflowDefinitionData definition) {
    }

    @Override
    public void writeInstanceData(WorkflowContextData workflowContext) {
        String instanceId = key(workflowContext);
        operations.add(tx -> hashCommands(tx).hset(instanceId, DATE,
                MarshallingUtils.writeInstant(factory, workflowContext.instanceData().startedAt())));
        operations.add(tx -> hashCommands(tx).hset(instanceId, INPUT,
                MarshallingUtils.writeModel(factory, workflowContext.instanceData().input())));
    }

    @Override
    public void writeRetryTask(WorkflowContextData workflowContext, TaskContextData taskContext) {
        String key = taskId(workflowContext, taskContext);
        operations.add(tx -> hashCommands(tx).hset(key, STATUS, MarshallingUtils.writeEnum(factory, TaskStatus.RETRIED)));
        operations.add(tx -> hashCommands(tx).hset(key, RETRY_ATTEMPT,
                MarshallingUtils.writeShort(factory, ((TaskContext) taskContext).retryAttempt())));
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
    }

    @Override
    public void writeStatus(WorkflowContextData workflowContext, WorkflowStatus status) {
        operations.add(tx -> hashCommands(tx).hset(key(workflowContext), STATUS, MarshallingUtils.writeEnum(factory, status)));
    }

    @Override
    public void removeProcessInstance(WorkflowContextData workflowContext) {
        operations.add(tx -> keyCommands(tx).del(key(workflowContext)));
    }

    @Override
    public void clearStatus(WorkflowContextData workflowContext) {
        operations.add(tx -> hashCommands(tx).hdel(key(workflowContext), STATUS));
    }

    @Override
    public Stream<PersistenceWorkflowInfo> scanAll(String applicationId, WorkflowDefinition definition) {
        KeyScanCursor<String> cursor = keyCommands
                .scan(new KeyScanArgs().match(prefixId(applicationId, definition) + "*"));
        if (!cursor.hasNext()) {
            return Stream.empty();
        }
        PersistenceWorkflowInfoGenerator generator = new PersistenceWorkflowInfoGenerator(cursor);
        return Stream.iterate(generator.next(), generator, generator);
    }

    private class PersistenceWorkflowInfoGenerator
            implements UnaryOperator<PersistenceWorkflowInfo>, Predicate<PersistenceWorkflowInfo> {

        private final KeyScanCursor<String> cursor;
        private Iterator<String> keys;

        public PersistenceWorkflowInfoGenerator(KeyScanCursor<String> cursor) {
            this.cursor = cursor;
            keys();
        }

        private void keys() {
            this.keys = cursor.next().iterator();
        }

        public PersistenceWorkflowInfo next() {
            if (!keys.hasNext()) {
                keys();
            }
            String key = keys.next();
            return readPersistenceInfo(key, lastChunk(key));
        }

        @Override
        public PersistenceWorkflowInfo apply(PersistenceWorkflowInfo t) {
            return next();
        }

        @Override
        public boolean test(PersistenceWorkflowInfo t) {
            return keys.hasNext() || cursor.hasNext();
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
        // scan key:* for task keys and then hgetall for each one of them
        KeyScanCursor<String> cursor = keyCommands.scan(new KeyScanArgs().match(taskPrefix(instanceId) + "*"));
        Map<String, PersistenceTaskInfo> result = new HashMap<>();
        while (cursor.hasNext()) {
            cursor.next().forEach(s -> result.put(lastChunk(s), readTaskInfo(s)));
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
                    MarshallingUtils.readString(factory, data.get(NEXT)));
        } else if (status == TaskStatus.RETRIED) {
            return new RetriedTaskInfo(MarshallingUtils.readShort(factory, data.get(RETRY_ATTEMPT)));
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

    private String key(WorkflowContextData workflowContext) {
        return key(workflowContext.definition(), workflowContext.instanceData().id());
    }

    private String key(WorkflowDefinitionData definition, String instanceId) {
        return prefixId(definition.application().id(), definition)
                + instanceId;
    }

    private String prefixId(String applicationId, WorkflowDefinitionData definition) {
        return definition.application().id() + SEPARATOR + definition.id().toString(SEPARATOR) + SEPARATOR;
    }

    private String taskId(WorkflowContextData workflowContext, TaskContextData taskContext) {
        return taskPrefix(workflowContext.instanceData().id()) + taskContext.position().jsonPointer();
    }

    private String taskPrefix(String instanceId) {
        return instanceId + SEPARATOR;
    }
}
