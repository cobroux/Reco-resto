package hr.emocio.recoresto.search.tool;

import java.util.Map;

/**
 * A tool the language model may call during a search (spec 009 FR-001): defined, limited and executed in one place.
 * Registering a new tool is adding one bean that implements this interface.
 */
public interface LlmTool {

    /** The name the model calls; unique among the registered tools. */
    String name();

    /** The OpenAI-style entry {@code {"type":"function","function":{name, description, parameters}}} sent to the model. */
    Map<String, Object> definition();

    /** How many times the model may call this tool for one sentence. */
    int callLimit();

    /**
     * Runs the tool with the arguments the model gave (never trusted: the tool checks them) and returns the text sent
     * back to the model as the tool message.
     */
    String execute(String argumentsJson, ToolContext context);
}
