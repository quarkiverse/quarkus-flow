package io.quarkiverse.flow.persistence.jpa;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.context.ManagedExecutor;

import io.serverlessworkflow.impl.WorkflowContextData;
import io.serverlessworkflow.impl.persistence.AbstractPersistenceInstanceWriter;
import io.serverlessworkflow.impl.persistence.PersistenceInstanceOperations;

@ApplicationScoped
public class JpaInstanceWriter extends AbstractPersistenceInstanceWriter {

    @Inject
    ManagedExecutor service;

    @Inject
    JpaInstanceOperations operations;

    private Map<String, CompletableFuture<Void>> futuresMap;

    @PostConstruct
    void init() {
        futuresMap = new ConcurrentHashMap<>();
    }

    @Override
    protected CompletableFuture<Void> doTransaction(Consumer<PersistenceInstanceOperations> operation,
            WorkflowContextData context) {
        Runnable runnable = () -> operation.accept(operations);
        return futuresMap.compute(
                context.instanceData().id(),
                (k, v) -> v == null
                        ? CompletableFuture.runAsync(runnable, service)
                        : v.thenRunAsync(runnable, service));
    }

    @Override
    public void close() {
        futuresMap.clear();
    }
}
