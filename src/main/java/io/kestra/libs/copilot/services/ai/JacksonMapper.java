package io.kestra.libs.copilot.services.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

final class JacksonMapper {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final ObjectMapper YAML = new YAMLMapper();

    private JacksonMapper() {
    }

    static ObjectMapper ofJson() {
        return JSON;
    }

    static ObjectMapper ofYaml() {
        return YAML;
    }
}
