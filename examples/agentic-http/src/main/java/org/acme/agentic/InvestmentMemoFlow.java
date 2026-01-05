package org.acme.agentic;

import static io.serverlessworkflow.fluent.func.FuncWorkflowBuilder.workflow;
import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.agent;
import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.get;

import io.quarkiverse.flow.Flow;
import io.serverlessworkflow.api.types.Workflow;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Map;

/**
 * Workflow that:
 * <p>
 * 1. Calls an HTTP endpoint to fetch market data for a ticker. 2. Pipes the result into a LangChain4j agent which
 * writes an InvestmentMemo.
 */
@ApplicationScoped
public class InvestmentMemoFlow extends Flow {

    private final InvestmentAnalystAgent analyst;

    @Inject
    public InvestmentMemoFlow(InvestmentAnalystAgent analyst) {
        this.analyst = analyst;
    }

    @Override
    public Workflow descriptor() {
        // tag::workflow[]
        return workflow("investment-memo").tasks(
                // 1) Fetch market data via HTTP and turn it into an InvestmentPrompt
                // tag::http-step[]
                get("fetchMarketData", "http://localhost:8080/market-data/{ticker}").outputAs((result, wf, tf) -> {
                    // This is the original task input, as sent by the workflow user
                    // It has the user's objective and horizon
                    // It could be a record, but we use as a Map to exemplify how to handle this type of object in the
                    // example.
                    final Map<String, Object> input = tf.input().asMap().orElseThrow();
                    // This is the task output before the outputAs filter
                    final String response = tf.rawOutput().asText().orElseThrow();
                    return new InvestmentPrompt(result.ticker(), input.get("objective").toString(),
                            input.get("horizon").toString(), response);
                }, MarketDataSnapshot.class),
                // end::http-step[]

                // 2) Call the LLM-backed investment analyst agent
                agent("investmentAnalyst", analyst::analyse, InvestmentPrompt.class)).build();
        // end::workflow[]
    }
}
