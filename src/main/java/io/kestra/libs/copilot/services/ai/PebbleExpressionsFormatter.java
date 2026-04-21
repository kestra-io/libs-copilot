package io.kestra.libs.copilot.services.ai;

import java.util.List;
import java.util.SequencedMap;

/**
 * Single source of truth for formatting a categorized Pebble expression map into
 * the string injected into LLM prompts.
 * <p>
 * All callers (OSS, EE, api.kestra.io fallback) must use this class so the prompt
 * format stays consistent across all copilot generation paths.
 */
public final class PebbleExpressionsFormatter {

    private PebbleExpressionsFormatter() {
    }

    /**
     * Formats a categorized expression map into a human-readable block for LLM prompts.
     * <p>
     * Each category is rendered as: {@code CategoryName: expr1, expr2, ...} on its own line.
     * Empty categories are omitted. Category display names are emitted verbatim as passed by the caller.
     *
     * @param expressionContext insertion-ordered map from category display name to expression list
     * @return formatted string suitable for injection into {@code {pebbleExpressions}} in prompts
     */
    public static String format(SequencedMap<String, List<String>> expressionContext) {
        if (expressionContext == null || expressionContext.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (var entry : expressionContext.entrySet()) {
            if (entry.getValue() == null || entry.getValue().isEmpty()) {
                continue;
            }
            sb.append(entry.getKey()).append(": ");
            sb.append(String.join(", ", entry.getValue()));
            sb.append("\n");
        }
        return sb.toString().stripTrailing();
    }
}
