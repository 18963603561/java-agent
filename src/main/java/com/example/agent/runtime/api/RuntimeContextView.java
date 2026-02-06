package com.example.agent.runtime.api;

import com.example.agent.runtime.output.OutputFieldExtractor;
import java.util.Map;

/**
 * 运行时上下文只读视图。
 *
 * <p>用途：为跨包消费方提供稳定的读取方式，避免直接散落 {@code Map.get(...)} 之类的硬编码键访问。
 * <p>输入：上下文映射（可能来自提示词中的 {@code *_CONTEXT_JSON}，也可能来自运行时链路上下文）。
 * <p>输出：用于决策/规划等场景的关键字段（例如 toolName）。
 * <p>边界：该视图只做“读取与规范化”，不做业务逻辑判断。
 */
public final class RuntimeContextView {

    private final Map<String, Object> context;

    private RuntimeContextView(Map<String, Object> context) {
        this.context = context == null ? Map.of() : context;
    }

    /**
     * 创建上下文视图。
     *
     * @param context 上下文映射
     * @return 上下文视图
     */
    public static RuntimeContextView of(Map<String, Object> context) {
        return new RuntimeContextView(context);
    }

    /**
     * 解析工具名称。
     *
     * <p>优先从顶层解析；若顶层缺失则尝试从内层 {@code context} 字段中解析。</p>
     *
     * @return 工具名称或 {@code null}
     */
    public String resolveToolName() {
        String toolName = OutputFieldExtractor.resolveToolName(context);
        if (toolName != null) {
            return toolName;
        }
        Object inner = context.get("context");
        if (inner instanceof Map<?, ?> map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> typed = (Map<String, Object>) map;
            return OutputFieldExtractor.resolveToolName(typed);
        }
        return null;
    }
}
