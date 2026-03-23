package io.kestra.libs.copilot.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kestra.libs.copilot.services.ai.JacksonMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class ToonUtilsTest {
    private static final ObjectMapper MAPPER = JacksonMapper.ofJson();

    @Test
    void testSimpleObject() throws Exception {
        String json = """
            {
              "type": "object",
              "properties": {
                "name": {
                  "type": "string"
                }
              }
            }
            """;

        JsonNode node = MAPPER.readTree(json);
        String toon = ToonUtils.jsonToToon(node);

        Assertions.assertTrue(toon.contains("type: object"));
        Assertions.assertTrue(toon.contains("properties:"));
        Assertions.assertTrue(toon.contains("name:"));
        Assertions.assertTrue(toon.contains("type: string"));
    }

    @Test
    void testNestedObject() throws Exception {
        String json = """
            {
              "type": "object",
              "properties": {
                "user": {
                  "type": "object",
                  "properties": {
                    "name": {
                      "type": "string"
                    },
                    "age": {
                      "type": "integer"
                    }
                  }
                }
              }
            }
            """;

        JsonNode node = MAPPER.readTree(json);
        String toon = ToonUtils.jsonToToon(node);

        Assertions.assertTrue(toon.contains("user:"));
        Assertions.assertTrue(toon.contains("type: object"));
        Assertions.assertTrue(toon.contains("name:"));
        Assertions.assertTrue(toon.contains("age:"));
    }

    @Test
    void testPrimitiveArray() throws Exception {
        String json = """
            {
              "required": ["id", "name", "type"]
            }
            """;

        JsonNode node = MAPPER.readTree(json);
        String toon = ToonUtils.jsonToToon(node);

        Assertions.assertTrue(toon.contains("required[3]: id,name,type"));
    }

    @Test
    void testEmptyArray() throws Exception {
        String json = """
            {
              "items": []
            }
            """;

        JsonNode node = MAPPER.readTree(json);
        String toon = ToonUtils.jsonToToon(node);

        Assertions.assertTrue(toon.contains("items[0]:"));
    }

    @Test
    void testUniformObjectArray() throws Exception {
        String json = """
            {
              "users": [
                {
                  "id": 1,
                  "name": "Alice"
                },
                {
                  "id": 2,
                  "name": "Bob"
                }
              ]
            }
            """;

        JsonNode node = MAPPER.readTree(json);
        String toon = ToonUtils.jsonToToon(node);

        // Should use tabular format
        Assertions.assertTrue(toon.contains("users[2]{id,name}:"));
        Assertions.assertTrue(toon.contains(" 1,Alice"));
        Assertions.assertTrue(toon.contains(" 2,Bob"));
    }

    @Test
    void testMixedArray() throws Exception {
        String json = """
            {
              "items": [
                {
                  "type": "string"
                },
                {
                  "type": "integer",
                  "minimum": 0
                }
              ]
            }
            """;

        JsonNode node = MAPPER.readTree(json);
        String toon = ToonUtils.jsonToToon(node);

        // Should use list format due to non-uniform structure
        Assertions.assertTrue(toon.contains("items[2]:"));
        Assertions.assertTrue(toon.contains("- type: string"));
        Assertions.assertTrue(toon.contains("- type: integer"));
        Assertions.assertTrue(toon.contains("minimum: 0"));
    }

    @Test
    void testPrimitiveValues() throws Exception {
        String json = """
            {
              "string": "hello",
              "numberAsString": 42,
              "decimal": 3.14,
              "boolean": true,
              "nullValue": null
            }
            """;

        JsonNode node = MAPPER.readTree(json);
        String toon = ToonUtils.jsonToToon(node);

        Assertions.assertTrue(toon.contains("string: hello"));
        Assertions.assertTrue(toon.contains("numberAsString: 42"));
        Assertions.assertTrue(toon.contains("decimal: 3.14"));
        Assertions.assertTrue(toon.contains("boolean: true"));
        Assertions.assertTrue(toon.contains("nullValue: null"));
    }

    @Test
    void testStringEscaping() throws Exception {
        String json = """
            {
              "withColon": "key:value",
              "withQuotes": "say \\"hello\\"",
              "withNewline": "line1\\nline2",
              "withComma": "a,b,c"
            }
            """;

        JsonNode node = MAPPER.readTree(json);
        String toon = ToonUtils.jsonToToon(node);

        // These should be quoted due to special characters
        Assertions.assertTrue(toon.contains("withColon: \"key:value\""));
        Assertions.assertTrue(toon.contains("withQuotes:"));
        Assertions.assertTrue(toon.contains("withNewline:"));
        Assertions.assertTrue(toon.contains("withComma: \"a,b,c\""));
    }

    @Test
    void testReservedKeywords() throws Exception {
        String json = """
            {
              "truthValue": "true",
              "falseValue": "false",
              "nullString": "null"
            }
            """;

        JsonNode node = MAPPER.readTree(json);
        String toon = ToonUtils.jsonToToon(node);

        // Should be quoted to distinguish from boolean/null primitives
        Assertions.assertTrue(toon.contains("truthValue: \"true\""));
        Assertions.assertTrue(toon.contains("falseValue: \"false\""));
        Assertions.assertTrue(toon.contains("nullString: \"null\""));
    }

    @Test
    void testJsonSchemaPattern() throws Exception {
        String json = """
            {
              "type": "object",
              "properties": {
                "id": {
                  "type": "string",
                  "description": "Unique identifier"
                },
                "tasks": {
                  "type": "array",
                  "items": {
                    "type": "object",
                    "properties": {
                      "type": {
                        "type": "string"
                      }
                    }
                  }
                }
              },
              "required": ["id"]
            }
            """;

        JsonNode node = MAPPER.readTree(json);
        String toon = ToonUtils.jsonToToon(node);

        Assertions.assertTrue(toon.contains("type: object"));
        Assertions.assertTrue(toon.contains("properties:"));
        Assertions.assertTrue(toon.contains("id:"));
        Assertions.assertTrue(toon.contains("tasks:"));
        Assertions.assertTrue(toon.contains("items:"));
        Assertions.assertTrue(toon.contains("required[1]: id"));
    }

    @Test
    void testSizeReduction() throws Exception {
        String json = """
            {
              "type": "object",
              "properties": {
                "id": {
                  "type": "string",
                  "description": "Unique identifier"
                },
                "name": {
                  "type": "string"
                },
                "age": {
                  "type": "integer"
                },
                "tags": {
                  "type": "array",
                  "items": {
                    "type": "string"
                  }
                }
              },
              "required": ["id", "name"]
            }
            """;

        JsonNode node = MAPPER.readTree(json);
        String toon = ToonUtils.jsonToToon(node);
        String jsonMinified = MAPPER.writeValueAsString(node);

        // TOON should be more compact than minified JSON
        Assertions.assertTrue(toon.length() < jsonMinified.length());
        
        // Calculate reduction percentage
        double reduction = (1.0 - (double) toon.length() / jsonMinified.length()) * 100;
        System.out.println("\n" + "=".repeat(80));
        System.out.println("BEFORE (JSON Format):");
        System.out.println("=".repeat(80));
        System.out.println(jsonMinified);
        System.out.println("\nSize: " + jsonMinified.length() + " bytes");
        
        System.out.println("\n" + "=".repeat(80));
        System.out.println("AFTER (TOON Format):");
        System.out.println("=".repeat(80));
        System.out.println(toon);
        System.out.println("\nSize: " + toon.length() + " bytes");
        
        System.out.println("\n" + "=".repeat(80));
        System.out.println("COMPARISON RESULTS:");
        System.out.println("=".repeat(80));
        System.out.printf("JSON size: %d bytes%n", jsonMinified.length());
        System.out.printf("TOON size: %d bytes%n", toon.length());
        System.out.printf("Reduction: %.1f%%%n", reduction);
        System.out.printf("Saved: %d bytes%n", jsonMinified.length() - toon.length());
        System.out.println("\nBenefits:");
        System.out.println("✓ Reduced token usage for LLM");
        System.out.println("✓ Faster response times");
        System.out.println("✓ More readable indentation-based format");
        System.out.println("✓ Less redundant syntax");
        System.out.println("=".repeat(80) + "\n");
        
        // Should have at least some reduction
        Assertions.assertTrue(reduction > 0.0);
    }

    @Test
    void testEmptyObject() throws Exception {
        String json = "{}";
        JsonNode node = MAPPER.readTree(json);
        String toon = ToonUtils.jsonToToon(node);

        // Empty object should produce empty string
        Assertions.assertEquals("", toon);
    }

    @Test
    void testRootPrimitive() throws Exception {
        String json = "\"hello\"";
        JsonNode node = MAPPER.readTree(json);
        String toon = ToonUtils.jsonToToon(node);

        Assertions.assertEquals("hello", toon);
    }

    @Test
    void testRootNumber() throws Exception {
        String json = "42";
        JsonNode node = MAPPER.readTree(json);
        String toon = ToonUtils.jsonToToon(node);

        Assertions.assertEquals("42", toon);
    }

    @Test
    void testRootArray() throws Exception {
        String json = "[1, 2, 3]";
        JsonNode node = MAPPER.readTree(json);
        String toon = ToonUtils.jsonToToon(node);

        Assertions.assertTrue(toon.contains("[3]: 1,2,3"));
    }

    @Test
    void testNullInput() {
        assertThrows(IllegalArgumentException.class, () -> ToonUtils.jsonToToon(null));
    }

    @Test
    void testKeyFormatting() throws Exception {
        String json = """
            {
              "simple": "value",
              "with-dash": "value",
              "with space": "value",
              "with.dot": "value",
              "$special": "value"
            }
            """;

        JsonNode node = MAPPER.readTree(json);
        String toon = ToonUtils.jsonToToon(node);

        // Simple keys and keys with dots should be unquoted
        Assertions.assertTrue(toon.contains("simple: value"));
        Assertions.assertTrue(toon.contains("with.dot: value"));

        // Keys with dashes and spaces should be quoted
        Assertions.assertTrue(toon.contains("\"with-dash\": value"));
        Assertions.assertTrue(toon.contains("\"with space\": value"));
        Assertions.assertTrue(toon.contains("\"$special\": value"));
    }

    @Test
    void testNestedArrays() throws Exception {
        String json = """
            {
              "matrix": [
                [1, 2],
                [3, 4]
              ]
            }
            """;

        JsonNode node = MAPPER.readTree(json);
        String toon = ToonUtils.jsonToToon(node);

        Assertions.assertTrue(toon.contains("matrix[2]:"));
        Assertions.assertTrue(toon.contains("- [2]: 1,2"));
        Assertions.assertTrue(toon.contains("- [2]: 3,4"));
    }

    @Test
    void testNumberFormatting() throws Exception {
        String json = """
            {
              "zero": 0,
              "negative": -5,
              "decimal": 3.14159,
              "scientific": 1.5e10,
              "trailingZeros": 10.0
            }
            """;

        JsonNode node = MAPPER.readTree(json);
        String toon = ToonUtils.jsonToToon(node);

        Assertions.assertTrue(toon.contains("zero: 0"));
        Assertions.assertTrue(toon.contains("negative: -5"));
        Assertions.assertTrue(toon.contains("decimal: 3.14159"));
        // Scientific notation should be converted to plain
        Assertions.assertTrue(toon.contains("scientific: 15000000000"));
        // Trailing zeros should be stripped
        Assertions.assertTrue(toon.contains("trailingZeros: 10"));
    }
}
