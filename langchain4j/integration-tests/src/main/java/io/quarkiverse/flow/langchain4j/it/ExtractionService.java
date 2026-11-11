package io.quarkiverse.flow.langchain4j.it;

import jakarta.enterprise.context.ApplicationScoped;

import dev.langchain4j.invocation.InvocationParameters;

/**
 * Calls {@link Agents.ExtractionRootAgent} the way an application would: with an all-null "nothing known yet" record
 * and an {@link InvocationParameters} carrying out-of-band execution context (#1111).
 * <p>
 * Living in {@code src/main} also gives the agentic extension an injection point it can see during the production
 * build, so the root agent's parameters are recognised as user-provided.
 */
@ApplicationScoped
public class ExtractionService {

    private final Agents.ExtractionRootAgent rootAgent;

    ExtractionService(Agents.ExtractionRootAgent rootAgent) {
        this.rootAgent = rootAgent;
    }

    public Agents.ExtractedFields extract(String input) {
        return rootAgent.process(input, new Agents.ExtractedFields(null, null),
                InvocationParameters.from("tenant", "acme"));
    }
}
