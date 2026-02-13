package com.example.agent.runtime.summary;

/**
 * 语义摘要场景标识。
 *
 * <p>用途：统一描述摘要生成场景，支持按场景配置摘要策略。</p>
 * <p>输入：由场景解析器根据步骤上下文推导。</p>
 * <p>输出：作为摘要策略与预算的匹配键。</p>
 */
public final class SemanticSummaryScenario {

    /**
     * 默认场景编码。
     */
    private static final String DEFAULT_CODE = "default";

    /**
     * 默认场景。
     */
    public static final SemanticSummaryScenario DEFAULT = new SemanticSummaryScenario(DEFAULT_CODE);

    /**
     * 场景编码。
     */
    private final String code;

    private SemanticSummaryScenario(String code) {
        this.code = normalize(code);
    }

    /**
     * 构建场景对象。
     *
     * @param code 场景编码
     * @return 场景对象
     */
    public static SemanticSummaryScenario of(String code) {
        // 判断编码是否为空，空时返回默认场景。
        if (code == null || code.isBlank()) {
            // 返回默认场景对象。
            return DEFAULT;
        }
        // 返回新构建的场景对象。
        return new SemanticSummaryScenario(code);
    }

    public String getCode() {
        return code;
    }

    private static String normalize(String code) {
        // 判断编码是否为空，空时返回默认编码。
        if (code == null || code.isBlank()) {
            // 返回默认编码。
            return DEFAULT_CODE;
        }
        // 返回标准化后的编码文本。
        return code.trim().toLowerCase();
    }
}
