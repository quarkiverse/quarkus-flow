package io.quarkiverse.flow.persistence.redis;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.hash.HashCommands;
import io.quarkus.redis.datasource.keys.KeyCommands;
import io.serverlessworkflow.impl.marshaller.WorkflowBufferFactory;
import io.serverlessworkflow.impl.persistence.PersistenceInstanceStore;
import io.serverlessworkflow.impl.persistence.PersistenceInstanceTransaction;

@ApplicationScoped
public class RedisInstanceStore implements PersistenceInstanceStore {

    private final RedisDataSource ds;
    private final WorkflowBufferFactory factory;
    private final KeyCommands<String> keyCommands;
    private final HashCommands<String, String, byte[]> hashCommands;

    public RedisInstanceStore(RedisDataSource ds, WorkflowBufferFactory factory) {
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
