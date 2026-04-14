package io.kestra.libs.copilot.services.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.kestra.libs.copilot.exceptions.AiException;
import io.kestra.libs.copilot.models.in.PluginMetadata;
import io.kestra.libs.copilot.utils.FunctionChecked;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AbstractAiCopilotTest {
    private static class TestAiCopilot extends AbstractAiCopilot<Object> {
        private static final String ALREADY_VALID_MESSAGE = "This entity already performs the requested action. Please provide additional instructions if you would like to request modifications.";
        private static final String NON_REQUEST_ERROR = "I can only assist with creating Kestra entities.";
        private static final String UNABLE_TO_GENERATE_ERROR = "The prompt did not provide enough information to generate a valid entity. Please clarify your request.";
        private static final List<String> POSSIBLE_ERROR_MESSAGES = List.of(ALREADY_VALID_MESSAGE, NON_REQUEST_ERROR, UNABLE_TO_GENERATE_ERROR);

        public TestAiCopilot(Class<Object> clazz) {
            super(clazz);
        }

        @Override
        protected String alreadyValidMessage() {
            return ALREADY_VALID_MESSAGE;
        }

        @Override
        protected String badRequestMessage() {
            return NON_REQUEST_ERROR;
        }

        @Override
        protected String unableToGenerateMessage() {
            return UNABLE_TO_GENERATE_ERROR;
        }

        @Override
        protected List<String> possibleErrorMessages() {
            return POSSIBLE_ERROR_MESSAGES;
        }

        @Override
        protected List<String> excludedPluginKinds() {
            return List.of();
        }

        private <VER extends Comparable<VER>> List<String> publicMostRelevantPlugins(PluginFinder pluginFinder, String userPrompt, List<PluginMetadata<VER>> plugins, List<String> excludedPluginTypes) {
            return mostRelevantPlugins(pluginFinder, userPrompt, plugins);
        }

        private <VER extends Comparable<VER>> String publicGenerateYaml(
            String originalYaml,
            String userPrompt,
            PluginFinder pluginFinder,
            List<PluginMetadata<VER>> availablePlugins,
            FunctionChecked<List<String>, String> jsonSchemaWithPluginsGenerator,
            YamlGenerator yamlGenerator
        ) {
            return super.generateYaml(
                originalYaml,
                userPrompt,
                pluginFinder,
                availablePlugins,
                jsonSchemaWithPluginsGenerator,
                yamlGenerator
            );
        }
    }

    @Test
    void mostRelevantPluginsReturnsResult() {
        TestAiCopilot copilot = new TestAiCopilot(Object.class);

        String actualPrompt = "my prompt";
        String olderVersionDescription = "Old plugin description";
        String newerVersionDescription = "New plugin description";
        String deprecatedPluginType = "io.kestra.libs.DeprecatedPlugin";
        assertThat(copilot.publicMostRelevantPlugins(
            (pluginsAsString, prompt) -> {
                assertThat(prompt).isEqualTo(actualPrompt);
                assertThat(pluginsAsString).doesNotContain(deprecatedPluginType);
                assertThat(pluginsAsString).doesNotContain(olderVersionDescription);
                assertThat(pluginsAsString).contains(newerVersionDescription);

                return List.of(
                    "io.kestra.libs.FakePlugin",
                    "io.kestra.libs.sub.package.SomePlugin"
                );
            },
            actualPrompt,
            List.of(
                new PluginMetadata<>("io.kestra.libs.FakePlugin", "A fake plugin", "tasks", false, 1),
                new PluginMetadata<>("io.kestra.libs.AnotherPlugin", olderVersionDescription, "tasks", false, 1),
                new PluginMetadata<>("io.kestra.libs.AnotherPlugin", newerVersionDescription, "tasks", false, 2),
                new PluginMetadata<>(deprecatedPluginType, "Deprecated plugin", "tasks", true, 2),
                new PluginMetadata<>("io.kestra.libs.sub.package.SomePlugin", "Some plugin", "tasks", false, 3)
            ),
            List.of()
        )).containsExactly("io.kestra.libs.FakePlugin", "io.kestra.libs.sub.package.SomePlugin");
    }

    @Test
    void mostRelevantPluginsEmptyThrows() {
        PluginFinder pluginFinder = mock(PluginFinder.class);
        when(pluginFinder.findPlugins(anyString(), eq("prompt"))).thenReturn(List.of());

        TestAiCopilot copilot = new TestAiCopilot(Object.class);

        assertThatThrownBy(() -> copilot.publicMostRelevantPlugins(pluginFinder, "prompt", List.of(), List.of()))
            .isInstanceOf(AiException.class)
            .hasMessage(TestAiCopilot.UNABLE_TO_GENERATE_ERROR);
    }

    @Test
    void minifySchemaRemovesDynamicsAndFalseDefault() throws JsonProcessingException {
        ObjectNode root = JacksonMapper.ofJson().createObjectNode();
        root.put("$dynamic", "toRemove");
        root.put("$group", "toRemove");
        root.put("default", false);

        ObjectNode props = JacksonMapper.ofJson().createObjectNode();
        ObjectNode child = JacksonMapper.ofJson().createObjectNode();
        child.put("default", false);
        props.set("child", child);
        root.set("properties", props);

        AbstractAiCopilot.minifySchema(root);

        assertThat(root.has("$dynamic")).isFalse();
        assertThat(root.has("$group")).isFalse();
        assertThat(root.has("default")).isFalse();
        assertThat(root.path("properties").path("child").has("default")).isFalse();
    }

    @Test
    void generateYamlHappyPathStripsCodeBlockMarkers() {
        TestAiCopilot copilot = new TestAiCopilot(Object.class);

        String yaml = copilot.publicGenerateYaml(
            """
                some: yaml""",
            "user prompt",
            pluginFinderMock(),
            Collections.emptyList(),
            (ignored) -> "{\"type\":\"object\",\"properties\":{\"a\":{\"default\":false}}}",
            (enhancedPrompt, schemaJson) -> "```yaml\na: 1\n```"
        );

        assertThat(yaml).isEqualTo("a: 1");
    }

    @Test
    void generateYamlReturnsPossibleErrorMessageThrows() {
        TestAiCopilot copilot = new TestAiCopilot(Object.class);

        assertThatThrownBy(() -> copilot.publicGenerateYaml(
            """
                some: yaml""",
            "user prompt",
            pluginFinderMock(),
            Collections.emptyList(),
            (ignored) -> "{\"type\":\"object\"}",
            (enhancedPrompt, schemaJson) -> TestAiCopilot.ALREADY_VALID_MESSAGE
        ))
            .isInstanceOf(AiException.class)
            .hasMessage(TestAiCopilot.ALREADY_VALID_MESSAGE);

        assertThat(copilot.publicGenerateYaml(
            """
                some: yaml""",
            "user prompt",
            pluginFinderMock(),
            Collections.emptyList(),
            (ignored) -> "{\"type\":\"object\"}",
            (enhancedPrompt, schemaJson) -> "Any message"
        )).isEqualTo("Any message");
    }

    @Test
    void generateYamlAlreadyValidThrows() {
        TestAiCopilot copilot = new TestAiCopilot(Object.class);

        assertThatThrownBy(() -> copilot.publicGenerateYaml(
            """
                my: value""",
            "user prompt",
            pluginFinderMock(),
            Collections.emptyList(),
            (ignored) -> "{\"type\":\"object\"}",
            (enhancedPrompt, schemaJson) -> "```yaml\nmy: value\n```"
        ))
            .isInstanceOf(AiException.class)
            .hasMessage(copilot.alreadyValidMessage());
    }

    private PluginFinder pluginFinderMock() {
        PluginFinder mock = mock(PluginFinder.class);
        Mockito.doReturn(List.of("placeholder.Task")).when(mock).findPlugins(anyString(), anyString());
        return mock;
    }
}
