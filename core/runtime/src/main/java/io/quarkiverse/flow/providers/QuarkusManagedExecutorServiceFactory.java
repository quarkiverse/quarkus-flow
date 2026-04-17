package io.quarkiverse.flow.providers;

import java.util.concurrent.ExecutorService;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import org.eclipse.microprofile.context.ManagedExecutor;

import io.quarkus.arc.Unremovable;
import io.serverlessworkflow.impl.ExecutorServiceFactory;

/**
 * Quarkus-aware ExecutorServiceFactory that uses ManagedExecutor for proper
 * context propagation in async workflow execution.
 * <p>
 * This ensures that CompletableFutures returned by HTTP and other async tasks
 * properly propagate Quarkus request context and can signal completion to
 * JAX-RS async processing.
 */
@ApplicationScoped
@Unremovable
public class QuarkusManagedExecutorServiceFactory implements ExecutorServiceFactory {

    @Inject
    Instance<ManagedExecutor> managedExecutor;

    @Override
    public ExecutorService get() {
        // Use ManagedExecutor if available, otherwise fall back to default
        if (managedExecutor.isResolvable())
            return managedExecutor.get();
        throw new IllegalStateException(
                "ManagedExecutor not available. Ensure quarkus-smallrye-context-propagation is on the classpath.");
    }

    @Override
    public void close() {
        // ManagedExecutor is managed by Quarkus container, don't close it
    }
}
