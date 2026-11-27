package hr.emocio.recoresto.search.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Spec 009 FR-001: the tools of the model are found by name in one place. */
class ToolRegistryTest {

    private static LlmTool tool(String name) {
        return new LlmTool() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public Map<String, Object> definition() {
                return Map.of("type", "function", "function", Map.of("name", name));
            }

            @Override
            public int callLimit() {
                return 1;
            }

            @Override
            public String execute(String argumentsJson, ToolContext context) {
                return "{}";
            }
        };
    }

    @Test
    void aToolIsFoundByItsName() {
        LlmTool first = tool("first");
        ToolRegistry registry = new ToolRegistry(List.of(first, tool("second")));

        assertThat(registry.find("first")).containsSame(first);
    }

    @Test
    void anUnknownNameGivesNoTool() {
        assertThat(new ToolRegistry(List.of(tool("first"))).find("other")).isEmpty();
    }

    @Test
    void twoToolsWithTheSameNameAreRefusedAtStartup() {
        assertThatThrownBy(() -> new ToolRegistry(List.of(tool("same"), tool("same"))))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("same");
    }

    @Test
    void theDefinitionsListEveryToolInOrder() {
        ToolRegistry registry = new ToolRegistry(List.of(tool("first"), tool("second")));

        assertThat(registry.definitions()).containsExactly(tool("first").definition(), tool("second").definition());
    }
}
