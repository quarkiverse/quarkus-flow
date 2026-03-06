package org.acme.newsletter.domain;

import java.util.List;

public record CriticReview(
        Verdict verdict,
        List<String> reasons,
        List<String> suggestions,
        Scores scores) {
    public enum Verdict {
        APPROVE,
        REVISE
    }

    public record Scores(
            Integer clarity,
            Integer tone,
            Integer compliance,
            Integer factuality,
            Integer overall) {
    }
}