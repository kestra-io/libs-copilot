package io.kestra.libs.copilot.services.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.kestra.libs.copilot.exceptions.AiException;
import io.kestra.libs.copilot.models.in.PluginMetadata;
import io.kestra.libs.copilot.utils.FunctionChecked;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public abstract class AbstractAiCopilot<T> {
    protected Class<T> clazz;

    public AbstractAiCopilot(Class<T> clazz) {
        this.clazz = clazz;
    }

    protected <V extends Comparable<V>> List<String> mostRelevantPlugins(PluginFinder pluginFinder, String userPrompt,
        List<PluginMetadata<V>> plugins) {
        Map<String, String> descriptionByType = plugins.stream()
            .sorted(Comparator.<PluginMetadata<V>, V>comparing(PluginMetadata::version).reversed())
            .filter(plugin -> !excludedPluginKinds().contains(plugin.kind()))
            .filter(plugin -> !plugin.deprecated())
            .collect(Collectors.toMap(PluginMetadata::type,
                plugin -> plugin.description() == null ? "" : plugin.description(),
                (existing, ignored) -> existing));

        String serializedPlugins;
        try {
            serializedPlugins = JacksonMapper.ofJson().writeValueAsString(descriptionByType.entrySet().stream()
                .map(entry -> Map.of("type", entry.getKey(), "description", entry.getValue())).toList());
        } catch (JsonProcessingException e) {
            serializedPlugins = "[]";
        }

        List<String> mostRelevantPlugins = pluginFinder.findPlugins(serializedPlugins, userPrompt);
        if (mostRelevantPlugins.isEmpty()) {
            throw new AiException(unableToGenerateMessage());
        }

        return mostRelevantPlugins;
    }

    public static String minifySchema(String schema) throws JsonProcessingException {
        return JacksonMapper.ofJson().writeValueAsString(minifySchema(JacksonMapper.ofJson().readTree(schema)));
    }

    public static JsonNode minifySchema(JsonNode node) throws JsonProcessingException {
        if (node == null) {
            return null;
        }

        if (node.isObject()) {
            ObjectNode obj = (ObjectNode) node;
            obj.remove("$dynamic");
            obj.remove("$group");
            if (obj.optional("default").map(defaultNode -> defaultNode.isBoolean() && !defaultNode.asBoolean())
                .orElse(false)) {
                obj.remove("default");
            }
            obj.properties().forEach(entry -> {
                try {
                    minifySchema(entry.getValue());
                } catch (JsonProcessingException e) {
                    throw new RuntimeException(e);
                }
            });
        }

        if (node.isArray()) {
            ArrayNode arr = (ArrayNode) node;
            for (JsonNode item : arr) {
                minifySchema(item);
            }
        }

        return node;
    }

    @FunctionalInterface
    protected interface YamlGenerator {
        String generate(String enhancedPrompt, String schemaJson);
    }

    protected <V extends Comparable<V>> String generateYaml(String originalYaml, String userPrompt,
        PluginFinder pluginFinder, List<PluginMetadata<V>> availablePlugins,
        FunctionChecked<List<String>, String> jsonSchemaWithPluginsGenerator,
        YamlGenerator yamlGenerator) {
        String enhancedPrompt = String.format(
            "Current " + clazz.getSimpleName() + " YAML:\n```yaml\n%s\n```\n\nUser's prompt:\n```\n%s\n```",
            Optional.ofNullable(originalYaml).orElse(""), userPrompt);
        String minifiedSchemaForPlugins;
        try {
            minifiedSchemaForPlugins = minifySchema(jsonSchemaWithPluginsGenerator
                .apply(mostRelevantPlugins(pluginFinder, enhancedPrompt, availablePlugins)));
        } catch (Exception e) {
            throw new AiException(this.badRequestMessage());
        }
        String yaml = yamlGenerator.generate(enhancedPrompt, minifiedSchemaForPlugins);
        if (possibleErrorMessages() != null && possibleErrorMessages().contains(yaml)) {
            throw new AiException(yaml);
        }

        yaml = yaml.replaceAll("\\s?```(?:yaml)?\\s?", "");

        if (originalYaml != null && yaml.equals(originalYaml)) {
            throw new AiException(alreadyValidMessage());
        }

        return yaml;
    }

    protected abstract String alreadyValidMessage();

    protected abstract String badRequestMessage();

    protected abstract String unableToGenerateMessage();

    protected abstract List<String> possibleErrorMessages();

    protected abstract List<String> excludedPluginKinds();
}
