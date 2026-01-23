package io.quarkiverse.flow.recorders;

import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import jakarta.enterprise.inject.Any;
import jakarta.enterprise.util.TypeLiteral;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.quarkiverse.flow.metrics.MicrometerExecutionListener;
import io.quarkiverse.flow.providers.CredentialsProviderSecretManager;
import io.quarkiverse.flow.providers.HttpClientProvider;
import io.quarkiverse.flow.providers.JQScopeSupplier;
import io.quarkiverse.flow.tracing.TraceLoggerExecutionListener;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ArcContainer;
import io.quarkus.arc.InjectableInstance;
import io.quarkus.arc.InstanceHandle;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.ShutdownContext;
import io.quarkus.runtime.annotations.Recorder;
import io.serverlessworkflow.impl.WorkflowApplication;
import io.serverlessworkflow.impl.WorkflowApplication.Builder;
import io.serverlessworkflow.impl.config.ConfigManager;
import io.serverlessworkflow.impl.config.SecretManager;
import io.serverlessworkflow.impl.events.EventConsumer;
import io.serverlessworkflow.impl.events.EventPublisher;
import io.serverlessworkflow.impl.executors.http.HttpClientResolver;
import io.serverlessworkflow.impl.expressions.jq.JQExpressionFactory;

@Recorder
public class WorkflowApplicationRecorder {
    private static final Logger LOG = LoggerFactory.getLogger(WorkflowApplicationRecorder.class);

    public RuntimeValue<WorkflowApplication.Builder> workflowAppBuilderSupplier(boolean tracingEnabled) {
        WorkflowApplication.Builder builder = WorkflowApplication.builder();
        if (tracingEnabled) {
            LOG.info("Flow: Tracing enabled");
            builder.withListener(new TraceLoggerExecutionListener());
        }
        return new RuntimeValue<>(builder);
    }

    public Supplier<WorkflowApplication> workflowAppSupplier(RuntimeValue<Builder> builderWrapper,
            ShutdownContext shutdownContext) {
        return () -> {
            final ArcContainer container = Arc.container();
            final Builder builder = builderWrapper.getValue();
            builder.withExpressionFactory(new JQExpressionFactory(container.instance(JQScopeSupplier.class).get()));

            InstanceHandle<MicrometerExecutionListener> instance = container
                    .instance(MicrometerExecutionListener.class);
            if (instance.isAvailable()) {
                builder.withListener(instance.get());
            }
            this.injectEventConsumers(container, builder);
            this.injectEventPublishers(container, builder);
            this.injectSecretManager(container, builder);
            this.injectConfigManager(container, builder);
            this.injectHttpClientProvider(container, builder);
            WorkflowApplication app = builder.build();
            shutdownContext.addShutdownTask(app::close);
            return app;
        };
    }

    private void injectEventConsumers(final ArcContainer container, final WorkflowApplication.Builder builder) {
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

    private void injectEventPublishers(final ArcContainer container, final WorkflowApplication.Builder builder) {
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

    private void injectSecretManager(final ArcContainer container, final WorkflowApplication.Builder builder) {
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

    private void injectConfigManager(final ArcContainer container, final WorkflowApplication.Builder builder) {
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

    private void injectHttpClientProvider(final ArcContainer container, final WorkflowApplication.Builder builder) {
        final HttpClientProvider httpClientProvider = container.instance(HttpClientProvider.class).get();
        LOG.info("Flow: Bound HttpClientProvider bean: {}", httpClientProvider.getClass().getName());
        builder.withAdditionalObject(HttpClientResolver.HTTP_CLIENT_PROVIDER, ((workflowContextData, taskContextData) -> {
            final String workflowName = workflowContextData.definition().workflow().getDocument().getName();
            final String taskName = taskContextData.taskName();
            return httpClientProvider.clientFor(workflowName, taskName);
        }));
    }

}
