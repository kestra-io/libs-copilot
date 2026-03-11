package io.kestra.libs.copilot.models.in;

import jakarta.validation.constraints.NotNull;

public record DashboardGenerationPrompt(@NotNull String conversationId, @NotNull String userPrompt, String yaml, String providerId) {
}

