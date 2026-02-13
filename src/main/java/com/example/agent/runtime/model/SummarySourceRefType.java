package com.example.agent.runtime.model;

/**
 * 摘要来源引用类型。
 *
 * <p>用途：标识语义摘要引用的来源类型，便于追踪与审计。</p>
 * <p>输入：由摘要生成服务或结果装配器写入。</p>
 * <p>输出：在摘要输出协议中序列化为字符串码。</p>
 */
public enum SummarySourceRefType {

    /**
     * 原始输出引用。
     */
    RAW_REF("rawRef"),

    /**
     * 结构化结果路径。
     */
    RESULT_PATH("resultPath"),

    /**
     * 工具名称引用。
     */
    TOOL_REF("toolRef");

    /**
     * 类型编码。
     */
    private final String code;

    SummarySourceRefType(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    /**
     * 通过编码解析类型。
     *
     * @param code 类型编码
     * @return 类型枚举，解析失败返回 {@code null}
     */
    public static SummarySourceRefType fromCode(String code) {
        // 判断编码是否为空，空时直接返回空值。
        if (code == null || code.isBlank()) {
            // 返回空值，表示无法解析类型。
            return null;
        }
        // 标准化编码文本，避免大小写差异。
        String normalized = code.trim().toLowerCase();
        // 循环遍历所有类型，匹配编码。
        for (SummarySourceRefType type : values()) {
            // 判断编码是否匹配，匹配时返回类型。
            if (type.getCode().equalsIgnoreCase(normalized)) {
                // 返回匹配的类型枚举。
                return type;
            }
        }
        // 未命中时返回空值。
        return null;
    }
}
