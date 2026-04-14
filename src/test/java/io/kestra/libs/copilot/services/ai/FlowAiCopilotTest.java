package io.kestra.libs.copilot.services.ai;

import com.fasterxml.jackson.databind.JsonNode;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import io.kestra.libs.copilot.models.in.FlowGenerationPrompt;
import io.kestra.libs.copilot.models.in.PluginMetadata;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FlowAiCopilotTest extends LlmAiCopilotTest {
    private static class TestFlowAiCopilot extends FlowAiCopilot<Object> {
        private TestFlowAiCopilot() {
            super(Object.class);
        }

        private List<String> publicExcludedPluginKinds() {
            return excludedPluginKinds();
        }
    }

    @Test
    void generateFlowPassesFlowSpecificArgumentsToBuilder() {
        PluginFinder pluginFinder = mock(PluginFinder.class);
        when(pluginFinder.findPlugins(anyString(), anyString())).thenReturn(List.of("io.kestra.plugin.core.log.Log"));

        FlowYamlBuilder flowYamlBuilder = mock(FlowYamlBuilder.class);
        String initialFlow = "existing: flow";
        when(flowYamlBuilder.buildFlow(
            eq("{\"type\":\"object\"}"),
            eq(FlowAiCopilot.BAD_REQUEST_ERROR),
            eq(initialFlow),
            eq("company.team"),
            eq("tenant-1"),
            eq(String.format(
                "Current Object YAML:\n```yaml\n%s\n```\n\nUser's prompt:\n```\n%s\n```",
                initialFlow, "Add a log task")))).thenReturn("id: generated");

        String yaml = new TestFlowAiCopilot().generateFlow(
            pluginFinder,
            flowYamlBuilder,
            (ignoredPlugins) -> {
                assertThat(ignoredPlugins).containsExactly("io.kestra.plugin.core.log.Log");
                return "{\"type\":\"object\"}";
            },
            List.of(new PluginMetadata<>("io.kestra.plugin.core.log.Log", "Emit log entries", "tasks", false, 1)),
            new FlowGenerationPrompt("conversation-1", "Add a log task", initialFlow, "company.team"),
            "tenant-1");

        assertThat(yaml).isEqualTo("id: generated");
    }

    @Test
    void excludedPluginKindsMatchFlowSpecificKinds() {
        assertThat(new TestFlowAiCopilot().publicExcludedPluginKinds()).containsExactly(
            PluginMetadata.STORAGES_GROUP_NAME,
            PluginMetadata.SECRETS_GROUP_NAME,
            PluginMetadata.APPS_GROUP_NAME,
            PluginMetadata.APP_BLOCKS_GROUP_NAME,
            PluginMetadata.CHARTS_GROUP_NAME,
            PluginMetadata.DATA_FILTERS_GROUP_NAME,
            PluginMetadata.DATA_FILTERS_KPI_GROUP_NAME);
    }

    @Test @Tag("llm")
    void sanityCheckGenerateFlowWithRealLlm() throws Exception {
        ChatModel model = realChatModel();

        PluginFinder pluginFinder = AiServices.create(PluginFinder.class, model);
        FlowYamlBuilder flowYamlBuilder = AiServices.create(FlowYamlBuilder.class, model);

        String yaml = new TestFlowAiCopilot().generateFlow(
            pluginFinder,
            flowYamlBuilder,
            (ignoredPlugins) -> TestSchemas.FLOW_SCHEMA,
            List.of(new PluginMetadata<>("io.kestra.plugin.core.log.Log", "Emit log entries from a flow.", "tasks", false, 1)),
            new FlowGenerationPrompt(
                "conversation-llm-flow",
                "Create the smallest valid Kestra flow possible with id llm_sanity_flow, namespace company.team, and exactly one Log task that logs Hello from Copilot test.",
                null,
                "company.team"),
            "my_tenant");

        JsonNode parsedYaml = JacksonMapper.ofYaml().readTree(yaml);

        assertThat(parsedYaml.path("id").asText()).isEqualTo("llm_sanity_flow");
        assertThat(parsedYaml.path("namespace").asText()).isEqualTo("company.team");
        assertThat(parsedYaml.path("tasks").size()).isEqualTo(1);
        assertThat(parsedYaml.path("tasks").get(0).path("type").asText()).isEqualTo("io.kestra.plugin.core.log.Log");
        assertThat(parsedYaml.path("tasks").get(0).path("message").asText()).containsIgnoringCase("hello");
    }
}
