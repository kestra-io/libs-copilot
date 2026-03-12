package io.kestra.libs.copilot.services.ai;

import io.kestra.libs.copilot.models.in.DashboardGenerationPrompt;
import io.kestra.libs.copilot.models.in.PluginMetadata;
import io.kestra.libs.copilot.utils.FunctionChecked;

import java.util.List;

import static io.kestra.libs.copilot.models.in.PluginMetadata.*;

public class DashboardAiCopilot<D> extends AbstractAiCopilot<D> {
    static final String ALREADY_VALID_MESSAGE = "This dashboard already performs the requested action. Please provide additional instructions if you would like to request modifications.";
    static final String BAD_REQUEST_ERROR = "I can only assist with creating Kestra dashboards.";
    static final String UNABLE_TO_GENERATE_ERROR = "The prompt did not provide enough information to generate a valid dashboard. Please clarify your request.";
    static final List<String> POSSIBLE_ERROR_MESSAGES = List.of(ALREADY_VALID_MESSAGE, BAD_REQUEST_ERROR, UNABLE_TO_GENERATE_ERROR);

    private static final List<String> EXCLUDED_PLUGIN_TYPES = List.of(
        STORAGES_GROUP_NAME,
        SECRETS_GROUP_NAME,
        APPS_GROUP_NAME,
        APP_BLOCKS_GROUP_NAME,
        TASKS_GROUP_NAME,
        TRIGGERS_GROUP_NAME,
        CONDITIONS_GROUP_NAME,
        ASSETS_GROUP_NAME,
        ASSETS_EXPORTERS_GROUP_NAME,
        LOG_EXPORTERS_GROUP_NAME
    );

    public DashboardAiCopilot(Class<D> clazz) {
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

    public <V extends Comparable<V>> String generateDashboard(
        PluginFinder pluginFinder,
        DashboardYamlBuilder dashboardYamlBuilder,
        FunctionChecked<List<String>, String> jsonSchemaWithPluginsGenerator,
        List<PluginMetadata<V>> plugins,
        DashboardGenerationPrompt dashboardGenerationPrompt
    ) {
        return generateYaml(
            dashboardGenerationPrompt.yaml(),
            dashboardGenerationPrompt.userPrompt(),
            pluginFinder,
            plugins,
            jsonSchemaWithPluginsGenerator,
            (schema) -> dashboardYamlBuilder.buildDashboard(schema, badRequestMessage(), dashboardGenerationPrompt.userPrompt())
        );
    }
}
