package io.kestra.libs.copilot.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.*;

/**
 * Converts a JSON Schema (draft-07) tree into TypeScript-style type definitions.
 * Designed for LLM token efficiency: TypeScript notation is ~60% smaller than JSON
 * Schema in token count because LLM tokenizers compress it naturally.
 *
 * Handles: $ref, allOf, anyOf, oneOf, type, properties, required, enum, const,
 * items, additionalProperties, default, title/description annotations.
 */
public final class JsonSchemaToTypeScript {

    private JsonSchemaToTypeScript() {}

    public static String convert(JsonNode schema) {
        Map<String, JsonNode> definitions = new LinkedHashMap<>();
        JsonNode defsNode = schema.path("definitions");
        if (defsNode.isObject()) {
            defsNode.fields().forEachRemaining(e -> definitions.put(e.getKey(), e.getValue()));
        }

        // Resolve the root type
        String rootRef = refTarget(schema);
        if (rootRef == null) {
            return "type Root = " + typeExpr(schema, definitions, new HashSet<>()) + "\n";
        }

        // Emit definitions in order, starting with root
        StringBuilder sb = new StringBuilder();
        Set<String> emitted = new LinkedHashSet<>();
        Set<String> referenced = new LinkedHashSet<>();
        referenced.add(rootRef);

        // BFS to emit only reachable definitions
        Deque<String> queue = new ArrayDeque<>();
        queue.add(rootRef);
        while (!queue.isEmpty()) {
            String name = queue.poll();
            if (!emitted.add(name)) continue;
            JsonNode defn = definitions.get(name);
            if (defn == null) continue;

            // Collect refs in this definition
            collectRefs(defn, referenced, queue, emitted);
        }

        // Emit in dependency order (emitted preserves BFS order)
        for (String name : emitted) {
            JsonNode defn = definitions.get(name);
            if (defn == null) continue;
            sb.append(emitDefinition(shortName(name), defn, definitions));
        }

        return sb.toString();
    }

    private static String emitDefinition(String name, JsonNode defn, Map<String, JsonNode> definitions) {
        StringBuilder sb = new StringBuilder();

        // Add title/description as comment if present
        String comment = annotation(defn);
        if (comment != null) {
            sb.append("// ").append(comment).append("\n");
        }

        if (isObjectWithProperties(defn)) {
            sb.append("interface ").append(name).append(" {\n");
            sb.append(propertiesBlock(defn, definitions));
            sb.append("}\n\n");
        } else if (defn.has("enum")) {
            sb.append("type ").append(name).append(" = ").append(enumExpr(defn.get("enum"))).append("\n\n");
        } else if (defn.has("allOf")) {
            sb.append("type ").append(name).append(" = ").append(allOfExpr(defn.get("allOf"), definitions, new HashSet<>())).append("\n\n");
        } else if (defn.has("anyOf") || defn.has("oneOf")) {
            JsonNode variants = defn.has("anyOf") ? defn.get("anyOf") : defn.get("oneOf");
            sb.append("type ").append(name).append(" = ").append(unionExpr(variants, definitions, new HashSet<>())).append("\n\n");
        } else {
            sb.append("type ").append(name).append(" = ").append(typeExpr(defn, definitions, new HashSet<>())).append("\n\n");
        }

        return sb.toString();
    }

    private static String propertiesBlock(JsonNode schema, Map<String, JsonNode> definitions) {
        // Merge properties from allOf if needed
        Map<String, JsonNode> props = new LinkedHashMap<>();
        Set<String> required = new HashSet<>();
        collectProperties(schema, props, required, definitions);

        StringBuilder sb = new StringBuilder();
        for (var entry : props.entrySet()) {
            String propName = entry.getKey();
            JsonNode propSchema = entry.getValue();

            // Skip deprecated properties entirely
            if (isDeprecated(propSchema)) continue;

            String comment = annotation(propSchema);
            if (comment != null) {
                sb.append("  // ").append(comment).append("\n");
            }

            String opt = required.contains(propName) ? "" : "?";
            String type = typeExpr(propSchema, definitions, new HashSet<>());

            // Build inline constraint comment
            String constraints = constraintsComment(propSchema);

            sb.append("  ").append(propName).append(opt).append(": ").append(type).append(constraints).append("\n");
        }
        return sb.toString();
    }

    private static void collectProperties(JsonNode schema, Map<String, JsonNode> props, Set<String> required, Map<String, JsonNode> definitions) {
        // Follow $ref
        String ref = refTarget(schema);
        if (ref != null) {
            JsonNode resolved = definitions.get(ref);
            if (resolved != null) {
                collectProperties(resolved, props, required, definitions);
            }
            return;
        }

        // Merge allOf
        if (schema.has("allOf")) {
            for (JsonNode part : schema.get("allOf")) {
                collectProperties(part, props, required, definitions);
            }
        }

        // Direct properties
        JsonNode propsNode = schema.path("properties");
        if (propsNode.isObject()) {
            propsNode.fields().forEachRemaining(e -> props.put(e.getKey(), e.getValue()));
        }

        JsonNode reqNode = schema.path("required");
        if (reqNode.isArray()) {
            for (JsonNode r : reqNode) {
                required.add(r.asText());
            }
        }
    }

    static String typeExpr(JsonNode schema, Map<String, JsonNode> definitions, Set<String> seen) {
        if (schema == null || schema.isMissingNode()) return "unknown";

        // $ref
        String ref = refTarget(schema);
        if (ref != null) {
            return shortName(ref);
        }

        // const
        if (schema.has("const")) {
            return literal(schema.get("const"));
        }

        // enum
        if (schema.has("enum")) {
            return enumExpr(schema.get("enum"));
        }

        // allOf
        if (schema.has("allOf")) {
            return allOfExpr(schema.get("allOf"), definitions, seen);
        }

        // anyOf / oneOf → union
        if (schema.has("anyOf") || schema.has("oneOf")) {
            JsonNode variants = schema.has("anyOf") ? schema.get("anyOf") : schema.get("oneOf");
            return unionExpr(variants, definitions, seen);
        }

        // type-based
        String type = schema.path("type").asText(null);
        if (type == null) {
            // No type, might be a complex schema — check for properties
            if (schema.has("properties")) {
                return inlineObject(schema, definitions, seen);
            }
            return "unknown";
        }

        return switch (type) {
            case "string" -> {
                String format = schema.path("format").asText(null);
                if (format != null) yield "string /* " + format + " */";
                yield "string";
            }
            case "integer", "number" -> "number";
            case "boolean" -> "boolean";
            case "null" -> "null";
            case "array" -> {
                JsonNode items = schema.path("items");
                if (items.isMissingNode() || items.isEmpty()) yield "any[]";
                // items can have anyOf/oneOf for polymorphic arrays
                String itemType = typeExpr(items, definitions, seen);
                // Wrap union types in parens for array
                if (itemType.contains(" | ")) yield "(" + itemType + ")[]";
                yield itemType + "[]";
            }
            case "object" -> {
                if (schema.has("properties")) {
                    yield inlineObject(schema, definitions, seen);
                }
                if (schema.has("additionalProperties")) {
                    JsonNode addl = schema.get("additionalProperties");
                    if (addl.isBoolean()) yield addl.asBoolean() ? "Record<string, any>" : "{}";
                    yield "Record<string, " + typeExpr(addl, definitions, seen) + ">";
                }
                yield "Record<string, any>";
            }
            default -> type;
        };
    }

    private static String inlineObject(JsonNode schema, Map<String, JsonNode> definitions, Set<String> seen) {
        Map<String, JsonNode> props = new LinkedHashMap<>();
        Set<String> required = new HashSet<>();
        collectProperties(schema, props, required, definitions);
        if (props.isEmpty()) return "{}";

        StringBuilder sb = new StringBuilder("{ ");
        boolean first = true;
        for (var entry : props.entrySet()) {
            if (!first) sb.append("; ");
            first = false;
            String opt = required.contains(entry.getKey()) ? "" : "?";
            sb.append(entry.getKey()).append(opt).append(": ").append(typeExpr(entry.getValue(), definitions, seen));
        }
        sb.append(" }");
        return sb.toString();
    }

    private static String allOfExpr(JsonNode allOf, Map<String, JsonNode> definitions, Set<String> seen) {
        // If allOf has a $ref + object with properties, merge into one object
        List<String> refParts = new ArrayList<>();
        Map<String, JsonNode> extraProps = new LinkedHashMap<>();
        Set<String> extraRequired = new HashSet<>();
        boolean hasObjectParts = false;

        for (JsonNode part : allOf) {
            String ref = refTarget(part);
            if (ref != null) {
                refParts.add(shortName(ref));
            } else if (part.has("properties") || part.has("required")) {
                // Skip parts that only have non-schema metadata ($dynamic, $group)
                JsonNode propsNode = part.path("properties");
                if (propsNode.isObject()) {
                    propsNode.fields().forEachRemaining(e -> extraProps.put(e.getKey(), e.getValue()));
                    hasObjectParts = true;
                }
                JsonNode reqNode = part.path("required");
                if (reqNode.isArray()) {
                    for (JsonNode r : reqNode) extraRequired.add(r.asText());
                }
            } else if (isSchemaRelevant(part)) {
                refParts.add(typeExpr(part, definitions, seen));
            }
            // Skip non-schema metadata nodes like {$dynamic: false}
        }

        if (refParts.size() == 1 && extraProps.isEmpty()) {
            return refParts.getFirst();
        }

        List<String> parts = new ArrayList<>(refParts);
        if (hasObjectParts) {
            StringBuilder obj = new StringBuilder("{ ");
            boolean first = true;
            for (var entry : extraProps.entrySet()) {
                if (!first) obj.append("; ");
                first = false;
                String opt = extraRequired.contains(entry.getKey()) ? "" : "?";
                obj.append(entry.getKey()).append(opt).append(": ").append(typeExpr(entry.getValue(), definitions, seen));
            }
            obj.append(" }");
            parts.add(obj.toString());
        }

        return String.join(" & ", parts);
    }

    private static String unionExpr(JsonNode variants, Map<String, JsonNode> definitions, Set<String> seen) {
        List<String> types = new ArrayList<>();
        for (JsonNode v : variants) {
            types.add(typeExpr(v, definitions, seen));
        }
        return String.join(" | ", types);
    }

    private static String enumExpr(JsonNode enumNode) {
        List<String> values = new ArrayList<>();
        for (JsonNode v : enumNode) {
            values.add(literal(v));
        }
        return String.join(" | ", values);
    }

    private static String literal(JsonNode node) {
        if (node.isTextual()) return "\"" + node.asText() + "\"";
        if (node.isNumber()) return node.asText();
        if (node.isBoolean()) return String.valueOf(node.asBoolean());
        if (node.isNull()) return "null";
        return node.toString();
    }

    private static String refTarget(JsonNode schema) {
        JsonNode ref = schema.path("$ref");
        if (ref.isTextual()) {
            String val = ref.asText();
            if (val.startsWith("#/definitions/")) {
                return val.substring("#/definitions/".length());
            }
            return val;
        }
        return null;
    }

    static String shortName(String fqn) {
        // "io.kestra.core.models.flows.Flow" → "Flow"
        // "io.kestra.core.models.flows.input.StringInput-2" → "StringInput_2"
        int lastDot = fqn.lastIndexOf('.');
        String name = lastDot >= 0 ? fqn.substring(lastDot + 1) : fqn;
        return name.replace('-', '_');
    }

    private static String annotation(JsonNode schema) {
        String title = schema.path("title").asText(null);
        if (title != null) return title;
        String desc = schema.path("description").asText(null);
        if (desc != null) return desc;
        // markdownDescription often carries useful info like default values
        String md = schema.path("markdownDescription").asText(null);
        if (md != null && !md.startsWith("Default value")) return md;
        return null;
    }

    private static boolean isDeprecated(JsonNode schema) {
        // Check the property node and any nested allOf/anyOf for $deprecated
        if (schema.path("$deprecated").asBoolean(false)) return true;
        for (String compositeKey : List.of("allOf", "anyOf", "oneOf")) {
            JsonNode composite = schema.path(compositeKey);
            if (composite.isArray()) {
                for (JsonNode part : composite) {
                    if (part.path("$deprecated").asBoolean(false)) return true;
                }
            }
        }
        return false;
    }

    private static String constraintsComment(JsonNode schema) {
        List<String> parts = new ArrayList<>();

        // Default value
        JsonNode def = schema.path("default");
        if (!def.isMissingNode() && !def.isNull() && !(def.isBoolean() && !def.asBoolean())) {
            parts.add("default: " + (def.isTextual() ? "\"" + def.asText() + "\"" : def.asText()));
        }

        // Pattern
        String pattern = schema.path("pattern").asText(null);
        if (pattern != null) parts.add("pattern: " + pattern);

        // Length/size constraints — compact form
        addBound(parts, schema, "minLength", "minLen");
        addBound(parts, schema, "maxLength", "maxLen");
        addBound(parts, schema, "minimum", "min");
        addBound(parts, schema, "maximum", "max");
        addBound(parts, schema, "minItems", "minItems");
        addBound(parts, schema, "maxItems", "maxItems");

        if (parts.isEmpty()) return "";
        return " // " + String.join(", ", parts);
    }

    private static void addBound(List<String> parts, JsonNode schema, String key, String label) {
        JsonNode val = schema.path(key);
        if (!val.isMissingNode()) {
            parts.add(label + ": " + val.asText());
        }
    }

    private static boolean isObjectWithProperties(JsonNode schema) {
        return "object".equals(schema.path("type").asText(null)) && schema.has("properties");
    }

    private static boolean isSchemaRelevant(JsonNode node) {
        if (!node.isObject()) return false;
        ObjectNode obj = (ObjectNode) node;
        // Skip nodes that only have metadata keys
        Iterator<String> fields = obj.fieldNames();
        while (fields.hasNext()) {
            String field = fields.next();
            if (!field.startsWith("$")) return true;
        }
        return false;
    }

    private static void collectRefs(JsonNode node, Set<String> referenced, Deque<String> queue, Set<String> emitted) {
        if (node == null) return;
        if (node.isObject()) {
            String ref = refTarget(node);
            if (ref != null && !emitted.contains(ref)) {
                referenced.add(ref);
                queue.add(ref);
            }
            node.fields().forEachRemaining(e -> collectRefs(e.getValue(), referenced, queue, emitted));
        } else if (node.isArray()) {
            for (JsonNode item : node) {
                collectRefs(item, referenced, queue, emitted);
            }
        }
    }
}