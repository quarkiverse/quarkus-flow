package org.acme.newsletter.domain;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Objects;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CriticAgentReview {

    /** "approve" | "revise" */
    @JsonProperty("verdict")
    private String verdict;

    /** Required non-empty array of reasons */
    @JsonProperty("reasons")
    private List<String> reasons;

    /** Optional suggestions */
    @JsonProperty("suggestions")
    private List<String> suggestions;

    /** Present & non-empty only when verdict == "revise" */
    @JsonProperty("revised_draft")
    @JsonAlias({"revisedDraft"})
    private String revisedDraft;

    @JsonProperty("original_draft")
    @JsonAlias({"originalDraft"})
    private String originalDraft;

    /** Optional scores block */
    @JsonProperty("scores")
    private Scores scores;

    public CriticAgentReview() {}

    public CriticAgentReview(String verdict, List<String> reasons, List<String> suggestions,
                             String revisedDraft, Scores scores, String originalDraft) {
        this.verdict = verdict;
        this.reasons = reasons;
        this.suggestions = suggestions;
        this.revisedDraft = revisedDraft;
        this.scores = scores;
        this.originalDraft = originalDraft;
    }

    public String getVerdict() { return verdict; }
    public void setVerdict(String verdict) { this.verdict = verdict; }

    public List<String> getReasons() { return reasons; }
    public void setReasons(List<String> reasons) { this.reasons = reasons; }

    public List<String> getSuggestions() { return suggestions; }
    public void setSuggestions(List<String> suggestions) { this.suggestions = suggestions; }

    public String getRevisedDraft() { return revisedDraft; }
    public void setRevisedDraft(String revisedDraft) { this.revisedDraft = revisedDraft; }

    public Scores getScores() { return scores; }
    public void setScores(Scores scores) { this.scores = scores; }

    public String getOriginalDraft() {
        return originalDraft;
    }
    public void setOriginalDraft(String originalDraft) { this.originalDraft = originalDraft; }

    @Override
    public String toString() {
        return "CriticAgentReview{" +
                "verdict='" + verdict + '\'' +
                ", reasons=" + reasons +
                ", suggestions=" + suggestions +
                ", revisedDraft=" + (revisedDraft == null ? "null" : "[...])") +
                ", scores=" + scores +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CriticAgentReview that)) return false;
        return Objects.equals(verdict, that.verdict) &&
                Objects.equals(reasons, that.reasons) &&
                Objects.equals(suggestions, that.suggestions) &&
                Objects.equals(revisedDraft, that.revisedDraft) &&
                Objects.equals(originalDraft, that.originalDraft) &&
                Objects.equals(scores, that.scores);
    }

    @Override
    public int hashCode() {
        return Objects.hash(verdict, reasons, suggestions, revisedDraft, originalDraft, scores);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Scores {
        @JsonProperty("clarity")     private Integer clarity;
        @JsonProperty("tone")        private Integer tone;
        @JsonProperty("compliance")  private Integer compliance;
        @JsonProperty("factuality")  private Integer factuality;
        @JsonProperty("overall")     private Integer overall;

        public Scores() {}

        public Scores(Integer clarity, Integer tone, Integer compliance,
                      Integer factuality, Integer overall) {
            this.clarity = clarity;
            this.tone = tone;
            this.compliance = compliance;
            this.factuality = factuality;
            this.overall = overall;
        }

        public Integer getClarity() { return clarity; }
        public void setClarity(Integer clarity) { this.clarity = clarity; }

        public Integer getTone() { return tone; }
        public void setTone(Integer tone) { this.tone = tone; }

        public Integer getCompliance() { return compliance; }
        public void setCompliance(Integer compliance) { this.compliance = compliance; }

        public Integer getFactuality() { return factuality; }
        public void setFactuality(Integer factuality) { this.factuality = factuality; }

        public Integer getOverall() { return overall; }
        public void setOverall(Integer overall) { this.overall = overall; }

        @Override
        public String toString() {
            return "Scores{" +
                    "clarity=" + clarity +
                    ", tone=" + tone +
                    ", compliance=" + compliance +
                    ", factuality=" + factuality +
                    ", overall=" + overall +
                    '}';
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Scores scores1)) return false;
            return Objects.equals(clarity, scores1.clarity) &&
                    Objects.equals(tone, scores1.tone) &&
                    Objects.equals(compliance, scores1.compliance) &&
                    Objects.equals(factuality, scores1.factuality) &&
                    Objects.equals(overall, scores1.overall);
        }

        @Override
        public int hashCode() {
            return Objects.hash(clarity, tone, compliance, factuality, overall);
        }
    }
}
