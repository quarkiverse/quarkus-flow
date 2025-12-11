package org.acme.agentic;

import java.util.Map;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import io.smallrye.mutiny.Uni;

/**
 * Simple JAX-RS entrypoint that drives the InvestmentMemoFlow.
 * <p>
 * GET /investments/{ticker}
 */
// tag::resource[]
@Path("/investments")
public class InvestmentMemoResource {

    @Inject
    InvestmentMemoFlow flow;

    @GET
    @Path("/{ticker}")
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<InvestmentMemo> analyse(@PathParam("ticker") String ticker) {
        return flow.startInstance(Map.of("ticker", ticker, "objective", "Long-term growth", "horizon", "3–5 years"))
                .onItem().transform(data -> data.as(InvestmentMemo.class).orElseThrow());
    }
}
// end::resource[]
