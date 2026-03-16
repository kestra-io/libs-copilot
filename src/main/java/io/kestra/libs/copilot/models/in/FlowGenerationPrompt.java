package io.kestra.libs.copilot.models.in;

import com.fasterxml.jackson.annotation.JsonCreator;
import jakarta.validation.constraints.NotNull;

public class FlowGenerationPrompt {
    @NotNull
    private final String conversationId;
    @NotNull
    private final String userPrompt;
    private final String yaml;
    private final String namespace;

    @JsonCreator
    public FlowGenerationPrompt(String conversationId, String userPrompt, String yaml, String namespace) {
        this.conversationId = conversationId;
        this.userPrompt = userPrompt;
        this.yaml = yaml;
        this.namespace = namespace;
    }

    public String getConversationId() {
        return conversationId;
    }

    public String getUserPrompt() {
        return userPrompt;
    }

    public String getYaml() {
        return yaml;
    }

    public String getNamespace() {
        return namespace;
    }
}
