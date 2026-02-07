package com.example.agent.runtime.output;

import com.example.agent.runtime.contract.RuntimeOutputKeys;

/**
 * 运行时输出键常量。
 *
 * <p>用途：集中管理运行时链路中约定的 {@code Map<String, Object>} 输出键，避免散落的硬编码字符串导致不一致。
 * <p>注意：该类仅用于运行时内部的解析/封装权威点（例如 raw/summary/output DTO），业务消费方应优先使用显式字段或 DTO。
 */
public final class OutputKeys {

    private OutputKeys() {
    }

    /**
     * 工具名称（规范字段）。
     */
    public static final String TOOL_NAME = RuntimeOutputKeys.TOOL_NAME;
    /**
     * 工具名称（兼容字段）。
     *
     * <p>说明：历史上该字段可能为字符串（表示 toolName 别名），也可能为对象（例如包含 name/arguments 的工具调用载荷）。
     * 运行时解析时应优先使用 {@code toolName}，并在必要时做尽力兼容解析。
     */
    public static final String TOOL = RuntimeOutputKeys.TOOL;

    /**
     * 原始输出引用键。
     */
    public static final String RAW_REF = RuntimeOutputKeys.RAW_REF;
    /**
     * 引用集合。
     */
    public static final String REFS = RuntimeOutputKeys.REFS;

    /**
     * 原始结果容器（兼容字段）。
     */
    public static final String RAW_RESULT = RuntimeOutputKeys.RAW_RESULT;
    /**
     * 结果容器（兼容字段）。
     */
    public static final String RESULT = RuntimeOutputKeys.RESULT;
    /**
     * 原始容器（兼容字段）。
     */
    public static final String RAW = RuntimeOutputKeys.RAW;
    /**
     * 数据字段（常见输出字段）。
     */
    public static final String DATA = RuntimeOutputKeys.DATA;

    /**
     * 决策阶段 rawRef。
     */
    public static final String DECISION_RAW_REF = RuntimeOutputKeys.DECISION_RAW_REF;
    /**
     * 摘要阶段 rawRef。
     */
    public static final String SUMMARY_RAW_REF = RuntimeOutputKeys.SUMMARY_RAW_REF;
    /**
     * 工具阶段 rawRef。
     */
    public static final String TOOL_RAW_REF = RuntimeOutputKeys.TOOL_RAW_REF;
    /**
     * 模型阶段 rawRef。
     */
    public static final String MODEL_RAW_REF = RuntimeOutputKeys.MODEL_RAW_REF;

    /**
     * 输出摘要层。
     */
    public static final String OUTPUT_SUMMARY = RuntimeOutputKeys.OUTPUT_SUMMARY;
    /**
     * 工具结果摘要层。
     */
    public static final String TOOL_RESULT_SUMMARY = RuntimeOutputKeys.TOOL_RESULT_SUMMARY;
    /**
     * 步骤摘要层。
     */
    public static final String STEP_SUMMARY = RuntimeOutputKeys.STEP_SUMMARY;
    /**
     * 输入摘要层。
     */
    public static final String INPUT_SUMMARY = RuntimeOutputKeys.INPUT_SUMMARY;
    /**
     * 输入指纹层。
     */
    public static final String INPUT_DIGEST = RuntimeOutputKeys.INPUT_DIGEST;
    /**
     * 输出指纹层。
     */
    public static final String OUTPUT_DIGEST = RuntimeOutputKeys.OUTPUT_DIGEST;
    /**
     * 摘要文本字段。
     */
    public static final String SUMMARY = RuntimeOutputKeys.SUMMARY;
    /**
     * 截断标记字段。
     */
    public static final String TRUNCATED = RuntimeOutputKeys.TRUNCATED;

    /**
     * 指纹字段：键数量。
     */
    public static final String KEY_COUNT = RuntimeOutputKeys.KEY_COUNT;
    /**
     * 指纹字段：键列表。
     */
    public static final String KEYS = RuntimeOutputKeys.KEYS;
    /**
     * 指纹字段：字符数。
     */
    public static final String CHAR_COUNT = RuntimeOutputKeys.CHAR_COUNT;
}
