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
import dev.langchain4j.service.V;
import io.quarkiverse.flow.langchain4j.annotations.ScheduleOn;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkus.test.QuarkusUnitTest;

public class FlowLangChain4JSchedulableIntervalWithArgsProcessorTest {

    @RegisterExtension
    static final QuarkusUnitTest test = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(Agentic.class)
                    .addAsResource(new StringAsset("quarkus.http.test-port=0"), "application.properties"))
            .assertException(throwable -> assertThat(throwable).isInstanceOf(IllegalStateException.class));

    @Test
    @DisplayName("Should fail when every trigger schedules a method with arguments")
    void should_fail_when_interval_trigger_schedules_a_method_with_arguments() {
        Assertions.assertTrue(true);
    }

    static class Agentic {

        @RegisterAiService
        public interface StoryPlanner {
            @SequenceAgent(subAgents = {
                    StoryCreator.class
            })
            @ScheduleOn(every = "PT10M")
            String plan(@V("topic") String topic);
        }

        public interface StoryCreator {
            @Agent("agent")
            String create();
        }
    }
}
