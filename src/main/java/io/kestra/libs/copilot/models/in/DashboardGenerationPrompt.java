package io.kestra.libs.copilot.models.in;

import com.fasterxml.jackson.annotation.JsonCreator;
import jakarta.validation.constraints.NotNull;

public class DashboardGenerationPrompt {
    @NotNull
    private final String conversationId;
    @NotNull
    private final String userPrompt;
    private final String yaml;

    @JsonCreator
    public DashboardGenerationPrompt(String conversationId, String userPrompt, String yaml) {
        this.conversationId = conversationId;
        this.userPrompt = userPrompt;
        this.yaml = yaml;
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
}
