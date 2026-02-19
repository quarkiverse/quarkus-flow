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
import java.util.concurrent.atomic.AtomicLong;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.util.TypeLiteral;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Metrics;
import io.quarkiverse.flow.config.FlowHttpConfig;
import io.quarkiverse.flow.config.FlowMetricsConfig;
import io.quarkiverse.flow.config.HttpClientConfig;
import io.quarkiverse.flow.metrics.FlowMetrics;
import io.quarkus.arc.Arc;
import io.serverlessworkflow.impl.WorkflowModel;
import io.smallrye.common.annotation.Identifier;
import io.smallrye.faulttolerance.api.CircuitBreakerState;
import io.smallrye.faulttolerance.api.TypedGuard;

@ApplicationScoped
public class FaultToleranceProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger(FaultToleranceProvider.class);

    private final FlowHttpConfig flowHttpConfig;
    private final RoutingNameResolver routingNameResolver;
    private final Map<String, TypedGuard<CompletionStage<WorkflowModel>>> namedGuards = new ConcurrentHashMap<>();
    private final FlowMetricsConfig flowMetricsConfig;
    private final ConcurrentHashMap<CircuitBreakerKey, CircuitBreakerCounters> countersForGauge = new ConcurrentHashMap<>();
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

        if (resilienceConfig.circuitBreaker().enabled().get()) {
            configureCircuitBreaker(ctx, builder, resilienceConfig);
        }

        return builder.build();
    }

    private void configureCircuitBreaker(WorkflowTaskContext ctx, TypedGuard.Builder<CompletionStage<WorkflowModel>> builder,
            HttpClientConfig.ResilienceConfig resilienceConfig) {
        var circuitBreakerBuilder = builder.withCircuitBreaker()
                .failureRatio(resilienceConfig.circuitBreaker().failureRatio().getAsDouble())
                .requestVolumeThreshold(resilienceConfig.circuitBreaker().requestVolumeThreshold().getAsInt())
                .successThreshold(resilienceConfig.circuitBreaker().successThreshold().getAsInt())
                .delay(resilienceConfig.circuitBreaker().delay().toMillis(), ChronoUnit.MILLIS);

        if (resilienceConfig.circuitBreaker().exceptions().isPresent()) {
            circuitBreakerBuilder.failOn(configureOnRetryExceptions(resilienceConfig.circuitBreaker().exceptions().get()));
        } else {
            circuitBreakerBuilder.failOn(DEFAULT_CLASSES);
        }

        if (ctx.isMicrometerSupported() && flowMetricsConfig.enabled()) {
            circuitBreakerBuilder
                    .onFailure(() -> sendCircuitBreakerFailure(ctx))
                    .onPrevented(() -> sendCircuitBreakerPrevented(ctx))
                    .onStateChange(state -> {
                        CircuitBreakerKey key = new CircuitBreakerKey(ctx.workflowName(), ctx.taskName());
                        onCircuitBreakerStateChange(state, key);
                    })
                    .done();
        }
    }

    private void configureRetry(WorkflowTaskContext ctx, TypedGuard.Builder<CompletionStage<WorkflowModel>> builder,
            HttpClientConfig.ResilienceConfig resilienceConfig) {
        TypedGuard.Builder.RetryBuilder<CompletionStage<WorkflowModel>> retryBuilder = builder.withRetry();

        if (ctx.isMicrometerSupported() && flowMetricsConfig.enabled()) {
            retryBuilder
                    .onRetry(() -> sendOnRetryMetric(ctx))
                    .onFailure(() -> sendOnFailureMetric(ctx));
        }

        if (resilienceConfig.retry().exceptions().isPresent()) {
            retryBuilder.retryOn(configureOnRetryExceptions(resilienceConfig.retry().exceptions().get()));
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

    private List<Class<? extends Throwable>> configureOnRetryExceptions(List<String> exceptions) {
        List<Class<? extends Throwable>> retryOnExceptions = new ArrayList<>();
        for (String exception : exceptions) {
            try {
                Class<?> clazz = Thread.currentThread().getContextClassLoader().loadClass(exception);
                retryOnExceptions.add((Class<? extends Throwable>) clazz);
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(
                        "Invalid exception configuration: class '" + exception +
                                "' must exist on the classpath and be a subtype of java.lang.Throwable.");
            }
        }
        retryOnExceptions.addAll(DEFAULT_CLASSES);
        return retryOnExceptions;
    }

    private void sendOnFailureMetric(WorkflowTaskContext ctx) {
        Counter.builder(FlowMetrics.FAULT_TOLERANCE_TASK_RETRY_FAILURE_TOTAL.prefixedWith(flowMetricsConfig.prefix().get()))
                .description("Fault Tolerance Task Failure")
                .tag("task", ctx.taskName())
                .tag("workflow", ctx.workflowName())
                .register(Metrics.globalRegistry)
                .increment();
    }

    private void sendOnRetryMetric(WorkflowTaskContext ctx) {
        Counter.builder(FlowMetrics.FAULT_TOLERANCE_TASK_RETRY_TOTAL.prefixedWith(flowMetricsConfig.prefix().get()))
                .description("Fault Tolerance Task Retry")
                .tag("task", ctx.taskName())
                .tag("workflow", ctx.workflowName())
                .register(Metrics.globalRegistry)
                .increment();

    }

    private void sendCircuitBreakerPrevented(WorkflowTaskContext ctx) {
        Counter.builder(
                FlowMetrics.FAULT_TOLERANCE_CIRCUIT_BREAKER_PREVENTED_TOTAL.prefixedWith(flowMetricsConfig.prefix().get()))
                .description("Fault Tolerance Circuit Breaker Prevented")
                .tag("task", ctx.taskName())
                .tag("workflow", ctx.workflowName())
                .register(Metrics.globalRegistry)
                .increment();
    }

    private void sendCircuitBreakerFailure(WorkflowTaskContext ctx) {
        Counter.builder(
                FlowMetrics.FAULT_TOLERANCE_CIRCUIT_BREAKER_FAILURE_TOTAL.prefixedWith(flowMetricsConfig.prefix().get()))
                .description("Fault Tolerance Circuit Breaker Failure")
                .tag("task", ctx.taskName())
                .tag("workflow", ctx.workflowName())
                .register(Metrics.globalRegistry)
                .increment();
    }

    private static class CircuitBreakerCounters {
        private final AtomicLong halfOpen = new AtomicLong();
        private final AtomicLong open = new AtomicLong();
        private final AtomicLong closed = new AtomicLong();
    }

    private record CircuitBreakerKey(String workflowName, String taskName) {
    }

    private void onCircuitBreakerStateChange(CircuitBreakerState state, CircuitBreakerKey key) {
        CircuitBreakerCounters counters = getCircuitBreakerCounters(key);
        switch (state) {
            case CLOSED -> {
                counters.closed.set(1);
                counters.open.set(0);
                counters.halfOpen.set(0);
            }
            case OPEN -> {
                counters.closed.set(0);
                counters.open.set(1);
                counters.halfOpen.set(0);
            }
            case HALF_OPEN -> {
                counters.closed.set(0);
                counters.open.set(0);
                counters.halfOpen.set(1);
            }
        }
    }

    private CircuitBreakerCounters getCircuitBreakerCounters(CircuitBreakerKey key) {
        return countersForGauge.computeIfAbsent(key, workflowMetadata -> {

            CircuitBreakerCounters circuitBreakerCounters = new CircuitBreakerCounters();

            Gauge.builder(FlowMetrics.FAULT_TOLERANCE_CIRCUIT_BREAKER_OPEN.prefixedWith(flowMetricsConfig.prefix().get()),
                    circuitBreakerCounters.open, AtomicLong::get)
                    .description("Circuit Breaker Currently Open")
                    .tag("workflow", workflowMetadata.workflowName())
                    .tag("task", workflowMetadata.taskName())
                    .register(Metrics.globalRegistry);

            Gauge.builder(FlowMetrics.FAULT_TOLERANCE_CIRCUIT_BREAKER_HALF_OPEN.prefixedWith(flowMetricsConfig.prefix().get()),
                    circuitBreakerCounters.halfOpen, AtomicLong::get)
                    .description("Circuit Breaker Currently Half Open")
                    .tag("workflow", workflowMetadata.workflowName())
                    .tag("task", workflowMetadata.taskName())
                    .register(Metrics.globalRegistry);

            Gauge.builder(FlowMetrics.FAULT_TOLERANCE_CIRCUIT_BREAKER_CLOSED.prefixedWith(flowMetricsConfig.prefix().get()),
                    circuitBreakerCounters.closed, AtomicLong::get)
                    .description("Circuit Breaker Currently Closed")
                    .tag("workflow", workflowMetadata.workflowName())
                    .tag("task", workflowMetadata.taskName())
                    .register(Metrics.globalRegistry);

            return circuitBreakerCounters;
        });
    }
}
