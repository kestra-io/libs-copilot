package io.kestra.libs.copilot.models.in;

import jakarta.validation.constraints.NotBlank;

public record PluginMetadata<V extends Comparable<V>>(
    @NotBlank String type,
    String description,
    String kind,
    boolean deprecated,
    V version) {
    public static final String TASKS_GROUP_NAME = "tasks";
    public static final String TRIGGERS_GROUP_NAME = "triggers";
    public static final String CONDITIONS_GROUP_NAME = "conditions";
    public static final String STORAGES_GROUP_NAME = "storages";
    public static final String SECRETS_GROUP_NAME = "secrets";
    public static final String TASK_RUNNERS_GROUP_NAME = "task-runners";
    public static final String ASSETS_GROUP_NAME = "assets";
    public static final String ASSETS_EXPORTERS_GROUP_NAME = "asset-exporters";
    public static final String APPS_GROUP_NAME = "apps";
    public static final String APP_BLOCKS_GROUP_NAME = "app-blocks";
    public static final String CHARTS_GROUP_NAME = "charts";
    public static final String DATA_FILTERS_GROUP_NAME = "data-filters";
    public static final String DATA_FILTERS_KPI_GROUP_NAME = "data-filters-kpi";
    public static final String LOG_EXPORTERS_GROUP_NAME = "log-exporters";
    public static final String ADDITIONAL_PLUGINS_GROUP_NAME = "additional-plugins";
}
