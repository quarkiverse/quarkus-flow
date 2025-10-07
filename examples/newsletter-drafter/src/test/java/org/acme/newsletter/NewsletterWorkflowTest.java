package org.acme.newsletter;

import java.util.Map;
import java.util.concurrent.ExecutionException;

import org.acme.newsletter.domain.Length;
import org.acme.newsletter.domain.NewsletterInput;
import org.acme.newsletter.domain.Tone;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
public class NewsletterWorkflowTest {

    @Inject
    NewsletterWorkflow workflow;

    @Test
    void basic_agent_chain_check() throws ExecutionException, InterruptedException {
        String result =
                workflow.instance(new NewsletterInput("buillish", "AAPL:+3%, MSFT:+2%", "CPI cooling; jobs steady", Tone.FRIENDLY, Length.MEDIUM))
                        .start().get().asText().orElseThrow();

        assertThat(result).isNotEmpty();
    }

}
