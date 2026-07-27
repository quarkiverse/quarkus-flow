package io.quarkiverse.flow.langchain4j.it;

public record CreativeWriterRequest(String topic, String style, String audience, Integer maxSentences) {
}
