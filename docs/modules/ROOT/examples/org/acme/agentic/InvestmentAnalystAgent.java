package org.acme.agentic;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.RegisterAiService;
import jakarta.enterprise.context.ApplicationScoped;

// tag::agent[]

/**
 * Simple investment analyst agent.
 * <p>
 * It receives an {@link InvestmentPrompt} (ticker + JSON market snapshot) and returns an {@link InvestmentMemo} with a
 * short recommendation.
 */
@ApplicationScoped
@RegisterAiService
@SystemMessage("""
        You are a careful, conservative investment analyst.

        Given:
        - a stock ticker
        - a description of the investment objective
        - an investment horizon
        - and a compact JSON snapshot of market data,

        you MUST respond with a short JSON document that can be mapped to:
          InvestmentMemo {
            String summary;
            String stance;      // BUY, HOLD or AVOID
            List<String> keyRisks;
          }

        Be concise and avoid marketing language.
        """)
public interface InvestmentAnalystAgent {

    /**
     * Analyze the prompt and produce an investment memo.
     *
     * @param memoryId
     *        Conversation / workflow memory id (provided by Quarkus Flow).
     * @param prompt
     *        Ticker, objective, horizon and raw market-data JSON.
     */
    @UserMessage("""
            Ticker: {prompt.ticker}
            Objective: {prompt.objective}
            Horizon: {prompt.horizon}

            Here is the JSON market-data snapshot you should analyze:

            {prompt.marketDataJson}

            Produce an InvestmentMemo JSON as specified above.
            """)
    InvestmentMemo analyse(@MemoryId String memoryId, @V("prompt") InvestmentPrompt prompt);
}
// end::agent[]
