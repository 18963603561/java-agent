package com.example.agent.runtime.structured;

import java.util.List;

/**
 * 结构化结果校验结论。
 *
 * <p>用途：统一承载校验是否通过、错误与警告信息，供主流程判断是否降级。</p>
 */
public class StructuredValidationResult {

    /**
     * 是否通过校验。
     */
    private final boolean valid;

    /**
     * 校验错误列表。
     */
    private final List<String> errors;

    /**
     * 校验警告列表。
     */
    private final List<String> warnings;

    private StructuredValidationResult(boolean valid, List<String> errors, List<String> warnings) {
        this.valid = valid;
        // 归一化错误列表，确保返回不可变集合。
        this.errors = errors == null ? List.of() : List.copyOf(errors);
        // 归一化警告列表，确保返回不可变集合。
        this.warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }

    /**
     * 构建通过校验的结果。
     *
     * @return 校验结论
     */
    public static StructuredValidationResult valid() {
        // 构建无错误无警告的校验结论。
        return new StructuredValidationResult(true, List.of(), List.of());
    }

    /**
     * 构建带警告的通过结果。
     *
     * @param warnings 警告列表
     * @return 校验结论
     */
    public static StructuredValidationResult validWithWarnings(List<String> warnings) {
        // 构建包含警告的通过结论。
        return new StructuredValidationResult(true, List.of(), warnings);
    }

    /**
     * 构建失败结果。
     *
     * @param errors 错误列表
     * @return 校验结论
     */
    public static StructuredValidationResult invalid(List<String> errors) {
        // 构建包含错误的失败结论。
        return new StructuredValidationResult(false, errors, List.of());
    }

    /**
     * 构建失败且带警告的结果。
     *
     * @param errors 错误列表
     * @param warnings 警告列表
     * @return 校验结论
     */
    public static StructuredValidationResult invalid(List<String> errors, List<String> warnings) {
        // 构建包含错误与警告的失败结论。
        return new StructuredValidationResult(false, errors, warnings);
    }

    public boolean isValid() {
        return valid;
    }

    public List<String> getErrors() {
        return errors;
    }

    public List<String> getWarnings() {
        return warnings;
    }
}
