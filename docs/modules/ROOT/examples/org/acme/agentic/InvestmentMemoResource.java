package org.acme.agentic;

import java.util.Map;
import java.util.concurrent.CompletionStage;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

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
    public CompletionStage<InvestmentMemo> analyse(@PathParam("ticker") String ticker) {
        return flow.instance(Map.of("ticker", ticker, "objective", "Long-term growth", "horizon", "3–5 years"))
                .start()
                .thenApply(data -> data.as(InvestmentMemo.class).orElseThrow());
    }
}
// end::resource[]