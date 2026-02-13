package com.example.agent.runtime.contract;

/**
 * 运行时输出契约键常量。
 *
 * <p>用途：集中维护运行时链路输出映射中的公共键，作为跨包稳定契约层。</p>
 * <p>边界：该类仅定义键常量，不承载业务逻辑。</p>
 */
public final class RuntimeOutputKeys {

    private RuntimeOutputKeys() {
    }

    /**
     * 工具名称（规范字段）。
     */
    public static final String TOOL_NAME = "toolName";
    /**
     * 工具名称（兼容字段）。
     */
    public static final String TOOL = "tool";

    /**
     * 原始输出引用键。
     */
    public static final String RAW_REF = "rawRef";
    /**
     * 引用集合。
     */
    public static final String REFS = "refs";

    /**
     * 原始结果容器（兼容字段）。
     */
    public static final String RAW_RESULT = "rawResult";
    /**
     * 结果容器（兼容字段）。
     */
    public static final String RESULT = "result";
    /**
     * 元信息容器。
     */
    public static final String META = "meta";
    /**
     * 决策容器。
     */
    public static final String DECISION = "decision";
    /**
     * 原始容器（兼容字段）。
     */
    public static final String RAW = "raw";
    /**
     * 数据字段（常见输出字段）。
     */
    public static final String DATA = "data";

    /**
     * 决策阶段 rawRef。
     */
    public static final String DECISION_RAW_REF = "decisionRawRef";
    /**
     * 摘要阶段 rawRef。
     */
    public static final String SUMMARY_RAW_REF = "summaryRawRef";
    /**
     * 工具阶段 rawRef。
     */
    public static final String TOOL_RAW_REF = "toolRawRef";
    /**
     * 模型阶段 rawRef。
     */
    public static final String MODEL_RAW_REF = "modelRawRef";

    /**
     * 输出摘要层。
     */
    public static final String OUTPUT_SUMMARY = "outputSummary";
    /**
     * 工具结果摘要层。
     */
    public static final String TOOL_RESULT_SUMMARY = "toolResultSummary";
    /**
     * 步骤摘要层。
     */
    public static final String STEP_SUMMARY = "stepSummary";
    /**
     * 输入摘要层。
     */
    public static final String INPUT_SUMMARY = "inputSummary";
    /**
     * 输入指纹层。
     */
    public static final String INPUT_DIGEST = "inputDigest";
    /**
     * 输出指纹层。
     */
    public static final String OUTPUT_DIGEST = "outputDigest";
    /**
     * 摘要容器字段（顶层）。
     */
    public static final String SUMMARY = "summary";
    /**
     * 语义摘要文本字段。
     */
    public static final String SUMMARY_TEXT = "text";
    /**
     * 语义摘要高亮字段。
     */
    public static final String SUMMARY_HIGHLIGHTS = "highlights";
    /**
     * 语义摘要未解决问题字段。
     */
    public static final String SUMMARY_OPEN_QUESTIONS = "openQuestions";
    /**
     * 语义摘要风险字段。
     */
    public static final String SUMMARY_RISKS = "risks";
    /**
     * 语义摘要来源引用字段。
     */
    public static final String SUMMARY_SOURCE_REFS = "sourceRefs";
    /**
     * 摘要来源引用类型字段。
     */
    public static final String SUMMARY_SOURCE_REF_TYPE = "type";
    /**
     * 摘要来源引用值字段。
     */
    public static final String SUMMARY_SOURCE_REF_VALUE = "value";
    /**
     * 摘要来源引用路径字段。
     */
    public static final String SUMMARY_SOURCE_REF_PATH = "path";
    /**
     * 摘要质量字段。
     */
    public static final String SUMMARY_QUALITY = "quality";
    /**
     * 摘要质量告警字段。
     */
    public static final String SUMMARY_WARNINGS = "warnings";
    /**
     * 摘要质量评分字段。
     */
    public static final String SUMMARY_QUALITY_SCORE = "score";
    /**
     * 摘要质量覆盖度字段。
     */
    public static final String SUMMARY_QUALITY_COVERAGE = "coverage";
    /**
     * 摘要质量一致性字段。
     */
    public static final String SUMMARY_QUALITY_COHERENCE = "coherence";
    /**
     * 摘要质量缺失字段列表。
     */
    public static final String SUMMARY_QUALITY_MISSING_FIELDS = "missingFields";
    /**
     * 截断标记字段。
     */
    public static final String TRUNCATED = "truncated";

    /**
     * 指纹字段：键数量。
     */
    public static final String KEY_COUNT = "keyCount";
    /**
     * 指纹字段：键列表。
     */
    public static final String KEYS = "keys";
    /**
     * 指纹字段：字符数。
     */
    public static final String CHAR_COUNT = "charCount";
}
