package hr.emocio.recoresto.search.tool;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Every registered {@link LlmTool}, found by name (spec 009 FR-001). */
@Component
public class ToolRegistry {

    private final Map<String, LlmTool> tools = new LinkedHashMap<>();

    public ToolRegistry(List<LlmTool> registered) {
        for (LlmTool tool : registered) {
            if (tools.putIfAbsent(tool.name(), tool) != null) {
                throw new IllegalStateException("Two LLM tools are named " + tool.name());
            }
        }
    }

    /** The definitions of all the tools, in registration order, as sent to the model. */
    public List<Object> definitions() {
        return List.copyOf(tools.values().stream().<Object>map(LlmTool::definition).toList());
    }

    public Optional<LlmTool> find(String name) {
        return Optional.ofNullable(tools.get(name));
    }
}
