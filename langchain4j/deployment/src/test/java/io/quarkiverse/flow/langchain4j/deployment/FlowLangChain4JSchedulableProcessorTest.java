package io.quarkiverse.flow.langchain4j.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.SequenceAgent;
import io.quarkiverse.flow.langchain4j.annotations.ScheduleOn;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkus.test.QuarkusUnitTest;

public class FlowLangChain4JSchedulableProcessorTest {

    @RegisterExtension
    static final QuarkusUnitTest test = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(Agentic.class)
                    .addAsResource(new StringAsset("quarkus.http.test-port=0"), "application.properties"))
            .assertException(throwable -> assertThat(throwable).isInstanceOf(IllegalStateException.class));

    @Test
    @DisplayName("Should throw when @ScheduleOn has both event and every configured")
    void should_fail_when_scheduleOn_has_both_event_and_interval_configured() {
        Assertions.assertTrue(true);
    }

    static class Agentic {

        @RegisterAiService
        public interface StoryPlanner {
            @SequenceAgent(subAgents = {
                    StoryCreator.class
            })
            @ScheduleOn(event = "story.requested", every = "PT10M")
            String plan();
        }

        public interface StoryCreator {
            @Agent("agent")
            String create();
        }
    }
}
