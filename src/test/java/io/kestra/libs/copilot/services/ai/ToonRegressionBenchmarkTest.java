package io.kestra.libs.copilot.services.ai;

import com.fasterxml.jackson.databind.JsonNode;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import io.kestra.libs.copilot.services.ai.AbstractAiCopilot.SchemaFormat;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A/B/C benchmark comparing flow generation quality across three schema formats:
 * JSON (compact), TOON (indentation-based), and TypeScript (type definitions).
 *
 * Run token comparison only (no API key needed):
 *   ./gradlew test --tests "*.ToonRegressionBenchmarkTest.schemaTokenComparison"
 *
 * Run full LLM benchmark:
 *   GEMINI_API_KEY=<key> ./gradlew test --tests "*.ToonRegressionBenchmarkTest.benchmarkAllFormats" -Dinclude.tags=benchmark
 */
class ToonRegressionBenchmarkTest extends LlmAiCopilotTest {

    private static final int ITERATIONS = 3;
    private static final List<String> FORMATS = List.of("JSON", "TOON", "TS");

    private record Prompt(String name, String text, List<Check> checks) {}

    private record Check(String path, CheckType type, Object expected) {}

    private enum CheckType { EQUALS, NOT_NULL, SIZE_GTE, CONTAINS_IGNORING_CASE }

    private static final List<Prompt> PROMPTS = List.of(
        new Prompt(
            "simple-log",
            "Create a flow with id benchmark_simple, namespace company.team, and exactly one Log task that logs Hello benchmark.",
            List.of(
                new Check("id", CheckType.EQUALS, "benchmark_simple"),
                new Check("namespace", CheckType.EQUALS, "company.team"),
                new Check("tasks", CheckType.SIZE_GTE, 1)
            )
        ),
        new Prompt(
            "two-tasks",
            "Create a flow with id benchmark_two_tasks, namespace company.team. "
                + "First task: an HTTP Request to https://api.example.com/data. "
                + "Second task: a Log task that logs the HTTP response.",
            List.of(
                new Check("id", CheckType.EQUALS, "benchmark_two_tasks"),
                new Check("namespace", CheckType.EQUALS, "company.team"),
                new Check("tasks", CheckType.SIZE_GTE, 2)
            )
        ),
        new Prompt(
            "with-inputs",
            "Create a flow with id benchmark_inputs, namespace company.team, "
                + "with a string input called 'name' and a Log task that logs Hello followed by the input value.",
            List.of(
                new Check("id", CheckType.EQUALS, "benchmark_inputs"),
                new Check("namespace", CheckType.EQUALS, "company.team"),
                new Check("inputs", CheckType.SIZE_GTE, 1),
                new Check("tasks", CheckType.SIZE_GTE, 1)
            )
        )
    );

    private Map<String, String> prepareSchemas() throws Exception {
        return Map.of(
            "JSON", AbstractAiCopilot.minifySchema(TestSchemas.FLOW_SCHEMA, SchemaFormat.JSON),
            "TOON", AbstractAiCopilot.minifySchema(TestSchemas.FLOW_SCHEMA, SchemaFormat.TOON),
            "TS",   AbstractAiCopilot.minifySchema(TestSchemas.FLOW_SCHEMA, SchemaFormat.TYPESCRIPT)
        );
    }

    @Test
    void schemaTokenComparison() throws Exception {
        Map<String, String> schemas = prepareSchemas();

        System.out.println("=== SCHEMA FORMAT COMPARISON (no LLM needed) ===");
        System.out.printf("  %-6s  %8s  %8s%n", "Format", "Chars", "~Tokens");
        System.out.println("  " + "-".repeat(28));

        int jsonTokens = estimateTokenCount(schemas.get("JSON"));
        for (String format : FORMATS) {
            String schema = schemas.get(format);
            int tokens = estimateTokenCount(schema);
            double tokenDelta = ((double) tokens / jsonTokens - 1) * 100;
            System.out.printf("  %-6s  %8d  %7d (%+.0f%%)%n", format, schema.length(), tokens, tokenDelta);
        }

        // Show first 500 chars of each
        for (String format : FORMATS) {
            String schema = schemas.get(format);
            System.out.printf("%n--- %s (first 500 chars) ---%n", format);
            System.out.println(schema.substring(0, Math.min(500, schema.length())));
        }
    }

    @Test
    @Tag("benchmark")
    void benchmarkAllFormats() throws Exception {
        ChatModel model = realChatModel();
        FlowYamlBuilder flowYamlBuilder = AiServices.create(FlowYamlBuilder.class, model);
        Map<String, String> schemas = prepareSchemas();

        // Print schema sizes
        System.out.println("=== SCHEMA SIZES ===");
        int jsonTokens = estimateTokenCount(schemas.get("JSON"));
        for (String format : FORMATS) {
            int tokens = estimateTokenCount(schemas.get(format));
            double delta = ((double) tokens / jsonTokens - 1) * 100;
            System.out.printf("  %-6s  %6d chars  ~%5d tokens (%+.0f%%)%n",
                format, schemas.get(format).length(), tokens, delta);
        }
        System.out.println();

        List<Result> results = new ArrayList<>();

        for (Prompt prompt : PROMPTS) {
            for (int i = 0; i < ITERATIONS; i++) {
                for (String format : FORMATS) {
                    Result result = runSingle(flowYamlBuilder, schemas.get(format), prompt, format, i);
                    results.add(result);

                    System.out.printf("[%-4s] %s iter=%d  validYaml=%b  checksPass=%d/%d%n",
                        format, prompt.name, i, result.validYaml, result.checksPassed, result.checksTotal);
                }
            }
        }

        printSummary(results);

        // Soft assertions: alternative formats should not be drastically worse than JSON
        long jsonValid = results.stream().filter(r -> r.format.equals("JSON") && r.validYaml).count();
        long toonValid = results.stream().filter(r -> r.format.equals("TOON") && r.validYaml).count();
        long tsValid = results.stream().filter(r -> r.format.equals("TS") && r.validYaml).count();
        assertThat(toonValid)
            .as("TOON valid YAML count should be at least 60%% of JSON")
            .isGreaterThanOrEqualTo((long) (jsonValid * 0.6));
        assertThat(tsValid)
            .as("TS valid YAML count should be at least 60%% of JSON")
            .isGreaterThanOrEqualTo((long) (jsonValid * 0.6));
    }

    private Result runSingle(
        FlowYamlBuilder flowYamlBuilder,
        String schema,
        Prompt prompt,
        String format,
        int iteration
    ) {
        String yaml;
        try {
            yaml = flowYamlBuilder.buildFlow(
                schema,
                FlowAiCopilot.BAD_REQUEST_ERROR,
                "",
                "company.team",
                "benchmark_tenant",
                prompt.text
            );
        } catch (Exception e) {
            System.out.printf("  ERROR [%s] %s iter=%d: %s%n", format, prompt.name, iteration, e.getMessage());
            return new Result(prompt.name, format, false, 0, prompt.checks.size());
        }

        if (yaml != null) {
            yaml = yaml.replaceAll("\\s?```(?:yaml)?\\s?", "");
        }

        if (yaml == null || FlowAiCopilot.POSSIBLE_ERROR_MESSAGES.contains(yaml)) {
            System.out.printf("  REFUSED [%s] %s iter=%d: %s%n", format, prompt.name, iteration, yaml);
            return new Result(prompt.name, format, false, 0, prompt.checks.size());
        }

        JsonNode parsed;
        try {
            parsed = JacksonMapper.ofYaml().readTree(yaml);
        } catch (Exception e) {
            System.out.printf("  INVALID_YAML [%s] %s iter=%d: %s%n", format, prompt.name, iteration, e.getMessage());
            return new Result(prompt.name, format, false, 0, prompt.checks.size());
        }

        if (parsed == null || parsed.isMissingNode()) {
            return new Result(prompt.name, format, false, 0, prompt.checks.size());
        }

        int passed = 0;
        for (Check check : prompt.checks) {
            if (evaluateCheck(parsed, check)) {
                passed++;
            }
        }

        return new Result(prompt.name, format, true, passed, prompt.checks.size());
    }

    private void printSummary(List<Result> results) {
        System.out.println();
        System.out.println("=== BENCHMARK SUMMARY ===");
        System.out.printf("%-14s %-6s  %-12s %-12s%n", "Prompt", "Format", "Valid YAML", "Checks Pass");
        System.out.println("-".repeat(56));

        for (Prompt prompt : PROMPTS) {
            for (String format : FORMATS) {
                List<Result> matching = results.stream()
                    .filter(r -> r.promptName.equals(prompt.name) && r.format.equals(format))
                    .toList();

                long validCount = matching.stream().filter(r -> r.validYaml).count();
                long totalChecks = matching.stream().mapToInt(r -> r.checksTotal).sum();
                long passedChecks = matching.stream().mapToInt(r -> r.checksPassed).sum();

                System.out.printf("%-14s %-6s  %d/%d          %d/%d%n",
                    prompt.name, format, validCount, matching.size(), passedChecks, totalChecks);
            }
        }

        System.out.println("-".repeat(56));
        long total = results.stream().filter(r -> r.format.equals("JSON")).count();
        for (String format : FORMATS) {
            long valid = results.stream().filter(r -> r.format.equals(format) && r.validYaml).count();
            long checks = results.stream().filter(r -> r.format.equals(format)).mapToInt(r -> r.checksPassed).sum();
            long totalChecks = results.stream().filter(r -> r.format.equals(format)).mapToInt(r -> r.checksTotal).sum();
            System.out.printf("TOTAL          %-6s  %d/%d          %d/%d%n", format, valid, total, checks, totalChecks);
        }
        System.out.println();
    }

    private boolean evaluateCheck(JsonNode root, Check check) {
        JsonNode node = root.path(check.path);
        try {
            return switch (check.type) {
                case EQUALS -> node.asText().equals(check.expected.toString());
                case NOT_NULL -> !node.isMissingNode() && !node.isNull();
                case SIZE_GTE -> node.isArray() && node.size() >= (int) check.expected;
                case CONTAINS_IGNORING_CASE -> node.asText().toLowerCase().contains(check.expected.toString().toLowerCase());
            };
        } catch (Exception e) {
            return false;
        }
    }

    private record Result(String promptName, String format, boolean validYaml, int checksPassed, int checksTotal) {}

    private static final Pattern TOKEN_PATTERN = Pattern.compile(
        "\\s+|[a-zA-Z_][a-zA-Z0-9_./-]*|\\d+\\.?\\d*|[{}\\[\\]:,\"]|."
    );

    static int estimateTokenCount(String text) {
        int count = 0;
        Matcher m = TOKEN_PATTERN.matcher(text);
        while (m.find()) count++;
        return count;
    }
}