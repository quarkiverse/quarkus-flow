package io.quarkiverse.flow.providers;

import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.util.TypeLiteral;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.quarkiverse.flow.config.FlowHttpConfig;
import io.quarkiverse.flow.config.HttpClientConfig;
import io.quarkus.logging.Log;
import io.serverlessworkflow.impl.WorkflowModel;
import io.smallrye.faulttolerance.api.TypedGuard;

@ApplicationScoped
public class FaultToleranceProvider {

    private static final Logger LOG = LoggerFactory.getLogger(FaultToleranceProvider.class);

    private final FlowHttpConfig config;
    private final RoutingNameResolver routingNameResolver;
    private final Map<String, TypedGuard<CompletionStage<WorkflowModel>>> namedGuards = new ConcurrentHashMap<>();

    private volatile TypedGuard<CompletionStage<WorkflowModel>> defaultGuard;

    public FaultToleranceProvider(FlowHttpConfig config) {
        this.config = config;
        this.routingNameResolver = new RoutingNameResolver(config);
    }

    public TypedGuard<CompletionStage<WorkflowModel>> guardFor(String workflowName, String taskName) {
        String guardName = this.routingNameResolver.resolveName(workflowName, taskName);
        if (guardName == null) {
            return getOrCreateDefaultGuard();
        }
        return getOrCreateNamedGuard(guardName);
    }

    private TypedGuard<CompletionStage<WorkflowModel>> getOrCreateDefaultGuard() {
        final TypedGuard<CompletionStage<WorkflowModel>> existing = defaultGuard;
        if (existing != null) {
            return existing;
        }
        synchronized (this) {
            if (defaultGuard == null) {
                Log.debug("Creating default HttpClient");
                defaultGuard = buildGuard(config);
            }
            return defaultGuard;
        }
    }

    private TypedGuard<CompletionStage<WorkflowModel>> getOrCreateNamedGuard(String name) {
        return namedGuards.computeIfAbsent(name, this::buildNamedGuard);
    }

    private TypedGuard<CompletionStage<WorkflowModel>> buildNamedGuard(String name) {
        HttpClientConfig namedConfig = config.named().get(name);
        if (namedConfig == null) {
            Log.debugf("Using default HTTP client for '%s'", name);
            return getOrCreateDefaultGuard();
        }
        Log.debugf("Creating named HttpClient '%s'", name);
        return buildGuard(namedConfig);
    }

    private TypedGuard<CompletionStage<WorkflowModel>> buildGuard(HttpClientConfig httpClientConfig) {

        TypedGuard.Builder<CompletionStage<WorkflowModel>> builder = TypedGuard
                .create(new TypeLiteral<>() {
                });

        if (httpClientConfig.retryEnabled().get()) {
            builder.withRetry()
                    .maxRetries(httpClientConfig.maxRetries().getAsInt())
                    .done();
        }

        return builder.build();
    }
}
