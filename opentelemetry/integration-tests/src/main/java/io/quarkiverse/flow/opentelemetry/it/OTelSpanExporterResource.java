package io.quarkiverse.flow.opentelemetry.it;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;

import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.data.SpanData;

@ApplicationScoped
@Path("otel-span-exporter")
public class OTelSpanExporterResource {

    @Inject
    InMemorySpanExporter exporter;

    @Path("spans")
    @GET
    @jakarta.ws.rs.Consumes(APPLICATION_JSON)
    @jakarta.ws.rs.Produces(APPLICATION_JSON)
    public List<SpanData> getSpans() {
        List<SpanData> spans = exporter.getFinishedSpanItems();
        return spans;
    }

    @Path("reset-spans")
    @DELETE
    @jakarta.ws.rs.Produces(APPLICATION_JSON)
    public Response resetSpans() {
        exporter.reset();
        return Response.status(204).build();
    }

    @ApplicationScoped
    static class InMemorySpanExporterProducer {
        @Produces
        @Singleton
        InMemorySpanExporter inMemorySpanExporter() {
            return InMemorySpanExporter.create();
        }
    }
}
