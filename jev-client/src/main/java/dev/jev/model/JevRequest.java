package dev.jev.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Request payload for {@code POST /v1/systemone}.
 *
 * <pre>{@code
 * JevRequest request = JevRequest.builder()
 *     .state("Customer: first-time buyer | Tool: chargeCard | Amount: $5,000")
 *     .question("risk",         Question.score("Financial risk").level("Low").level("Medium").level("High"))
 *     .question("reversible",   Question.noul("This action can be undone if wrong"))
 *     .question("needs_human",  Question.noul("A human should review before executing"))
 *     .build();
 * }</pre>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class JevRequest {

    @JsonProperty("state")
    private final String state;

    @JsonProperty("model")
    private final String model;

    @JsonProperty("questions")
    private final Map<String, Question> questions;

    private JevRequest(Builder b) {
        this.state = b.state;
        this.model = b.model;
        this.questions = Collections.unmodifiableMap(new LinkedHashMap<>(b.questions));
    }

    public String getState()                        { return state; }
    public String getModel()                        { return model; }
    public Map<String, Question> getQuestions()     { return questions; }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String state;
        private String model = "jev-latest";
        private final Map<String, Question> questions = new LinkedHashMap<>();

        public Builder state(String state) {
            this.state = state;
            return this;
        }

        public Builder model(String model) {
            this.model = model;
            return this;
        }

        public Builder question(String name, Question question) {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("Question name must not be blank");
            }
            questions.put(name, question);
            return this;
        }

        public JevRequest build() {
            if (state == null || state.isBlank()) {
                throw new IllegalStateException("state must not be blank");
            }
            if (questions.isEmpty()) {
                throw new IllegalStateException("At least one question is required");
            }
            // Validate each question's internal constraints
            for (Map.Entry<String, Question> e : questions.entrySet()) {
                if (e.getValue() instanceof Question.Choice c) c.validate();
                if (e.getValue() instanceof Question.Score s) s.validate();
            }
            return new JevRequest(this);
        }
    }
}
