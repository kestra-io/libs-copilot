package io.kestra.libs.copilot.services.ai;

import java.time.Duration;
import java.util.Optional;

import org.junit.jupiter.api.Assumptions;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;

public class LlmAiCopilotTest {
    private static final String GEMINI_API_KEY = "GEMINI_API_KEY";
    private static final String GEMINI_MODEL = "GEMINI_MODEL";

    protected static ChatModel realChatModel() {
        String apiKey = System.getenv(GEMINI_API_KEY);
        String modelName = Optional.ofNullable(System.getenv(GEMINI_MODEL)).orElse("gemini-2.5-flash");

        Assumptions.assumeTrue(apiKey != null && !apiKey.isBlank(), GEMINI_API_KEY + " must be set");

        return GoogleAiGeminiChatModel.builder()
            .apiKey(apiKey)
            .modelName(modelName)
            .temperature(0.7)
            .timeout(Duration.ofSeconds(90))
            .build();
    }
}
