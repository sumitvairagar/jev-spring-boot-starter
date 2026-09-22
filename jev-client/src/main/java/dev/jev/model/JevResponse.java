package dev.jev.model;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Response from {@code POST /v1/systemone}.
 *
 * <p>Access answers by name with typed convenience methods:
 * <pre>{@code
 * OptionalDouble riskScore = response.score("risk");          // 0.0 = Low, 1.0 = Medium, 2.0 = High
 * OptionalDouble urgency   = response.noul("is_urgent");      // 0.0–1.0 probability
 * Optional<String> dept    = response.choice("department");   // "billing", "technical", etc.
 * }</pre>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class JevResponse {

    @JsonProperty("answers")
    private Map<String, Answer> answers = new LinkedHashMap<>();

    /** Noul (true/false probability) answer for the named question. */
    public OptionalDouble noul(String name) {
        Answer a = answers.get(name);
        if (a instanceof NoulAnswer na) return OptionalDouble.of(na.noul);
        return OptionalDouble.empty();
    }

    /** Score answer for the named question (index into the levels array). */
    public OptionalDouble score(String name) {
        Answer a = answers.get(name);
        if (a instanceof ScoreAnswer sa) return OptionalDouble.of(sa.score);
        return OptionalDouble.empty();
    }

    /** Choice answer for the named question. */
    public Optional<String> choice(String name) {
        Answer a = answers.get(name);
        if (a instanceof ChoiceAnswer ca) return Optional.ofNullable(ca.choice);
        return Optional.empty();
    }

    /** Confidence for any answer type (0.0–1.0). Empty if unavailable. */
    public OptionalDouble confidence(String name) {
        Answer a = answers.get(name);
        if (a == null) return OptionalDouble.empty();
        if (a instanceof ScoreAnswer sa && sa.confidence != null)  return OptionalDouble.of(sa.confidence);
        if (a instanceof ChoiceAnswer ca && ca.confidence != null) return OptionalDouble.of(ca.confidence);
        return OptionalDouble.empty();
    }

    /** Raw answer map — use for inspection or custom access. */
    public Map<String, Answer> getAnswers() { return answers; }

    // -----------------------------------------------------------------------
    // Answer types
    // -----------------------------------------------------------------------

    public sealed interface Answer permits NoulAnswer, ScoreAnswer, ChoiceAnswer {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class NoulAnswer implements Answer {
        @JsonProperty("noul")   public double noul;
        @JsonProperty("type")   public String type;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class ScoreAnswer implements Answer {
        @JsonProperty("score")      public double score;
        @JsonProperty("confidence") public Double confidence;
        @JsonProperty("type")       public String type;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class ChoiceAnswer implements Answer {
        @JsonProperty("choice")         public String choice;
        @JsonProperty("confidence")     public Double confidence;
        @JsonProperty("probabilities")  public Map<String, Double> probabilities;
        @JsonProperty("type")           public String type;
    }

    // -----------------------------------------------------------------------
    // Jackson answer deserializer — type-dispatch on the "type" field
    // -----------------------------------------------------------------------

    /**
     * Intermediate holder used during deserialization.
     * Jackson populates a raw map first; we convert in {@link JevResponseDeserializer}.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    static final class RawAnswer {
        @JsonProperty("type")           String type;
        @JsonProperty("noul")           Double noul;
        @JsonProperty("score")          Double score;
        @JsonProperty("confidence")     Double confidence;
        @JsonProperty("choice")         String choice;
        @JsonProperty("probabilities")  Map<String, Double> probabilities;
    }
}
