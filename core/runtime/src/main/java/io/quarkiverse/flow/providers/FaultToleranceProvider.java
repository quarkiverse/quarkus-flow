package io.quarkiverse.flow.providers;

import java.io.IOException;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.util.TypeLiteral;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Metrics;
import io.quarkiverse.flow.config.FlowHttpConfig;
import io.quarkiverse.flow.config.FlowMetricsConfig;
import io.quarkiverse.flow.config.HttpClientConfig;
import io.quarkiverse.flow.metrics.FlowMetrics;
import io.quarkus.arc.Arc;
import io.serverlessworkflow.impl.WorkflowModel;
import io.smallrye.common.annotation.Identifier;
import io.smallrye.faulttolerance.api.TypedGuard;

@ApplicationScoped
public class FaultToleranceProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger(FaultToleranceProvider.class);

    private final FlowHttpConfig flowHttpConfig;
    private final RoutingNameResolver routingNameResolver;
    private final Map<String, TypedGuard<CompletionStage<WorkflowModel>>> namedGuards = new ConcurrentHashMap<>();
    private final FlowMetricsConfig flowMetricsConfig;
    private static final List<Class<? extends Throwable>> DEFAULT_CLASSES = List.of(
            IOException.class,
            ConnectException.class,
            SocketTimeoutException.class,
            NoRouteToHostException.class,
            SocketException.class,
            UnknownHostException.class,
            TimeoutException.class,
            RuntimeException.class);

    private volatile TypedGuard<CompletionStage<WorkflowModel>> defaultGuard;

    public FaultToleranceProvider(FlowHttpConfig flowHttpConfig, FlowMetricsConfig flowMetricsConfig) {
        this.flowHttpConfig = flowHttpConfig;
        this.flowMetricsConfig = flowMetricsConfig;
        this.routingNameResolver = new RoutingNameResolver(flowHttpConfig);
    }

    public TypedGuard<CompletionStage<WorkflowModel>> guardFor(WorkflowTaskContext ctx) {
        String guardName = this.routingNameResolver.resolveName(ctx.workflowName(), ctx.taskName());
        if (guardName == null) {
            return getOrCreateDefaultGuard(ctx);
        }
        return getOrCreateNamedGuard(guardName, ctx);
    }

    private TypedGuard<CompletionStage<WorkflowModel>> getOrCreateDefaultGuard(WorkflowTaskContext ctx) {
        final TypedGuard<CompletionStage<WorkflowModel>> existing = defaultGuard;
        if (existing != null) {
            return existing;
        }
        synchronized (this) {
            if (defaultGuard == null) {
                LOGGER.debug("Creating default TypedGuard");
                defaultGuard = buildGuard(flowHttpConfig, ctx);
            }
            return defaultGuard;
        }
    }

    private TypedGuard<CompletionStage<WorkflowModel>> getOrCreateNamedGuard(String name, WorkflowTaskContext metadata) {
        return namedGuards.computeIfAbsent(name, key -> buildNamedGuard(name, metadata));
    }

    private TypedGuard<CompletionStage<WorkflowModel>> buildNamedGuard(String name, WorkflowTaskContext metadata) {
        HttpClientConfig namedConfig = flowHttpConfig.named().get(name);
        if (namedConfig == null) {
            LOGGER.debug("Using default TypedGuard for '{}'", name);
            return getOrCreateDefaultGuard(metadata);
        }
        LOGGER.debug("Creating named TypedGuard '{}'", name);
        return buildGuard(namedConfig, metadata);
    }

    private TypedGuard<CompletionStage<WorkflowModel>> buildGuard(HttpClientConfig httpClientConfig, WorkflowTaskContext ctx) {

        HttpClientConfig.ResilienceConfig resilienceConfig = httpClientConfig.resilience();

        if (resilienceConfig.identifier().isPresent()) {
            return Arc.container().select(new TypeLiteral<TypedGuard<CompletionStage<WorkflowModel>>>() {
            }, Identifier.Literal.of(resilienceConfig.identifier().get())).get();
        }

        TypedGuard.Builder<CompletionStage<WorkflowModel>> builder = TypedGuard
                .create(new TypeLiteral<>() {
                });

        if (resilienceConfig.retry().enabled().get()) {
            configureRetry(ctx, builder, resilienceConfig);
        }

        return builder.build();
    }

    private void configureRetry(WorkflowTaskContext ctx, TypedGuard.Builder<CompletionStage<WorkflowModel>> builder,
            HttpClientConfig.ResilienceConfig resilienceConfig) {
        TypedGuard.Builder.RetryBuilder<CompletionStage<WorkflowModel>> retryBuilder = builder.withRetry();

        if (ctx.isMicrometerSupported() && flowMetricsConfig.enabled()) {
            retryBuilder
                    .onRetry(() -> sendOnRetryMetric(ctx))
                    .onFailure(() -> sendOnFailureMetric(ctx));
        }

        List<Class<? extends Throwable>> retryOnExceptions = new ArrayList<>();
        if (resilienceConfig.retry().exceptions().isPresent()) {
            retryBuilder.retryOn(configureOnRetryExceptions(resilienceConfig));
        } else {
            retryBuilder.retryOn(DEFAULT_CLASSES);
        }

        retryBuilder
                .maxRetries(resilienceConfig.retry().maxRetries().getAsInt())
                .delay(resilienceConfig.retry().delay().toMillis(), ChronoUnit.MILLIS)
                .jitter(resilienceConfig.retry().jitter().toMillis(), ChronoUnit.MILLIS)
                .done()
                .build();
    }

    private List<Class<? extends Throwable>> configureOnRetryExceptions(HttpClientConfig.ResilienceConfig resilienceConfig) {
        List<Class<? extends Throwable>> retryOnExceptions = new ArrayList<>();
        for (String exception : resilienceConfig.retry().exceptions().get()) {
            try {
                Class<?> clazz = Thread.currentThread().getContextClassLoader().loadClass(exception);
                retryOnExceptions.add((Class<? extends Throwable>) clazz);
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(
                        "Invalid retry exception configuration: class '" + exception +
                                "' must exist on the classpath and be a subtype of java.lang.Throwable.");
            }
        }
        retryOnExceptions.addAll(DEFAULT_CLASSES);
        return retryOnExceptions;
    }

    private void sendOnFailureMetric(WorkflowTaskContext ctx) {
        Counter.builder(FlowMetrics.FAULT_TOLERANCE_TASK_FAILURE.prefixedWith(flowMetricsConfig.prefix().get()))
                .description("Fault Tolerance Task Failure")
                .tag("taskName", ctx.taskName())
                .tag("workflow", ctx.workflowName())
                .register(Metrics.globalRegistry)
                .increment();
    }

    private void sendOnRetryMetric(WorkflowTaskContext ctx) {
        Counter.builder(FlowMetrics.FAULT_TOLERANCE_TASK_RETRY.prefixedWith(flowMetricsConfig.prefix().get()))
                .description("Fault Tolerance Task Retry")
                .tag("taskName", ctx.taskName())
                .tag("workflow", ctx.workflowName())
                .register(Metrics.globalRegistry)
                .increment();

    }
}
