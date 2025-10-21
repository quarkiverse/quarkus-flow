package org.acme.newsletter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.TextNode;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class HumanReviewHelper {

    @Inject
    ObjectMapper objectMapper;

    /**
     * Normalizes the listen output to a plain JSON string body.
     * Handles ArrayNode, String, single JsonNode, or arbitrary POJO.
     */
    public String unwrapEventArray(Object in) {
        try {
            if (in instanceof JsonNode node) {
                if (node.isArray()) {
                    ArrayNode arr = (ArrayNode) node;
                    if (arr.isEmpty()) return "";
                    JsonNode first = arr.get(0);
                    return first.isTextual() ? first.asText() : objectMapper.writeValueAsString(first);
                }
                return node.isTextual() ? node.asText() : objectMapper.writeValueAsString(node);
            }
            if (in instanceof String s) return s;
            return objectMapper.writeValueAsString(in);
        } catch (Exception e) {
            // Conservative: return empty so the predicate loops
            return "";
        }
    }

    /**
     * Returns true to loop (needs revision), false to finish.
     * Expects a JSON string body with "status".
     */
    public boolean needsAnotherRevision(String body) {
        try {
            String status = objectMapper.readTree(body).path("status").asText("");
            return !"done".equalsIgnoreCase(status);
        } catch (Exception e) {
            return true; // conservative loop on parse failure
        }
    }

    /**
     * Extracts the `draft` text from a review JSON payload.
     * If parsing fails or `draft` is missing/null, returns the original input.
     */
    public String extractDraft(Object body) {
        try {
            // Expect a TextNode; tolerate plain CharSequence for robustness
            final String text =
                    (body instanceof TextNode tn) ? tn.textValue() :
                            (body instanceof CharSequence cs) ? cs.toString() :
                                    objectMapper.writeValueAsString(body); // last-resort stringify

            JsonNode root = objectMapper.readTree(text); // parse the JSON string
            JsonNode draft = root.path("draft");         // safely navigate
            return (draft.isMissingNode() || draft.isNull()) ? text : draft.asText("");
        } catch (Exception e) {
            // Conservative fallback: return the original textual content
            if (body instanceof TextNode tn) return tn.textValue();
            return String.valueOf(body);
        }
    }
}
