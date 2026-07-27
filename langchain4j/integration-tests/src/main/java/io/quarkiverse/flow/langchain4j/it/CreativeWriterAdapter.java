package io.quarkiverse.flow.langchain4j.it;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class CreativeWriterAdapter {

    @Inject
    Agents.StoryCreatorWithMaxSentences storyAgent;

    public String writeStory(CreativeWriterRequest req) {
        return storyAgent.write(req.topic(), req.style(), req.audience(), req.maxSentences());
    }

}
