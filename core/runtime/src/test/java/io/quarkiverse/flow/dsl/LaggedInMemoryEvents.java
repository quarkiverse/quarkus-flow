package io.quarkiverse.flow.dsl;

import java.util.concurrent.CompletableFuture;

import io.cloudevents.CloudEvent;
import io.serverlessworkflow.impl.events.InMemoryEvents;

public class LaggedInMemoryEvents extends InMemoryEvents {

    @Override
    public CompletableFuture<Void> publish(CloudEvent ce) {

        return super.publish(ce)
                .thenRun(
                        () -> {
                            try {
                                Thread.sleep(50);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                        });
    }
}
