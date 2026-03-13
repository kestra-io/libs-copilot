package io.kestra.libs.copilot.services.ai;

import com.fasterxml.jackson.databind.JsonNode;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import io.kestra.libs.copilot.models.in.DashboardGenerationPrompt;
import io.kestra.libs.copilot.models.in.PluginMetadata;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DashboardAiCopilotTest extends LlmAiCopilotTest {
    private static class TestDashboardAiCopilot extends DashboardAiCopilot<Object> {
        private TestDashboardAiCopilot() {
            super(Object.class);
        }

        private List<String> publicExcludedPluginKinds() {
            return excludedPluginKinds();
        }
    }

    @Test
    void generateDashboardPassesDashboardSpecificArgumentsToBuilder() {
        PluginFinder pluginFinder = mock(PluginFinder.class);
        when(pluginFinder.findPlugins(anyString(), anyString())).thenReturn(List.of("io.kestra.plugin.core.dashboard.chart.Markdown"));

        DashboardYamlBuilder dashboardYamlBuilder = mock(DashboardYamlBuilder.class);
        when(dashboardYamlBuilder.buildDashboard(
            eq("{\"type\":\"object\"}"),
            eq(DashboardAiCopilot.BAD_REQUEST_ERROR),
            eq("Create a markdown dashboard")
        )).thenReturn("id: generated-dashboard");

        String yaml = new TestDashboardAiCopilot().generateDashboard(
            pluginFinder,
            dashboardYamlBuilder,
            (plugins) -> {
                assertThat(plugins).containsExactly("io.kestra.plugin.core.dashboard.chart.Markdown");
                return "{\"type\":\"object\"}";
            },
            List.of(new PluginMetadata<>("io.kestra.plugin.core.dashboard.chart.Markdown", "Render markdown", "charts", false, 1)),
            new DashboardGenerationPrompt("conversation-1", "Create a markdown dashboard", "id: old-dashboard")
        );

        assertThat(yaml).isEqualTo("id: generated-dashboard");
    }

    @Test
    void excludedPluginKindsMatchDashboardSpecificKinds() {
        assertThat(new TestDashboardAiCopilot().publicExcludedPluginKinds()).containsExactly(
            PluginMetadata.STORAGES_GROUP_NAME,
            PluginMetadata.SECRETS_GROUP_NAME,
            PluginMetadata.APPS_GROUP_NAME,
            PluginMetadata.APP_BLOCKS_GROUP_NAME,
            PluginMetadata.TASKS_GROUP_NAME,
            PluginMetadata.TRIGGERS_GROUP_NAME,
            PluginMetadata.CONDITIONS_GROUP_NAME,
            PluginMetadata.ASSETS_GROUP_NAME,
            PluginMetadata.ASSETS_EXPORTERS_GROUP_NAME,
            PluginMetadata.LOG_EXPORTERS_GROUP_NAME
        );
    }

    @Test
    @Tag("llm")
    void sanityCheckGenerateDashboardWithRealLlm() throws Exception {
        ChatModel model = realChatModel();

        PluginFinder pluginFinder = AiServices.create(PluginFinder.class, model);
        DashboardYamlBuilder dashboardYamlBuilder = AiServices.create(DashboardYamlBuilder.class, model);

        String yaml = new TestDashboardAiCopilot().generateDashboard(
            pluginFinder,
            dashboardYamlBuilder,
            (ignoredPlugins) -> TestSchemas.DASHBOARD_SCHEMA,
            List.of(new PluginMetadata<>("io.kestra.plugin.core.dashboard.chart.Markdown", "Add context and insights with Markdown.", "charts", false, 1)),
            new DashboardGenerationPrompt(
                "conversation-llm-dashboard",
                "Create the smallest valid Kestra dashboard possible with id llm-sanity-dashboard, title LLM Sanity Dashboard, and exactly one Markdown chart with a short heading and the sentence 'Hello world'.",
                null
            )
        );

        JsonNode parsedYaml = JacksonMapper.ofYaml().readTree(yaml);

        assertThat(parsedYaml.path("id").asText()).isEqualTo("llm-sanity-dashboard");
        assertThat(parsedYaml.path("title").asText()).isEqualTo("LLM Sanity Dashboard");
        assertThat(parsedYaml.path("charts").size()).isEqualTo(1);
        assertThat(parsedYaml.path("charts").get(0).path("type").asText()).isEqualTo("io.kestra.plugin.core.dashboard.chart.Markdown");
        assertThat(parsedYaml.path("charts").get(0).path("source").path("type").asText()).isEqualTo("Text");
        assertThat(parsedYaml.path("charts").get(0).path("source").path("content").asText()).containsIgnoringCase("hello");
    }
}
