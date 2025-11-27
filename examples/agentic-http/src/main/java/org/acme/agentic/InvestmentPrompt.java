package org.acme.agentic;

/**
 * Input sent to the {@link InvestmentAnalystAgent}.
 *
 * It combines the user's intent (objective / horizon) with
 * the raw JSON market data returned by the HTTP task.
 */
public record InvestmentPrompt(
        String ticker,
        String objective,
        String horizon,
        String marketDataJson) {
}
