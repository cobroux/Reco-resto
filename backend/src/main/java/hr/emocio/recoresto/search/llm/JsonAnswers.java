package hr.emocio.recoresto.search.llm;

import java.util.Optional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reading of the language model answers (spec 008 D12, FR-004; spec 007 research R7): the single place that finds the
 * JSON object in an answer, whatever text or code fence surrounds it.
 */
public final class JsonAnswers {

    private JsonAnswers() {
    }

    /**
     * The first balanced {@code {...}} block of the text that reads as a JSON object; braces inside strings are not
     * structure. A block that does not read as JSON is skipped. Code fences are text around the object.
     */
    public static Optional<JsonNode> firstObject(JsonMapper mapper, String reply) {
        if (reply == null) {
            return Optional.empty();
        }
        int depth = 0;
        int start = -1;
        boolean inString = false;
        boolean escaped = false;
        for (int i = 0; i < reply.length(); i++) {
            char c = reply.charAt(i);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    inString = false;
                }
            } else if (c == '"') {
                inString = depth > 0;
            } else if (c == '{') {
                if (depth++ == 0) {
                    start = i;
                }
            } else if (c == '}' && depth > 0 && --depth == 0) {
                Optional<JsonNode> object = readObject(mapper, reply.substring(start, i + 1));
                if (object.isPresent()) {
                    return object;
                }
            }
        }
        return Optional.empty();
    }

    /** The trimmed text of a JSON string node; null when the node is missing, not a string, or blank. */
    public static String text(JsonNode node) {
        return node.isString() && !node.asString().isBlank() ? node.asString().trim() : null;
    }

    private static Optional<JsonNode> readObject(JsonMapper mapper, String text) {
        try {
            JsonNode node = mapper.readTree(text);
            return node != null && node.isObject() ? Optional.of(node) : Optional.empty();
        } catch (JacksonException e) {
            return Optional.empty();
        }
    }
}
