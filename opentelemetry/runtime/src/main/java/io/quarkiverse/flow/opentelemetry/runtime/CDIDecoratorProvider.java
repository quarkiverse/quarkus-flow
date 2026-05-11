package io.quarkiverse.flow.opentelemetry.runtime;

import java.util.concurrent.atomic.AtomicReference;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.interceptor.Interceptor;

import io.quarkus.arc.Unremovable;
import io.quarkus.runtime.StartupEvent;

@ApplicationScoped
@Unremovable
public class CDIDecoratorProvider {
    private static final AtomicReference<CDIOTelEmittedEventDecorator> EVENT_DECORATOR_INSTANCE = new AtomicReference<>();

    void onStart(@Observes @Priority(Interceptor.Priority.PLATFORM_BEFORE) StartupEvent event,
            CDIOTelEmittedEventDecorator eventDecorator) {
        EVENT_DECORATOR_INSTANCE.set(eventDecorator);
    }

    public static CDIOTelEmittedEventDecorator getEventDecorator() {
        return EVENT_DECORATOR_INSTANCE.get();
    }
}
