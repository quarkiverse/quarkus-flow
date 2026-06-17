package io.quarkiverse.flow.persistence.jpa;

import java.util.Optional;
import java.util.concurrent.ExecutorService;

import io.smallrye.context.api.ManagedExecutorConfig;
import jakarta.enterprise.context.ApplicationScoped;

import jakarta.inject.Inject;
import org.eclipse.microprofile.context.ManagedExecutor;
import org.eclipse.microprofile.context.ThreadContext;

import io.serverlessworkflow.impl.persistence.AbstractAsyncPersistenceExecutor;

@ApplicationScoped
public class JpaPersistenceExecutor extends AbstractAsyncPersistenceExecutor {

    @Inject
    // See https://github.com/quarkiverse/quarkus-flow/issues/652
    @ManagedExecutorConfig(cleared = ThreadContext.TRANSACTION)
    ManagedExecutor service;

    @Override
    protected Optional<ExecutorService> executorService() {
        return Optional.of(service);
    }
}
