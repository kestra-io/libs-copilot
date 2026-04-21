package io.kestra.libs.copilot.services.ai;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import io.kestra.libs.copilot.models.in.FlowGenerationPrompt;
import io.kestra.libs.copilot.models.in.PluginMetadata;
import io.kestra.libs.copilot.utils.FunctionChecked;

import static io.kestra.libs.copilot.models.in.PluginMetadata.APPS_GROUP_NAME;
import static io.kestra.libs.copilot.models.in.PluginMetadata.APP_BLOCKS_GROUP_NAME;
import static io.kestra.libs.copilot.models.in.PluginMetadata.CHARTS_GROUP_NAME;
import static io.kestra.libs.copilot.models.in.PluginMetadata.DATA_FILTERS_GROUP_NAME;
import static io.kestra.libs.copilot.models.in.PluginMetadata.DATA_FILTERS_KPI_GROUP_NAME;
import static io.kestra.libs.copilot.models.in.PluginMetadata.SECRETS_GROUP_NAME;
import static io.kestra.libs.copilot.models.in.PluginMetadata.STORAGES_GROUP_NAME;

public class FlowAiCopilot<F> extends AbstractAiCopilot<F> {
    static final String ALREADY_VALID_MESSAGE = "This flow already performs the requested action. Please provide additional instructions if you would like to request modifications.";
    static final String BAD_REQUEST_ERROR = "I can only assist with creating Kestra flows.";
    static final String UNABLE_TO_GENERATE_ERROR = "The prompt did not provide enough information to generate a valid flow. Please clarify your request.";
    static final List<String> POSSIBLE_ERROR_MESSAGES = List.of(
        ALREADY_VALID_MESSAGE, BAD_REQUEST_ERROR,
        UNABLE_TO_GENERATE_ERROR
    );

    private static final List<String> EXCLUDED_PLUGIN_TYPES = List.of(
        STORAGES_GROUP_NAME, SECRETS_GROUP_NAME,
        APPS_GROUP_NAME, APP_BLOCKS_GROUP_NAME, CHARTS_GROUP_NAME, DATA_FILTERS_GROUP_NAME,
        DATA_FILTERS_KPI_GROUP_NAME
    );

    public FlowAiCopilot(Class<F> clazz) {
        super(clazz);
    }

    @Override
    protected String alreadyValidMessage() {
        return ALREADY_VALID_MESSAGE;
    }

    @Override
    protected String badRequestMessage() {
        return BAD_REQUEST_ERROR;
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
        return EXCLUDED_PLUGIN_TYPES;
    }

    public <V extends Comparable<V>> String generateFlow(PluginFinder pluginFinder, FlowYamlBuilder flowYamlBuilder,
        FunctionChecked<List<String>, String> jsonSchemaWithPluginsGenerator, List<PluginMetadata<V>> plugins,
        FlowGenerationPrompt flowGenerationPrompt, String tenantId,
        String pebbleExpressions) {
        // Langchain4j throws IllegalArgumentException when a @V template variable is null.
        String safePebbleExpressions = Objects.requireNonNullElse(pebbleExpressions, "");
        return generateYaml(
            flowGenerationPrompt.getYaml(), flowGenerationPrompt.getUserPrompt(), pluginFinder, plugins,
            jsonSchemaWithPluginsGenerator,
            (enhancedPrompt, schemaJson) -> flowYamlBuilder.buildFlow(
                schemaJson, badRequestMessage(),
                Optional.ofNullable(flowGenerationPrompt.getYaml()).orElse(""),
                flowGenerationPrompt.getNamespace(), tenantId,
                safePebbleExpressions, enhancedPrompt
            )
        );
    }
}
