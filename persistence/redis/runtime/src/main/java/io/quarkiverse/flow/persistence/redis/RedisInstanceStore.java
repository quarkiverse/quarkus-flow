package io.quarkiverse.flow.persistence.redis;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkus.arc.Unremovable;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.hash.HashCommands;
import io.quarkus.redis.datasource.keys.KeyCommands;
import io.quarkus.redis.datasource.set.SetCommands;
import io.quarkus.redis.datasource.value.ValueCommands;
import io.serverlessworkflow.impl.marshaller.WorkflowBufferFactory;
import io.serverlessworkflow.impl.persistence.PersistenceInstanceStore;
import io.serverlessworkflow.impl.persistence.PersistenceInstanceTransaction;
import io.serverlessworkflow.impl.persistence.hashing.*;

@ApplicationScoped
@Unremovable
public class RedisInstanceStore implements PersistenceInstanceStore {

    private final RedisDataSource ds;
    private final WorkflowBufferFactory factory;
    private final KeyCommands<String> keyCommands;
    private final HashCommands<String, String, byte[]> hashCommands;
    private final ValueCommands<String, String> valueCommands;
    private final SetCommands<String, String> setCommands;
    private final HashFactory hashFactory;

    public RedisInstanceStore(RedisDataSource ds, WorkflowBufferFactory factory, HashFactory hashFactory) {
        this.ds = ds;
        this.factory = factory;
        this.hashFactory = hashFactory;
        this.keyCommands = ds.key(String.class);
        this.hashCommands = ds.hash(String.class, String.class, byte[].class);
        this.setCommands = ds.set(String.class);
        this.valueCommands = ds.value(String.class);

    }

    @Override
    public PersistenceInstanceTransaction begin() {
        return new RedisInstanceTransaction(ds, keyCommands, hashCommands, valueCommands, setCommands, factory, hashFactory);
    }
}
