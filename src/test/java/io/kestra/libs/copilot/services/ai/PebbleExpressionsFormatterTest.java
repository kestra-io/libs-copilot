package io.kestra.libs.copilot.services.ai;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.SequencedMap;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PebbleExpressionsFormatterTest {

    @Test
    void formatReturnsEmptyStringForNullMap() {
        assertThat(PebbleExpressionsFormatter.format(null)).isEqualTo("");
    }

    @Test
    void formatReturnsEmptyStringForEmptyMap() {
        assertThat(PebbleExpressionsFormatter.format(new LinkedHashMap<>())).isEqualTo("");
    }

    @Test
    void formatSkipsEmptyCategoryLists() {
        SequencedMap<String, List<String>> map = new LinkedHashMap<>();
        map.put("Filters", List.of());
        map.put("Functions", List.of("now()", "uuid()"));

        assertThat(PebbleExpressionsFormatter.format(map)).isEqualTo("Functions: now(), uuid()");
    }

    @Test
    void formatSkipsNullCategoryList() {
        SequencedMap<String, List<String>> map = new LinkedHashMap<>();
        map.put("Filters", null);
        map.put("Functions", List.of("now()"));

        assertThat(PebbleExpressionsFormatter.format(map)).isEqualTo("Functions: now()");
    }

    @Test
    void formatPreservesInsertionOrder() {
        SequencedMap<String, List<String>> map = new LinkedHashMap<>();
        map.put("Zebra", List.of("z1"));
        map.put("Alpha", List.of("a1"));
        map.put("Middle", List.of("m1"));

        String result = PebbleExpressionsFormatter.format(map);

        assertThat(result).isEqualTo("Zebra: z1\nAlpha: a1\nMiddle: m1");
    }

    @Test
    void formatStripsTrailingNewline() {
        SequencedMap<String, List<String>> map = new LinkedHashMap<>();
        map.put("Filters", List.of("upper", "lower"));

        String result = PebbleExpressionsFormatter.format(map);

        assertThat(result).doesNotEndWith("\n");
    }

    @Test
    void formatMultipleCategoriesHappyPath() {
        SequencedMap<String, List<String>> map = new LinkedHashMap<>();
        map.put("Filters (use as | filterName)", List.of("upper", "lower", "trim"));
        map.put("Functions", List.of("now()", "uuid()", "secret(key='MY_SECRET')"));

        String result = PebbleExpressionsFormatter.format(map);

        assertThat(result).isEqualTo(
            "Filters (use as | filterName): upper, lower, trim\n" +
            "Functions: now(), uuid(), secret(key='MY_SECRET')"
        );
    }
}
