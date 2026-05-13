package io.quarkiverse.flow.recorders;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.eclipse.microprofile.config.ConfigProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.quarkiverse.flow.metrics.MicrometerExecutionListener;
import io.quarkiverse.flow.providers.CredentialsProviderSecretManager;
import io.quarkiverse.flow.providers.FaultToleranceProvider;
import io.quarkiverse.flow.providers.HttpClientProvider;
import io.quarkiverse.flow.providers.JQScopeSupplier;
import io.quarkiverse.flow.providers.QuarkusManagedExecutorServiceFactory;
import io.quarkiverse.flow.providers.WorkflowTaskContext;
import io.quarkiverse.flow.tracing.TraceLoggerExecutionListener;
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

@Singleton
public class WorkflowApplicationCreator {

    private static final Logger LOG = LoggerFactory.getLogger(WorkflowApplicationCreator.class);

    @Inject
    QuarkusManagedExecutorServiceFactory executorServiceFactory;

    @Inject
    JQScopeSupplier jqScopeSupplier;

    @Inject
    HttpClientProvider httpClientProvider;

    @Inject
    FaultToleranceProvider faultToleranceProvider;

    @Inject
    @Any
    Instance<WorkflowExecutionListener> executionListeners;

    @Inject
    @Any
    Instance<WorkflowExecutionCompletableListener> completableListeners;

    @Inject
    @Any
    Instance<EventConsumer<?, ?>> eventConsumers;

    @Inject
    @Any
    Instance<EventPublisher> eventPublishers;

    @Inject
    @Any
    Instance<SecretManager> secretManagers;

    @Inject
    @Any
    Instance<ConfigManager> configManagers;

    @Inject
    Instance<MicrometerExecutionListener> micrometerListeners;

    @Inject
    @Any
    Instance<WorkflowApplicationBuilderCustomizer> customizers;

    public WorkflowApplication create(boolean tracingEnabled, boolean isMicrometerSupported) {
        final Builder builder = WorkflowApplication.builder();
        if (tracingEnabled) {
            LOG.info("Flow: Tracing enabled");
            builder.withListener(new TraceLoggerExecutionListener());
        }
        builder.withContextFactory(new JavaModelFactory()).withModelFactory(new JacksonModelFactory());

        injectAppId(builder);
        injectExecutorServiceFactory(builder);
        injectJQExpressionFactory(builder);
        injectEventConsumers(builder);
        injectEventPublishers(builder);
        injectSecretManager(builder);
        injectConfigManager(builder);
        injectHttpClientProvider(builder);
        injectMicrometerListener(builder);
        injectFaultTolerance(builder, isMicrometerSupported);
        injectCustomListeners(builder);

        customizers.stream().forEachOrdered(customizer -> customizer.customize(builder));

        return builder.build();
    }

    private void injectAppId(final Builder builder) {
        ConfigProvider.getConfig().getOptionalValue("quarkus.application.name", String.class).ifPresent(builder::withId);
    }

    private void injectExecutorServiceFactory(Builder builder) {
        builder.withExecutorFactory(executorServiceFactory);
        LOG.debug("Flow: Bound ExecutorServiceFactory bean: {}", executorServiceFactory.getClass().getName());
    }

    private void injectCustomListeners(Builder builder) {
        final Set<Class<?>> internalListeners = Set.of(
                TraceLoggerExecutionListener.class,
                MicrometerExecutionListener.class);

        executionListeners.stream()
                .filter(listener -> !internalListeners.contains(listener.getClass()))
                .forEach(listener -> {
                    builder.withListener(listener);
                    LOG.debug("Flow: Bound WorkflowExecutionListener bean: {}", listener.getClass().getName());
                });

        completableListeners.stream()
                .forEach(listener -> {
                    builder.withListener(listener);
                    LOG.debug("Flow: Bound WorkflowExecutionCompletableListener bean: {}", listener.getClass().getName());
                });
    }

    private void injectEventConsumers(final Builder builder) {
        if (eventConsumers.isResolvable()) {
            EventConsumer<?, ?> consumer = eventConsumers.get();
            builder.withEventConsumer(consumer);
            LOG.info("Flow: Bound EventConsumer bean: {}", consumer.getClass().getName());
        } else if (eventConsumers.isAmbiguous()) {
            throw new IllegalStateException(
                    "Multiple EventConsumer beans found. Provide exactly one, or configure selection.");
        } else {
            LOG.info("Flow: No EventConsumer bean found; using default fallback.");
        }
    }

    private void injectEventPublishers(final Builder builder) {
        final List<EventPublisher> pubHandles = eventPublishers.stream().toList();
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

    private void injectSecretManager(final Builder builder) {
        List<SecretManager> managers = secretManagers.stream().toList();

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

    private void injectConfigManager(final Builder builder) {
        if (configManagers.isResolvable()) {
            ConfigManager configManager = configManagers.get();
            builder.withConfigManager(configManager);
            LOG.info("Flow: Bound ConfigManager bean: {}", configManager.getClass().getName());
        } else if (configManagers.isAmbiguous()) {
            throw new IllegalStateException(
                    "Multiple ConfigManager beans found. Must exist only one in the classpath");
        } else {
            LOG.info("Flow: No ConfigManager bean found; workflow runtime 'secrets' expression and definitions won't work.");
        }
    }

    private void injectHttpClientProvider(final Builder builder) {
        LOG.info("Flow: Bound HttpClientProvider bean: {}", httpClientProvider.getClass().getName());
        builder.withAdditionalObject(HttpClientResolver.HTTP_CLIENT_PROVIDER, ((workflowContextData, taskContextData) -> {
            final String workflowName = workflowContextData.definition().workflow().getDocument().getName();
            final String taskName = taskContextData.taskName();
            return httpClientProvider.clientFor(workflowName, taskName);
        }));
    }

    private void injectMicrometerListener(Builder builder) {
        if (micrometerListeners.isResolvable()) {
            builder.withListener(micrometerListeners.get());
        }
    }

    private void injectJQExpressionFactory(Builder builder) {
        builder.withExpressionFactory(new JQExpressionFactory(jqScopeSupplier));
    }

    private void injectFaultTolerance(Builder builder, boolean isMicrometerSupported) {
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
