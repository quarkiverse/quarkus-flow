package io.quarkiverse.flow.deployment.test;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.util.TypeLiteral;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.cloudevents.CloudEvent;
import io.quarkus.arc.Arc;
import io.quarkus.arc.InjectableInstance;
import io.quarkus.test.QuarkusUnitTest;
import io.serverlessworkflow.impl.events.AbstractTypeConsumer;
import io.serverlessworkflow.impl.events.EventConsumer;
import io.serverlessworkflow.impl.events.EventPublisher;

public class EventAdaptersUnremovableTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(CustomEventPublisher.class, CustomEventConsumer.class))
            .withConfigurationResource("application-test-random.properties");

    @Test
    void customPublisherAndConsumerAreAvailable() {
        assertTrue(Arc.container().instance(EventPublisher.class).isAvailable());
        InjectableInstance<EventConsumer<?, ?>> consumerHandle = Arc.container().select(new TypeLiteral<>() {
        });
        assertTrue(consumerHandle.isResolvable());
    }

    @ApplicationScoped
    public static class CustomEventPublisher implements EventPublisher {
        @Override
        public CompletableFuture<Void> publish(CloudEvent event) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void close() {
        }
    }

    @ApplicationScoped
    public static class CustomEventConsumer extends AbstractTypeConsumer {
        @Override
        protected void registerToAll(Consumer<CloudEvent> consumer) {
        }

        @Override
        protected void unregisterFromAll() {
        }

        @Override
        protected void register(String type, Consumer<CloudEvent> consumer) {
        }

        @Override
        protected void unregister(String type) {
        }

        @Override
        public void close() {
        }
    }
}
