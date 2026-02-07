package com.example.agent.runtime.output;

import com.example.agent.runtime.contract.RuntimeOutputFieldExtractor;
import java.util.Map;

/**
 * 运行时输出字段提取器。
 *
 * <p>用途：在少数权威点集中解析 {@code Map<String, Object>} 输出中的关键字段（toolName/rawRef/refs），避免重复实现导致不一致。
 * <p>边界：该类仅做“读取与规范化（trim/空白处理）”，不做裁剪、摘要生成等重量逻辑。
 */
public final class OutputFieldExtractor {

    private OutputFieldExtractor() {
    }

    /**
     * 解析工具名称。
     *
     * <p>约定：优先读取规范字段 {@code toolName}。
     * <p>兼容：当顶层 {@code tool} 为字符串时视为工具名别名；当顶层 {@code tool} 为对象时尝试读取 {@code tool.name}。</p>
     *
     * @param payload 输出映射
     * @return 工具名称或 {@code null}
     */
    public static String resolveToolName(Map<String, Object> payload) {
        return RuntimeOutputFieldExtractor.resolveToolName(payload);
    }

    /**
     * 解析原始引用键。
     *
     * <p>约定：优先读取顶层 {@code rawRef}，其次依次读取 {@code rawResult.rawRef}、{@code result.rawRef}、{@code raw.rawRef}。</p>
     *
     * @param payload 输出映射
     * @return 原始引用键或 {@code null}
     */
    public static String resolveRawRef(Map<String, Object> payload) {
        return RuntimeOutputFieldExtractor.resolveRawRef(payload);
    }

    /**
     * 解析引用集合。
     *
     * <p>约定：合并顶层 {@code refs}、顶层各类 *RawRef 字段，以及 {@code raw.refs}。</p>
     *
     * @param payload 输出映射
     * @return 引用集合（不可变视图）
     */
    public static Map<String, String> resolveRefs(Map<String, Object> payload) {
        return RuntimeOutputFieldExtractor.resolveRefs(payload);
    }
}
