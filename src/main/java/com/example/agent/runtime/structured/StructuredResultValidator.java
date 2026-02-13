package com.example.agent.runtime.structured;

import com.example.agent.runtime.structured.StructuredResultSchemaRegistry.SchemaDefinition;
import com.example.agent.runtime.structured.StructuredResultSchemaRegistry.SchemaType;
import com.example.agent.runtime.structured.result.StructuredResult;
import com.example.agent.runtime.structured.structured.StructuredData;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 结构化结果校验器。
 *
 * <p>用途：基于注册的结构约束校验结构化结果是否符合主流程要求。</p>
 */
@Component
public class StructuredResultValidator {

    /**
     * 结构化校验定义注册表。
     */
    private final StructuredResultSchemaRegistry schemaRegistry;

    public StructuredResultValidator(StructuredResultSchemaRegistry schemaRegistry) {
        this.schemaRegistry = schemaRegistry;
    }

    /**
     * 校验结构化结果。
     *
     * @param result 结构化结果
     * @return 校验结论
     */
    public StructuredValidationResult validate(StructuredResult<? extends StructuredData> result) {
        // 判断结构化结果是否为空，空时直接返回失败。
        if (result == null) {
            // 构建缺少结果对象的失败结论。
            return StructuredValidationResult.invalid(List.of("result_null"));
        }
        // 读取结果类型，作为校验选择依据。
        ResultKind kind = result.getKind();
        // 判断类型是否为空，空时直接返回失败。
        if (kind == null) {
            // 构建缺少类型的失败结论。
            return StructuredValidationResult.invalid(List.of("kind_missing"));
        }
        // 归一化结构版本，避免空值影响查找。
        Integer schemaVersion = normalizeSchemaVersion(result.getSchemaVersion());
        // 查询结构化校验定义。
        SchemaDefinition definition = schemaRegistry.findSchema(kind, schemaVersion);
        // 判断是否命中校验定义，未命中时返回带警告的通过结果。
        if (definition == null) {
            // 构建缺少校验定义的告警结论。
            return StructuredValidationResult.validWithWarnings(List.of("schema_not_found"));
        }
        // 提取结构化数据映射，作为校验输入。
        Map<String, Object> data = result.dataAsMap();
        // 根据校验类型执行对应的结构校验。
        List<String> errors = switch (definition.getSchemaType()) {
            case SQL -> {
                // 调用 SQL 结构校验。
                yield validateSqlData(data);
            }
            case RECORDSET -> {
                // 调用记录集结构校验。
                yield validateRecordsetData(data);
            }
            case ERROR -> {
                // 调用错误结构校验。
                yield validateErrorData(data);
            }
            case DEFAULT -> {
                // 调用默认结构校验。
                yield validateDefaultData(data);
            }
        };
        // 判断是否存在错误，空时返回通过结论。
        if (errors.isEmpty()) {
            // 返回通过校验的结论。
            return StructuredValidationResult.valid();
        }
        // 返回包含错误的失败结论。
        return StructuredValidationResult.invalid(errors);
    }

    private Integer normalizeSchemaVersion(Integer schemaVersion) {
        // 判断结构版本是否为空，空时使用默认版本。
        if (schemaVersion == null) {
            // 返回默认结构版本。
            return 1;
        }
        // 返回原始结构版本。
        return schemaVersion;
    }

    private List<String> validateSqlData(Map<String, Object> data) {
        // 初始化错误列表容器。
        List<String> errors = new ArrayList<>();
        // 读取 SQL 字段值。
        Object sql = data.get("sql");
        // 判断 SQL 是否为空或空白，空时记录错误。
        if (!hasText(sql)) {
            // 记录 SQL 缺失错误。
            errors.add("sql_required");
        }
        // 读取方言字段值。
        Object dialect = data.get("dialect");
        // 判断方言类型是否为字符串，非字符串时记录错误。
        if (dialect != null && !(dialect instanceof String)) {
            // 记录方言字段类型错误。
            errors.add("dialect_type_invalid");
        }
        // 返回 SQL 校验错误列表。
        return errors;
    }

    private List<String> validateRecordsetData(Map<String, Object> data) {
        // 初始化错误列表容器。
        List<String> errors = new ArrayList<>();
        // 读取列列表字段值。
        Object columns = data.get("columns");
        // 判断列字段是否为列表，不是列表时记录错误。
        if (!(columns instanceof List<?> list)) {
            // 记录列字段缺失或类型错误。
            errors.add("columns_invalid");
        } else if (!list.isEmpty()) {
            // 设计意图：仅检查首元素类型以降低校验成本，风险是无法发现后续异常项。
            // 读取首个列元素。
            Object first = list.get(0);
            // 判断首个列元素是否为字符串，非字符串时记录错误。
            if (!(first instanceof String)) {
                // 记录列元素类型错误。
                errors.add("columns_element_invalid");
            }
        }
        // 读取行列表字段值。
        Object rows = data.get("rows");
        // 判断行字段是否为列表，不是列表时记录错误。
        if (!(rows instanceof List<?> list)) {
            // 记录行字段缺失或类型错误。
            errors.add("rows_invalid");
        } else if (!list.isEmpty()) {
            // 设计意图：仅检查首行类型以降低校验成本，风险是无法发现后续异常项。
            // 读取首个行元素。
            Object first = list.get(0);
            // 判断首行元素是否为映射，非映射时记录错误。
            if (!(first instanceof Map<?, ?>)) {
                // 记录行元素类型错误。
                errors.add("rows_element_invalid");
            }
        }
        // 返回记录集校验错误列表。
        return errors;
    }

    private List<String> validateDefaultData(Map<String, Object> data) {
        // 初始化错误列表容器。
        List<String> errors = new ArrayList<>();
        // 读取 keys 字段值。
        Object keys = data.get("keys");
        // 判断 keys 是否为列表，不是列表时记录错误。
        if (!(keys instanceof List<?> list)) {
            // 记录 keys 缺失或类型错误。
            errors.add("keys_invalid");
        } else if (!list.isEmpty()) {
            // 设计意图：仅检查首元素类型以降低校验成本，风险是无法发现后续异常项。
            // 读取首个 keys 元素。
            Object first = list.get(0);
            // 判断首个 keys 元素是否为字符串，非字符串时记录错误。
            if (!(first instanceof String)) {
                // 记录 keys 元素类型错误。
                errors.add("keys_element_invalid");
            }
        }
        // 读取 size 字段值。
        Object size = data.get("size");
        // 判断 size 是否为数字类型，非数字时记录错误。
        if (!(size instanceof Number)) {
            // 记录 size 类型错误。
            errors.add("size_invalid");
        }
        // 读取 tool 字段值。
        Object tool = data.get("tool");
        // 判断 tool 是否为字符串，非字符串时记录错误。
        if (tool != null && !(tool instanceof String)) {
            // 记录 tool 类型错误。
            errors.add("tool_type_invalid");
        }
        // 返回默认结构校验错误列表。
        return errors;
    }

    private List<String> validateErrorData(Map<String, Object> data) {
        // 初始化错误列表容器。
        List<String> errors = new ArrayList<>();
        // 读取 category 字段值。
        Object category = data.get("category");
        // 判断 category 是否为空或空白，空时记录错误。
        if (!hasText(category)) {
            // 记录 category 缺失错误。
            errors.add("category_missing");
        }
        // 读取 message 字段值。
        Object message = data.get("message");
        // 判断 message 是否为空或空白，空时记录错误。
        if (!hasText(message)) {
            // 记录 message 缺失错误。
            errors.add("message_missing");
        }
        // 读取 rawSize 字段值。
        Object rawSize = data.get("rawSize");
        // 判断 rawSize 是否为数字类型，非数字时记录错误。
        if (rawSize != null && !(rawSize instanceof Number)) {
            // 记录 rawSize 类型错误。
            errors.add("raw_size_invalid");
        }
        // 读取 actionableNext 字段值。
        Object actionableNext = data.get("actionableNext");
        // 设计意图：仅在存在 actionableNext 时做类型与首元素校验，风险是无法覆盖所有元素。
        // 判断 actionableNext 是否存在。
        if (actionableNext != null) {
            // 判断 actionableNext 是否为列表，非列表时记录错误。
            if (!(actionableNext instanceof List<?> list)) {
                // 记录 actionableNext 类型错误。
                errors.add("actionable_next_invalid");
            } else if (!list.isEmpty()) {
                // 读取首个 actionableNext 元素。
                Object first = list.get(0);
                // 判断首个 actionableNext 元素是否为字符串，非字符串时记录错误。
                if (!(first instanceof String)) {
                    // 记录 actionableNext 元素类型错误。
                    errors.add("actionable_next_element_invalid");
                }
            }
        }
        // 返回错误结构校验错误列表。
        return errors;
    }

    private boolean hasText(Object value) {
        // 判断值是否为空，空时直接返回否。
        if (value == null) {
            // 返回否，表示无有效文本。
            return false;
        }
        // 将值转换为字符串。
        String text = String.valueOf(value);
        // 返回文本是否包含非空白字符。
        return !text.trim().isEmpty();
    }
}
