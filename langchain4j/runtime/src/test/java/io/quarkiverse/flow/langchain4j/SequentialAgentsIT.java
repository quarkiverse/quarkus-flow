package io.quarkiverse.flow.langchain4j;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class SequentialAgentsIT {

    @Inject
    Agents.StoryCreatorWithConfigurableStyleEditor storyCreator;

    @Test
    void checkSequenceWorkflow() {
        assertThat(storyCreator).isNotNull();

        final String story = storyCreator.write("dungeons and dragons", "fantasy", "nerds");
        assertThat(story).isNotEmpty();
    }

}
