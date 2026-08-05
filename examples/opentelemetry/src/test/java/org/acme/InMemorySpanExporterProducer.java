package org.acme;

import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

@ApplicationScoped
public class InMemorySpanExporterProducer {

    @Produces
    @Singleton
    InMemorySpanExporter inMemorySpanExporter() {
        return InMemorySpanExporter.create();
    }

}