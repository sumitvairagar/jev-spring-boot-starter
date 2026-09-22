package dev.jev.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A typed question sent to Jev alongside a piece of state.
 *
 * <p>Three concrete types exist — create them with the static factories:
 * <pre>{@code
 * Question.noul("Is this message urgent?")
 * Question.choice("Which team?").option("billing", "Payment issues").option("technical", "Bugs")
 * Question.score("How frustrated?").level("Calm").level("Frustrated").level("Very angry")
 * }</pre>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public sealed interface Question permits Question.Noul, Question.Choice, Question.Score {

    /** True/false probability question. Returns a probability from 0.0 to 1.0. */
    static Noul noul(String instructions) {
        return new Noul(instructions);
    }

    /** Pick-one-from-a-set question. Returns the chosen option with per-option probabilities. */
    static Choice choice(String instructions) {
        return new Choice(instructions);
    }

    /** Rate-on-an-ordered-scale question. Returns a weighted score value. */
    static Score score(String instructions) {
        return new Score(instructions);
    }

    // -----------------------------------------------------------------------

    final class Noul implements Question {
        @JsonProperty("type")
        public final String type = "noul";

        @JsonProperty("instructions")
        public final String instructions;

        private Noul(String instructions) {
            if (instructions == null || instructions.isBlank()) {
                throw new IllegalArgumentException("Noul instructions must not be blank");
            }
            this.instructions = instructions;
        }
    }

    final class Choice implements Question {
        @JsonProperty("type")
        public final String type = "choice";

        @JsonProperty("instructions")
        public final String instructions;

        @JsonProperty("criteria")
        public final Map<String, String> criteria = new LinkedHashMap<>();

        private Choice(String instructions) {
            if (instructions == null || instructions.isBlank()) {
                throw new IllegalArgumentException("Choice instructions must not be blank");
            }
            this.instructions = instructions;
        }

        /**
         * Add a named option with a description.
         *
         * @param key         machine-readable key returned in the answer (e.g. "billing")
         * @param description human-readable label (e.g. "Payment and subscription issues")
         */
        public Choice option(String key, String description) {
            if (key == null || key.isBlank()) throw new IllegalArgumentException("Option key must not be blank");
            criteria.put(key, description);
            return this;
        }

        public void validate() {
            if (criteria.size() < 2) {
                throw new IllegalStateException("Choice question requires at least 2 options, got " + criteria.size());
            }
        }
    }

    final class Score implements Question {
        @JsonProperty("type")
        public final String type = "score";

        @JsonProperty("instructions")
        public final String instructions;

        @JsonProperty("criteria")
        public final List<String> criteria = new ArrayList<>();

        private Score(String instructions) {
            if (instructions == null || instructions.isBlank()) {
                throw new IllegalArgumentException("Score instructions must not be blank");
            }
            this.instructions = instructions;
        }

        /**
         * Add an ordered level (lowest to highest).
         *
         * @param label e.g. "Calm", "Frustrated", "Very angry"
         */
        public Score level(String label) {
            if (label == null || label.isBlank()) throw new IllegalArgumentException("Level label must not be blank");
            criteria.add(label);
            return this;
        }

        public void validate() {
            if (criteria.size() < 2) {
                throw new IllegalStateException("Score question requires at least 2 levels, got " + criteria.size());
            }
        }
    }
}
