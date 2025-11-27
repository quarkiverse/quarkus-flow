package org.acme.agentic;

import java.math.BigDecimal;

/**
 * Simple DTO representing the market data returned by MarketDataResource.
 */
public record MarketDataSnapshot(String ticker, BigDecimal price, double pe, double dividendYield, String currency) {
}
