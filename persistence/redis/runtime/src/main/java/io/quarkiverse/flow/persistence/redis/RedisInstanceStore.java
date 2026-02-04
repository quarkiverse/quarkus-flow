package io.quarkiverse.flow.persistence.redis;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkus.redis.datasource.ReactiveRedisDataSource;
import io.quarkus.redis.datasource.hash.ReactiveHashCommands;
import io.quarkus.redis.datasource.keys.ReactiveKeyCommands;
import io.serverlessworkflow.impl.marshaller.WorkflowBufferFactory;
import io.serverlessworkflow.impl.persistence.PersistenceInstanceStore;
import io.serverlessworkflow.impl.persistence.PersistenceInstanceTransaction;

@ApplicationScoped
public class RedisInstanceStore implements PersistenceInstanceStore {

    private final ReactiveRedisDataSource ds;
    private final WorkflowBufferFactory factory;
    private final ReactiveKeyCommands<String> keyCommands;
    private final ReactiveHashCommands<String, String, byte[]> hashCommands;

    public RedisInstanceStore(ReactiveRedisDataSource ds, WorkflowBufferFactory factory) {
        this.ds = ds;
        this.factory = factory;
        this.keyCommands = ds.key(String.class);
        this.hashCommands = ds.hash(String.class, String.class, byte[].class);
    }

    @Override
    public PersistenceInstanceTransaction begin() {
        return new RedisInstanceTransaction(ds, keyCommands, hashCommands, factory);
    }

}
