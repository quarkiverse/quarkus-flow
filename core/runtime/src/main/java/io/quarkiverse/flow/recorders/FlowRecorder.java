package io.quarkiverse.flow.recorders;

import java.lang.reflect.Field;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import jakarta.enterprise.inject.Any;
import jakarta.enterprise.util.TypeLiteral;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkiverse.flow.Flow;
import io.quarkiverse.flow.providers.JQScopeSupplier;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ArcContainer;
import io.quarkus.arc.InjectableInstance;
import io.quarkus.runtime.ShutdownContext;
import io.quarkus.runtime.annotations.Recorder;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.impl.WorkflowApplication;
import io.serverlessworkflow.impl.WorkflowDefinition;
import io.serverlessworkflow.impl.events.EventConsumer;
import io.serverlessworkflow.impl.events.EventPublisher;
import io.serverlessworkflow.impl.expressions.jq.JQExpressionFactory;
import io.serverlessworkflow.impl.jackson.JsonUtils;

// TODO: produce definitions from workflows in the YAML format within the current classpath

/**
 * Registries all Workflow definitions found in the classpath built via the Java DSL.
 */
@Recorder
public class FlowRecorder {

    private static final Logger LOG = LoggerFactory.getLogger(FlowRecorder.class);

    // TODO: expose WorkflowApplication build via configuration
    // TODO: add injected providers from user
    // TODO: wire persistence/REST/RESTClient/etc infrastructure to the WorkflowApplication

    public Supplier<WorkflowApplication> workflowAppSupplier(ShutdownContext shutdownContext) {
        return () -> {
            final ArcContainer container = Arc.container();
            final WorkflowApplication.Builder builder = WorkflowApplication.builder()
                    .withExpressionFactory(new JQExpressionFactory(container.instance(JQScopeSupplier.class).get()));

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

            WorkflowApplication app = builder.build();

            shutdownContext.addShutdownTask(app::close);
            return app;
        };
    }

    public Supplier<WorkflowDefinition> workflowDefinitionSupplier(String flowDescriptorClassName) {
        return () -> {
            try {
                final WorkflowApplication app = Arc.container().instance(WorkflowApplication.class).get();
                final ClassLoader cl = Thread.currentThread().getContextClassLoader();
                final Class<?> flowClass = Class.forName(flowDescriptorClassName, true, cl);

                Flow flow = (Flow) Arc.container().instance(flowClass, Any.Literal.INSTANCE).get();
                final Workflow wf = flow.descriptor();

                return app.workflowDefinition(wf);
            } catch (RuntimeException re) {
                throw re;
            } catch (Throwable e) {
                throw new RuntimeException("Failed to create WorkflowDefinition for " + flowDescriptorClassName, e);
            }
        };
    }

    /**
     * Replace the static ObjectMapper from the SDK with Quarkus' bean if presented on classpath
     */
    public void injectQuarkusObjectMapper() {
        InjectableInstance<ObjectMapper> mapper = Arc.container().select(ObjectMapper.class);
        if (mapper.isResolvable()) {
            try {
                final Class<?> ju = Class.forName(JsonUtils.class.getName());
                final Field fi = ju.getDeclaredField("mapper");
                fi.setAccessible(true);
                fi.set(null, mapper.get());
            } catch (Exception e) {
                throw new RuntimeException("Failed to replace JsonUtils ObjectMapper's", e);
            }
        }

    }
}
