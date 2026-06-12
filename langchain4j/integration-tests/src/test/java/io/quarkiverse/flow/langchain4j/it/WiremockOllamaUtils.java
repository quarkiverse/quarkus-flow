package io.quarkiverse.flow.langchain4j.it;

public class WiremockOllamaUtils {

    private WiremockOllamaUtils() {
    }

    /**
     * Creates an Ollama-format chat response JSON for plain text content.
     */
    public static String ollamaResponse(String content) {
        return """
                {
                  "model": "llama3.2",
                  "created_at": "2024-01-01T00:00:00.000000Z",
                  "message": {
                    "role": "assistant",
                    "content": "%s"
                  },
                  "done": true,
                  "total_duration": 1000000000,
                  "load_duration": 100000000,
                  "prompt_eval_count": 50,
                  "prompt_eval_duration": 200000000,
                  "eval_count": 100,
                  "eval_duration": 700000000
                }
                """.formatted(content.replace("\"", "\\\"").replace("\n", "\\n"));
    }

    /**
     * Creates an Ollama-format chat response JSON for raw JSON content (arrays, objects).
     * The content is already JSON so we escape inner quotes properly.
     */
    public static String ollamaResponseRaw(String jsonContent) {
        // Escape quotes inside the JSON content for embedding in the outer JSON string
        String escaped = jsonContent.replace("\"", "\\\"");
        return """
                {
                  "model": "llama3.2",
                  "created_at": "2024-01-01T00:00:00.000000Z",
                  "message": {
                    "role": "assistant",
                    "content": "%s"
                  },
                  "done": true,
                  "total_duration": 1000000000,
                  "load_duration": 100000000,
                  "prompt_eval_count": 50,
                  "prompt_eval_duration": 200000000,
                  "eval_count": 100,
                  "eval_duration": 700000000
                }
                """.formatted(escaped);
    }
}
