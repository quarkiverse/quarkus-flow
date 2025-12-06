package org.acme.agentic;

import java.math.BigDecimal;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * Simple in-memory market data endpoint used by the InvestmentMemoFlow.
 * <p>
 * GET /market-data/{ticker}
 */
@Path("/market-data")
@Produces(MediaType.APPLICATION_JSON)
public class MarketDataResource {

    @GET
    @Path("/{ticker}")
    public MarketDataSnapshot marketData(@PathParam("ticker") String ticker) {
        String normalized = ticker.toUpperCase();

        BigDecimal price;
        double pe;
        double dividendYield;
        String currency = "USD";

        switch (normalized) {
            case "CSU.TO" -> {
                price = new BigDecimal("3170.25");
                pe = 28.4;
                dividendYield = 0.003;
                currency = "CAD";
            }
            case "RY.TO" -> {
                price = new BigDecimal("130.10");
                pe = 12.5;
                dividendYield = 0.043;
                currency = "CAD";
            }
            default -> {
                price = new BigDecimal("100.00");
                pe = 15.0;
                dividendYield = 0.0;
            }
        }

        return new MarketDataSnapshot(normalized, price, pe, dividendYield, currency);
    }
}
