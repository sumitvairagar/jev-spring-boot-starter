package dev.jev.model;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Deserializes a Jev {@code /v1/systemone} response, converting each answer
 * into the correct sealed subtype based on its {@code "type"} field.
 */
public final class JevResponseDeserializer extends JsonDeserializer<JevResponse> {

    @Override
    public JevResponse deserialize(JsonParser p, DeserializationContext ctx) throws IOException {
        ObjectMapper mapper = (ObjectMapper) p.getCodec();
        JsonNode root = mapper.readTree(p);

        JevResponse response = new JevResponse();
        JsonNode answersNode = root.path("answers");

        if (!answersNode.isMissingNode() && answersNode.isObject()) {
            Map<String, JevResponse.Answer> answers = new LinkedHashMap<>();
            answersNode.fields().forEachRemaining(entry -> {
                String name = entry.getKey();
                JsonNode answerNode = entry.getValue();
                String type = answerNode.path("type").asText("unknown");
                try {
                    JevResponse.Answer answer = switch (type) {
                        case "noul"   -> mapper.treeToValue(answerNode, JevResponse.NoulAnswer.class);
                        case "score"  -> mapper.treeToValue(answerNode, JevResponse.ScoreAnswer.class);
                        case "choice" -> mapper.treeToValue(answerNode, JevResponse.ChoiceAnswer.class);
                        default -> {
                            // Forward-compatible: unknown future types degrade gracefully
                            JevResponse.NoulAnswer unknown = new JevResponse.NoulAnswer();
                            unknown.type = type;
                            yield unknown;
                        }
                    };
                    answers.put(name, answer);
                } catch (Exception e) {
                    // Skip undeserializable answers rather than failing the whole response
                }
            });
            response.getAnswers().putAll(answers);
        }

        return response;
    }
}
