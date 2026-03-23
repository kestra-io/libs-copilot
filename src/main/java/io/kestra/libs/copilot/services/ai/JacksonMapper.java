package io.kestra.libs.copilot.services.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

public final class JacksonMapper {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final ObjectMapper YAML = new YAMLMapper();

    private JacksonMapper() {
    }

    public static ObjectMapper ofJson() {
        return JSON;
    }

    public static ObjectMapper ofYaml() {
        return YAML;
    }
}
