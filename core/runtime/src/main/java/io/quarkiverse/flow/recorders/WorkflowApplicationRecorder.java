package io.quarkiverse.flow.recorders;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import jakarta.enterprise.inject.Any;
import jakarta.enterprise.util.TypeLiteral;

import org.eclipse.microprofile.config.ConfigProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.quarkiverse.flow.metrics.MicrometerExecutionListener;
import io.quarkiverse.flow.providers.CredentialsProviderSecretManager;
import io.quarkiverse.flow.providers.FaultToleranceProvider;
import io.quarkiverse.flow.providers.HttpClientProvider;
import io.quarkiverse.flow.providers.JQScopeSupplier;
import io.quarkiverse.flow.providers.WorkflowTaskContext;
import io.quarkiverse.flow.tracing.TraceLoggerExecutionListener;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ArcContainer;
import io.quarkus.arc.InjectableInstance;
import io.quarkus.arc.InstanceHandle;
import io.quarkus.runtime.ShutdownContext;
import io.quarkus.runtime.annotations.Recorder;
import io.serverlessworkflow.api.types.CallHTTP;
import io.serverlessworkflow.api.types.CallOpenAPI;
import io.serverlessworkflow.api.types.TaskBase;
import io.serverlessworkflow.impl.WorkflowApplication;
import io.serverlessworkflow.impl.WorkflowApplication.Builder;
import io.serverlessworkflow.impl.WorkflowModel;
import io.serverlessworkflow.impl.config.ConfigManager;
import io.serverlessworkflow.impl.config.SecretManager;
import io.serverlessworkflow.impl.events.EventConsumer;
import io.serverlessworkflow.impl.events.EventPublisher;
import io.serverlessworkflow.impl.executors.CallableTask;
import io.serverlessworkflow.impl.executors.CallableTaskProxyBuilder;
import io.serverlessworkflow.impl.executors.http.HttpClientResolver;
import io.serverlessworkflow.impl.expressions.jq.JQExpressionFactory;
import io.serverlessworkflow.impl.lifecycle.WorkflowExecutionCompletableListener;
import io.serverlessworkflow.impl.lifecycle.WorkflowExecutionListener;
import io.serverlessworkflow.impl.model.func.JavaModelFactory;
import io.serverlessworkflow.impl.model.jackson.JacksonModelFactory;
import io.smallrye.faulttolerance.api.TypedGuard;

@Recorder
public class WorkflowApplicationRecorder {
    private static final Logger LOG = LoggerFactory.getLogger(WorkflowApplicationRecorder.class);

    public Supplier<WorkflowApplication> workflowAppSupplier(ShutdownContext shutdownContext, boolean tracingEnabled,
            boolean isMicrometerSupported) {
        return () -> {
            final WorkflowApplication.Builder builder = WorkflowApplication.builder();
            if (tracingEnabled) {
                LOG.info("Flow: Tracing enabled");
                builder.withListener(new TraceLoggerExecutionListener());
            }
            builder.withContextFactory(new JavaModelFactory()).withModelFactory(new JacksonModelFactory());

            final ArcContainer container = Arc.container();

            this.injectAppId(builder);
            this.injectJQExpressionFactory(builder, container);
            this.injectEventConsumers(container, builder);
            this.injectEventPublishers(container, builder);
            this.injectSecretManager(container, builder);
            this.injectConfigManager(container, builder);
            this.injectHttpClientProvider(container, builder);
            this.injectMicrometerListener(container, builder);
            this.injectFaultTolerance(container, builder, isMicrometerSupported);
            this.injectCustomListeners(container, builder);

            // customize
            container.select(WorkflowApplicationBuilderCustomizer.class, Any.Literal.INSTANCE)
                    .stream()
                    .forEachOrdered(customizer -> customizer.customize(builder));

            WorkflowApplication app = builder.build();
            shutdownContext.addShutdownTask(app::close);
            return app;
        };
    }

    private void injectAppId(final Builder builder) {
        ConfigProvider.getConfig().getOptionalValue("quarkus.application.name", String.class).ifPresent(builder::withId);
    }

    private void injectCustomListeners(ArcContainer container, Builder builder) {
        // List of internal classes
        final Set<Class<?>> internalListeners = Set.of(
                TraceLoggerExecutionListener.class,
                MicrometerExecutionListener.class);

        container
                .select(WorkflowExecutionListener.class, Any.Literal.INSTANCE)
                .stream()
                .filter(listener -> !internalListeners.contains(listener.getClass()))
                .forEach(listener -> {
                    builder.withListener(listener);
                    LOG.debug("Flow: Bound WorkflowExecutionListener bean: {}", listener.getClass().getName());
                });

        container
                .select(WorkflowExecutionCompletableListener.class, Any.Literal.INSTANCE)
                .stream()
                .forEach(listener -> {
                    builder.withListener(listener);
                    LOG.debug("Flow: Bound WorkflowExecutionCompletableListener bean: {}", listener.getClass().getName());
                });
    }

    private void injectEventConsumers(final ArcContainer container, final Builder builder) {
        final InjectableInstance<EventConsumer<?, ?>> consumerHandle = container.select(new TypeLiteral<>() {
        }, Any.Literal.INSTANCE);
        if (consumerHandle.isResolvable()) {
            EventConsumer<?, ?> consumer = consumerHandle.get();
            builder.withEventConsumer(consumer);
            LOG.info("Flow: Bound EventConsumer bean: {}", consumer.getClass().getName());
        } else if (consumerHandle.isAmbiguous()) {
            throw new IllegalStateException(
                    "Multiple EventConsumer beans found. Provide exactly one, or configure selection.");
        } else {
            LOG.info("Flow: No EventConsumer bean found; using default fallback.");
        }
    }

    private void injectEventPublishers(final ArcContainer container, final Builder builder) {
        final List<EventPublisher> pubHandles = container.select(EventPublisher.class, Any.Literal.INSTANCE).stream()
                .toList();
        if (!pubHandles.isEmpty()) {
            for (EventPublisher pub : pubHandles) {
                builder.withEventPublisher(pub);
            }
            LOG.info("Flow: Bound {} EventPublisher bean(s): {}",
                    pubHandles.size(), pubHandles.stream()
                            .map(p -> p.getClass().getName())
                            .collect(Collectors.joining(", ")));
        } else {
            LOG.info("Flow: No EventPublisher beans found; using default fallback.");
        }
    }

    private void injectSecretManager(final ArcContainer container, final Builder builder) {
        List<SecretManager> managers = container.select(SecretManager.class, Any.Literal.INSTANCE).stream().toList();

        if (managers.isEmpty()) {
            LOG.info("Flow: No SecretManager bean found; skipping so SDK can fall back to MicroProfile Config.");
            return;
        }

        List<SecretManager> usable = managers.stream()
                .filter(sm -> !(sm instanceof CredentialsProviderSecretManager)
                        || ((CredentialsProviderSecretManager) sm).hasAnyProviders())
                .sorted(Comparator.comparingInt(SecretManager::priority))
                .toList();

        if (usable.isEmpty()) {
            LOG.info("Flow: SecretManager beans present but none usable (no CredentialsProvider available); " +
                    "skipping so SDK can fall back to MicroProfile Config.");
        } else {
            SecretManager chosen = usable.get(0);
            builder.withSecretManager(chosen);
            LOG.info("Flow: SecretManager bound: {} (priority: {})",
                    chosen.getClass().getName(), chosen.priority());
        }
    }

    private void injectConfigManager(final ArcContainer container, final Builder builder) {
        final InjectableInstance<ConfigManager> configHandler = container.select(new TypeLiteral<>() {
        }, Any.Literal.INSTANCE);
        if (configHandler.isResolvable()) {
            ConfigManager consumer = configHandler.get();
            builder.withConfigManager(consumer);
            LOG.info("Flow: Bound ConfigManager bean: {}", consumer.getClass().getName());
        } else if (configHandler.isAmbiguous()) {
            throw new IllegalStateException(
                    "Multiple ConfigManager beans found. Must exist only one in the classpath");
        } else {
            LOG.info("Flow: No ConfigManager bean found; workflow runtime 'secrets' expression and definitions won't work.");
        }
    }

    private void injectHttpClientProvider(final ArcContainer container, final Builder builder) {
        final HttpClientProvider httpClientProvider = container.instance(HttpClientProvider.class).get();
        LOG.info("Flow: Bound HttpClientProvider bean: {}", httpClientProvider.getClass().getName());
        builder.withAdditionalObject(HttpClientResolver.HTTP_CLIENT_PROVIDER, ((workflowContextData, taskContextData) -> {
            final String workflowName = workflowContextData.definition().workflow().getDocument().getName();
            final String taskName = taskContextData.taskName();
            return httpClientProvider.clientFor(workflowName, taskName);
        }));
    }

    private void injectMicrometerListener(ArcContainer container, Builder builder) {
        InstanceHandle<MicrometerExecutionListener> instance = container
                .instance(MicrometerExecutionListener.class);
        if (instance.isAvailable()) {
            builder.withListener(instance.get());
        }
    }

    private void injectJQExpressionFactory(Builder builder, ArcContainer container) {
        builder.withExpressionFactory(new JQExpressionFactory(container.instance(JQScopeSupplier.class).get()));
    }

    private void injectFaultTolerance(ArcContainer container, Builder builder, boolean isMicrometerSupported) {
        FaultToleranceProvider faultToleranceProvider = container.instance(FaultToleranceProvider.class).get();
        LOG.info("Flow: Bound FaultToleranceProvider bean: {}", faultToleranceProvider.getClass().getName());
        builder.withCallableProxy(new CallableTaskProxyBuilder() {
            @Override
            public CallableTask build(CallableTask delegate) {
                return (workflowContext, taskContext, input) -> {
                    String workflowName = workflowContext.definition().workflow().getDocument().getName();
                    String taskName = taskContext.taskName();
                    TypedGuard<CompletionStage<WorkflowModel>> guard = faultToleranceProvider
                            .guardFor(new WorkflowTaskContext(workflowName, taskName, isMicrometerSupported));

                    return guard.get(() -> delegate.apply(workflowContext, taskContext, input)).toCompletableFuture();
                };
            }

            @Override
            public boolean accept(TaskBase taskBase) {
                return taskBase instanceof CallHTTP || taskBase instanceof CallOpenAPI;
            }
        });
    }
}
