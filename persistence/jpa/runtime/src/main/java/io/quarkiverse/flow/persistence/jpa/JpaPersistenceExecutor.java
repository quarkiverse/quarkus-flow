package io.quarkiverse.flow.persistence.jpa;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.eclipse.microprofile.context.ManagedExecutor;

import io.serverlessworkflow.impl.WorkflowDefinitionData;
import io.serverlessworkflow.impl.persistence.AbstractAsyncPersistenceExecutor;

@ApplicationScoped
public class JpaPersistenceExecutor extends AbstractAsyncPersistenceExecutor {

    @Inject
    ManagedExecutor service;

    @Override
    @Transactional
    public <T> CompletableFuture<T> execute(Supplier<T> runnable, WorkflowDefinitionData definition) {
        return super.execute(runnable, definition);
    }

    @Override
    @Transactional
    public CompletableFuture<Void> execute(Runnable runnable, WorkflowDefinitionData definition) {
        return super.execute(runnable, definition);
    }

    @Override
    protected Optional<ExecutorService> executorService() {
        return Optional.of(service);
    }
}
