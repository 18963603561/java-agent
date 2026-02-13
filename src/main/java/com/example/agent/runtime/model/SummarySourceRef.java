package com.example.agent.runtime.model;

/**
 * 语义摘要来源引用。
 *
 * <p>用途：描述摘要内容对应的来源，支持原始引用、结果路径与工具来源。</p>
 * <p>输入：摘要生成阶段构建。</p>
 * <p>输出：在摘要协议中序列化为 {@code type/value/path} 结构。</p>
 */
public class SummarySourceRef {

    /**
     * 来源类型。
     */
    private final SummarySourceRefType type;

    /**
     * 引用值（如 rawRef、工具名称）。
     */
    private final String value;

    /**
     * 引用路径（如 result.data.xxx）。
     */
    private final String path;

    /**
     * 构造来源引用对象。
     *
     * @param type 来源类型
     * @param value 引用值
     * @param path 引用路径
     */
    public SummarySourceRef(SummarySourceRefType type, String value, String path) {
        this.type = type;
        this.value = value;
        this.path = path;
    }

    /**
     * 创建原始引用来源。
     *
     * @param rawRef 原始引用
     * @return 来源引用对象
     */
    public static SummarySourceRef rawRef(String rawRef) {
        // 返回原始引用来源对象。
        return new SummarySourceRef(SummarySourceRefType.RAW_REF, rawRef, null);
    }

    /**
     * 创建结果路径来源。
     *
     * @param path 结果路径
     * @return 来源引用对象
     */
    public static SummarySourceRef resultPath(String path) {
        // 返回结果路径来源对象。
        return new SummarySourceRef(SummarySourceRefType.RESULT_PATH, null, path);
    }

    /**
     * 创建工具名称来源。
     *
     * @param toolName 工具名称
     * @return 来源引用对象
     */
    public static SummarySourceRef toolRef(String toolName) {
        // 返回工具来源对象。
        return new SummarySourceRef(SummarySourceRefType.TOOL_REF, toolName, null);
    }

    public SummarySourceRefType getType() {
        return type;
    }

    public String getValue() {
        return value;
    }

    public String getPath() {
        return path;
    }
}
