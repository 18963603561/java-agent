package com.example.agent.agentcore;

import com.example.agent.common.ErrorCodeException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;

/**
 * 工具参数 JSON Schema 校验与轻量纠正器。
 * 仅覆盖必填、类型与额外字段控制，避免改变现有业务语义。
 */
public class ToolArgumentValidator {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * 校验并纠正工具调用参数。
     *
     * @param schema 输入参数 schema
     * @param arguments 实际调用参数
     * @param toolName 工具名称
     * @return 校验通过且纠正后的参数
     */
    public Map<String, Object> validateAndNormalize(Map<String, Object> schema,
                                                    Map<String, Object> arguments,
                                                    String toolName) {
        Map<String, Object> safeArguments = arguments == null ? new HashMap<>() : new HashMap<>(arguments);
        if (schema == null || schema.isEmpty()) {
            return safeArguments;
        }
        validateObjectSchema(schema, safeArguments, toolName, "arguments");
        return safeArguments;
    }

    /**
     * 校验并纠正 JsonNode 形式的工具调用参数。
     *
     * @param schema 输入参数 schema
     * @param arguments JsonNode 形式参数
     * @param toolName 工具名称
     * @return 校验通过且纠正后的参数
     */
    public Map<String, Object> validateAndNormalize(Map<String, Object> schema,
                                                    JsonNode arguments,
                                                    String toolName) {
        Map<String, Object> normalized = arguments == null
                ? new HashMap<>()
                : OBJECT_MAPPER.convertValue(arguments, Map.class);
        return validateAndNormalize(schema, normalized, toolName);
    }

    private void validateObjectSchema(Map<String, Object> schema,
                                      Map<String, Object> target,
                                      String toolName,
                                      String path) {
        String type = normalizeType(schema.get("type"));
        if (type != null && !"object".equals(type)) {
            throw invalid(toolName, path, "参数类型不匹配");
        }
        List<String> requiredFields = toStringList(schema.get("required"));
        for (String field : requiredFields) {
            if (!target.containsKey(field) || target.get(field) == null) {
                throw invalid(toolName, buildPath(path, field), "必填字段缺失");
            }
        }

        Map<String, Object> properties = toMap(schema.get("properties"));
        Boolean additionalProperties = toBoolean(schema.get("additionalProperties"));
        if (Boolean.FALSE.equals(additionalProperties)) {
            Set<String> allowed = properties != null ? properties.keySet() : Set.of();
            for (String key : target.keySet()) {
                if (!allowed.contains(key)) {
                    throw invalid(toolName, buildPath(path, key), "不允许额外字段");
                }
            }
        }

        if (properties == null || properties.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            String field = entry.getKey();
            if (!target.containsKey(field)) {
                continue;
            }
            Map<String, Object> fieldSchema = toMap(entry.getValue());
            if (fieldSchema == null || fieldSchema.isEmpty()) {
                continue;
            }
            Object value = target.get(field);
            Object normalized = normalizeValue(fieldSchema, value, toolName, buildPath(path, field));
            if (normalized != value) {
                target.put(field, normalized);
            }
        }
    }

    private Object normalizeValue(Map<String, Object> schema, Object value, String toolName, String path) {
        String type = normalizeType(schema.get("type"));
        if (type == null) {
            return value;
        }
        if (value == null) {
            throw invalid(toolName, path, "字段值不能为空");
        }
        return switch (type) {
            case "string" -> normalizeString(value, toolName, path);
            case "number" -> normalizeNumber(value, toolName, path);
            case "integer" -> normalizeInteger(value, toolName, path);
            case "boolean" -> normalizeBoolean(value, toolName, path);
            case "object" -> normalizeObject(schema, value, toolName, path);
            case "array" -> normalizeArray(schema, value, toolName, path);
            default -> value;
        };
    }

    private Object normalizeString(Object value, String toolName, String path) {
        if (value instanceof String) {
            return value;
        }
        if (value instanceof JsonNode node && node.isTextual()) {
            return node.textValue();
        }
        throw invalid(toolName, path, "类型应为字符串");
    }

    private Object normalizeNumber(Object value, String toolName, String path) {
        if (value instanceof Number) {
            return value;
        }
        if (value instanceof String text) {
            try {
                return new BigDecimal(text.trim());
            } catch (NumberFormatException ex) {
                throw invalid(toolName, path, "类型应为数字");
            }
        }
        if (value instanceof JsonNode node) {
            if (node.isNumber()) {
                return node.decimalValue();
            }
            if (node.isTextual()) {
                return normalizeNumber(node.textValue(), toolName, path);
            }
        }
        throw invalid(toolName, path, "类型应为数字");
    }

    private Object normalizeInteger(Object value, String toolName, String path) {
        if (value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long) {
            return value;
        }
        if (value instanceof BigDecimal decimal) {
            try {
                return decimal.longValueExact();
            } catch (ArithmeticException ex) {
                throw invalid(toolName, path, "类型应为整数");
            }
        }
        if (value instanceof Number number) {
            double doubleValue = number.doubleValue();
            if (doubleValue % 1 == 0) {
                return (long) doubleValue;
            }
            throw invalid(toolName, path, "类型应为整数");
        }
        if (value instanceof String text) {
            try {
                return Long.parseLong(text.trim());
            } catch (NumberFormatException ex) {
                throw invalid(toolName, path, "类型应为整数");
            }
        }
        if (value instanceof JsonNode node) {
            if (node.isIntegralNumber()) {
                return node.longValue();
            }
            if (node.isTextual()) {
                return normalizeInteger(node.textValue(), toolName, path);
            }
        }
        throw invalid(toolName, path, "类型应为整数");
    }

    private Object normalizeBoolean(Object value, String toolName, String path) {
        if (value instanceof Boolean) {
            return value;
        }
        if (value instanceof String text) {
            String normalized = text.trim().toLowerCase(Locale.ROOT);
            if ("true".equals(normalized)) {
                return true;
            }
            if ("false".equals(normalized)) {
                return false;
            }
            throw invalid(toolName, path, "类型应为布尔值");
        }
        if (value instanceof JsonNode node) {
            if (node.isBoolean()) {
                return node.booleanValue();
            }
            if (node.isTextual()) {
                return normalizeBoolean(node.textValue(), toolName, path);
            }
        }
        throw invalid(toolName, path, "类型应为布尔值");
    }

    private Object normalizeObject(Map<String, Object> schema, Object value, String toolName, String path) {
        Map<String, Object> mapValue = toMap(value);
        if (mapValue == null) {
            throw invalid(toolName, path, "类型应为对象");
        }
        Map<String, Object> mutable = new HashMap<>(mapValue);
        validateObjectSchema(schema, mutable, toolName, path);
        return mutable;
    }

    private Object normalizeArray(Map<String, Object> schema, Object value, String toolName, String path) {
        List<Object> listValue = toList(value);
        if (listValue == null) {
            throw invalid(toolName, path, "类型应为数组");
        }
        Map<String, Object> itemSchema = toMap(schema.get("items"));
        if (itemSchema == null || itemSchema.isEmpty()) {
            return listValue;
        }
        List<Object> normalized = new ArrayList<>(listValue.size());
        for (int i = 0; i < listValue.size(); i++) {
            String itemPath = path + "[" + i + "]";
            normalized.add(normalizeValue(itemSchema, listValue.get(i), toolName, itemPath));
        }
        return normalized;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        if (value instanceof JsonNode node && node.isObject()) {
            return OBJECT_MAPPER.convertValue(node, Map.class);
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private List<Object> toList(Object value) {
        if (value instanceof List<?> list) {
            return (List<Object>) list;
        }
        if (value instanceof Object[] array) {
            return new ArrayList<>(List.of(array));
        }
        if (value instanceof JsonNode node && node.isArray()) {
            return OBJECT_MAPPER.convertValue(node, List.class);
        }
        return null;
    }

    private List<String> toStringList(Object value) {
        if (value instanceof List<?> list) {
            List<String> results = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof String text) {
                    results.add(text);
                }
            }
            return results;
        }
        if (value instanceof String text) {
            return List.of(text);
        }
        if (value instanceof JsonNode node && node.isArray()) {
            List<String> results = new ArrayList<>();
            for (JsonNode item : node) {
                if (item.isTextual()) {
                    results.add(item.textValue());
                }
            }
            return results;
        }
        return List.of();
    }

    private Boolean toBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String text) {
            return Boolean.parseBoolean(text.trim());
        }
        if (value instanceof JsonNode node && node.isBoolean()) {
            return node.booleanValue();
        }
        return null;
    }

    private String normalizeType(Object value) {
        if (value instanceof String text) {
            return text.trim().toLowerCase(Locale.ROOT);
        }
        if (value instanceof JsonNode node && node.isTextual()) {
            return node.textValue().trim().toLowerCase(Locale.ROOT);
        }
        return null;
    }

    private String buildPath(String base, String field) {
        if (base == null || base.isBlank()) {
            return field;
        }
        return base + "." + field;
    }

    private ErrorCodeException invalid(String toolName, String path, String reason) {
        String prefix = toolName == null || toolName.isBlank()
                ? "工具参数校验失败"
                : "工具参数校验失败: tool=" + toolName;
        String detail = path == null || path.isBlank()
                ? prefix
                : prefix + ", field=" + path;
        return new ErrorCodeException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", detail + ", reason=" + reason);
    }
}
