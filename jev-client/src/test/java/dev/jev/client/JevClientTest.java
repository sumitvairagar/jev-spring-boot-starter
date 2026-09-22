package dev.jev.client;

import dev.jev.model.JevRequest;
import dev.jev.model.JevResponse;
import dev.jev.model.Question;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class JevClientTest {

    // -----------------------------------------------------------------------
    // Builder validation
    // -----------------------------------------------------------------------

    @Test
    void builder_rejects_blank_api_key() {
        assertThatThrownBy(() -> JevClient.builder().build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("api-key");
    }

    @Test
    void builder_accepts_valid_key() {
        JevClient client = JevClient.builder().apiKey("test-key").build();
        assertThat(client).isNotNull();
    }

    // -----------------------------------------------------------------------
    // JevRequest builder validation
    // -----------------------------------------------------------------------

    @Test
    void request_rejects_blank_state() {
        assertThatThrownBy(() ->
                JevRequest.builder()
                        .question("q", Question.noul("Is this urgent?"))
                        .build()
        ).isInstanceOf(IllegalStateException.class).hasMessageContaining("state");
    }

    @Test
    void request_rejects_empty_questions() {
        assertThatThrownBy(() ->
                JevRequest.builder().state("some state").build()
        ).isInstanceOf(IllegalStateException.class).hasMessageContaining("question");
    }

    @Test
    void request_rejects_choice_with_fewer_than_two_options() {
        assertThatThrownBy(() ->
                JevRequest.builder()
                        .state("some state")
                        .question("dept", Question.choice("Which dept?").option("only", "only one"))
                        .build()
        ).isInstanceOf(IllegalStateException.class).hasMessageContaining("2 options");
    }

    @Test
    void request_rejects_score_with_fewer_than_two_levels() {
        assertThatThrownBy(() ->
                JevRequest.builder()
                        .state("some state")
                        .question("risk", Question.score("Risk level").level("Low"))
                        .build()
        ).isInstanceOf(IllegalStateException.class).hasMessageContaining("2 levels");
    }

    @Test
    void valid_request_builds_without_error() {
        JevRequest request = JevRequest.builder()
                .state("Customer sent an angry refund request")
                .question("urgent",      Question.noul("Is this urgent?"))
                .question("frustration", Question.score("How frustrated?").level("Calm").level("Frustrated").level("Angry"))
                .question("dept",        Question.choice("Which team?").option("billing", "Payments").option("tech", "Bugs"))
                .build();
        assertThat(request.getState()).contains("refund");
        assertThat(request.getQuestions()).hasSize(3);
        assertThat(request.getModel()).isEqualTo("jev-latest");
    }

    // -----------------------------------------------------------------------
    // JevResponse parsing
    // -----------------------------------------------------------------------

    @Test
    void response_parses_noul_answer() throws Exception {
        String json = """
            {
              "answers": {
                "urgent": { "type": "noul", "noul": 0.92 }
              }
            }
            """;
        JevResponse response = parseResponse(json);
        assertThat(response.noul("urgent")).isPresent().hasValue(0.92);
        assertThat(response.noul("missing")).isEmpty();
    }

    @Test
    void response_parses_score_answer() throws Exception {
        String json = """
            {
              "answers": {
                "frustration": { "type": "score", "score": 2.0, "confidence": 0.88 }
              }
            }
            """;
        JevResponse response = parseResponse(json);
        assertThat(response.score("frustration")).isPresent().hasValue(2.0);
        assertThat(response.confidence("frustration")).isPresent().hasValue(0.88);
    }

    @Test
    void response_parses_choice_answer() throws Exception {
        String json = """
            {
              "answers": {
                "dept": {
                  "type": "choice",
                  "choice": "billing",
                  "confidence": 0.94,
                  "probabilities": { "billing": 0.94, "tech": 0.06 }
                }
              }
            }
            """;
        JevResponse response = parseResponse(json);
        assertThat(response.choice("dept")).isPresent().hasValue("billing");
        assertThat(response.confidence("dept")).isPresent().hasValue(0.94);
    }

    @Test
    void response_handles_unknown_answer_type_gracefully() throws Exception {
        String json = """
            {
              "answers": {
                "future_type": { "type": "ranking", "ranking": [1, 2, 3] }
              }
            }
            """;
        // Should not throw
        JevResponse response = parseResponse(json);
        assertThat(response.getAnswers()).containsKey("future_type");
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private JevResponse parseResponse(String json) throws Exception {
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        com.fasterxml.jackson.databind.module.SimpleModule module = new com.fasterxml.jackson.databind.module.SimpleModule();
        module.addDeserializer(JevResponse.class, new dev.jev.model.JevResponseDeserializer());
        mapper.registerModule(module);
        return mapper.readValue(json, JevResponse.class);
    }
}
