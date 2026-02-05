package com.example.agent.runtime.summary;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import com.example.agent.runtime.engine.StepRecord;

/**
 * 步骤输出摘要构建器，用于生成步骤输出的摘要层与指纹层数据。
 * 主要用于将复杂输出压缩为可观察的关键字段与样本，便于日志与审计场景使用。
 * 输入包含步骤元数据与原始输出，输出为摘要结构，可能为空。
 * 边界条件：未启用摘要时返回空结构；输出为空时返回占位摘要；字段超过阈值会被截断。
 * 注意事项：摘要仅保留有限信息，不应依赖其完整性进行业务判断。
 */
@Component
public class StepOutputSummaryBuilder {

    /**
     * 日志记录器。
     */
    private static final Logger log = LoggerFactory.getLogger(StepOutputSummaryBuilder.class);

    /**
     * 摘要配置，控制开关与截断阈值。
     */
    private final StepSummaryProperties properties;

    /**
     * 构造方法，注入摘要配置。
     *
     * @param properties 摘要配置
     */
    public StepOutputSummaryBuilder(StepSummaryProperties properties) {
        this.properties = properties;
    }

    /**
     * 判断是否启用摘要生成。
     * 当配置为空时返回否。
     *
     * @return 是否启用
     */
    public boolean isEnabled() {
        // 配置为空或开关关闭时视为未启用。
        return properties != null && properties.isEnable();
    }

    /**
     * 生成摘要字段，返回包含输出摘要、工具结果摘要、步骤摘要、输入摘要与指纹摘要的结构。
     * 当摘要开关关闭时直接返回空结构。
     * 当输出为空或内容过大时会使用占位与截断策略。
     *
     * @param record 步骤记录
     * @param stepInput 步骤输入（可选，用于覆盖 record 中的输入）
     * @param output 输出内容
     * @param toolName 工具名称（可选）
     * @param error 异常信息（可选）
     * @return 摘要结构
     */
    public Map<String, Object> build(StepRecord record,
                                     Map<String, Object> stepInput,
                                     Object output,
                                     String toolName,
                                     Object error) {
        if (!isEnabled()) {
            // 未启用摘要时直接返回空结果。
            return Collections.emptyMap();
        }

        String stepId = record != null ? record.getStepId() : null;
        String stepType = record != null ? record.getType() : null;
        String status = record != null && record.getStatus() != null ? record.getStatus().name() : null;
        Integer attempt = record != null ? record.getAttempt() : null;
        Map<String, Object> effectiveInput = stepInput != null ? stepInput : record != null ? record.getInput() : null;

        // 计算摘要限制与截断状态。
        SummaryLimits limits = SummaryLimits.from(properties);
        TruncationState truncation = new TruncationState();
        // 生成输出快照并解析工具名称。
        OutputSnapshot snapshot = buildSnapshot(output, limits, truncation);
        String resolvedToolName = resolveToolName(toolName, output);

        // 构建输出摘要层。
        Map<String, Object> outputSummary = new LinkedHashMap<>();
        putIfNotNull(outputSummary, "status", status);
        if (output == null) {
            // 输出为空时提供占位信息，避免摘要缺失。
            outputSummary.put("hasOutput", false);
            putIfNotNull(outputSummary, "stepId", stepId);
            putIfNotNull(outputSummary, "type", stepType);
            outputSummary.put("summary", "no output");
        }
        if (attempt != null) {
            outputSummary.put("attempt", attempt);
        }
        if (!snapshot.keys.isEmpty()) {
            outputSummary.put("keyFields", snapshot.keys);
        }
        if (StringUtils.hasText(snapshot.sample)) {
            outputSummary.put("sample", snapshot.sample);
        }
        // 拼装错误摘要，便于排查。
        String errorText = resolveErrorText(error, limits, truncation);
        if (StringUtils.hasText(errorText)) {
            outputSummary.put("error", errorText);
        }

        // 构建工具结果摘要层。
        Map<String, Object> toolResultSummary = new LinkedHashMap<>();
        if (StringUtils.hasText(resolvedToolName)) {
            toolResultSummary.put("tool", resolvedToolName);
        }
        if (!snapshot.keys.isEmpty()) {
            toolResultSummary.put("resultKeys", snapshot.keys);
        }
        if (StringUtils.hasText(snapshot.sample)) {
            toolResultSummary.put("sample", snapshot.sample);
        }

        // 构建步骤摘要层。
        Map<String, Object> stepSummary = new LinkedHashMap<>();
        putIfNotNull(stepSummary, "stepId", stepId);
        putIfNotNull(stepSummary, "type", stepType);
        putIfNotNull(stepSummary, "status", status);
        if (attempt != null) {
            stepSummary.put("attempt", attempt);
        }
        if (StringUtils.hasText(resolvedToolName)) {
            stepSummary.put("tool", resolvedToolName);
        }
        // 生成简短摘要文本。
        String summaryText = buildStepSummaryText(stepType, status, resolvedToolName, snapshot, limits, truncation);
        if (StringUtils.hasText(summaryText)) {
            stepSummary.put("summary", summaryText);
        }

        // 构建输入摘要层。
        TruncationState inputTruncation = new TruncationState();
        Map<String, Object> inputSummary = buildInputSummary(effectiveInput, resolvedToolName, limits, inputTruncation,
                stepInput != null ? "stepInput" : "record");
        OutputSnapshot inputSnapshot = buildSnapshot(effectiveInput, limits, inputTruncation);
        Map<String, Object> inputDigest = new LinkedHashMap<>();
        inputDigest.put("keyCount", inputSnapshot.keyCount);
        inputDigest.put("keys", inputSnapshot.keys);
        inputDigest.put("charCount", inputSnapshot.charCount);
        inputDigest.put("truncated", inputTruncation.truncated);

        // 构建指纹摘要层。
        Map<String, Object> outputDigest = new LinkedHashMap<>();
        outputDigest.put("keyCount", snapshot.keyCount);
        outputDigest.put("keys", snapshot.keys);
        outputDigest.put("charCount", snapshot.charCount);
        outputDigest.put("truncated", truncation.truncated);

        // 汇总所有摘要层级。
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("outputSummary", outputSummary);
        result.put("toolResultSummary", toolResultSummary);
        result.put("stepSummary", stepSummary);
        if (inputSummary != null && !inputSummary.isEmpty()) {
            result.put("inputSummary", inputSummary);
        }
        if (inputSnapshot.keyCount > 0 || inputSnapshot.charCount > 0) {
            result.put("inputDigest", inputDigest);
        }
        result.put("outputDigest", outputDigest);
        result.put("truncated", truncation.truncated);
        return result;
    }

    /**
     * 基于输出构建键快照与样本文本快照。
     * 输出不是映射结构时，仅生成样本文本快照。
     * 输出为空时返回空样本与零字符数。
     *
     * @param output 原始输出
     * @param limits 摘要限制
     * @param truncation 截断状态
     * @return 输出快照
     */
    private OutputSnapshot buildSnapshot(Object output, SummaryLimits limits, TruncationState truncation) {
        // 先解析键快照，确保关键字段可用。
        KeySnapshot keySnapshot = resolveKeys(output, limits, truncation);
        // 再构建样本文本快照，控制长度与截断。
        BoundedSnapshot snapshot = buildBoundedSnapshot(output, limits, truncation);
        String sample = StringUtils.hasText(snapshot.text)
                ? trimText(snapshot.text, limits.maxFieldChars, truncation)
                : null;
        return new OutputSnapshot(keySnapshot.keyCount, keySnapshot.keys, snapshot.charCount, sample);
    }

    /**
     * 解析输出中的字段键集合。
     * 仅对映射结构生效，非映射或为空时返回空集合。
     * 触发列表限制时会标记截断状态。
     *
     * @param output 原始输出
     * @param limits 摘要限制
     * @param truncation 截断状态
     * @return 键快照
     */
    private KeySnapshot resolveKeys(Object output, SummaryLimits limits, TruncationState truncation) {
        if (!(output instanceof Map<?, ?> map) || map.isEmpty()) {
            // 非映射或无数据时直接返回空键快照。
            return new KeySnapshot(0, Collections.emptyList());
        }
        List<String> keys = new ArrayList<>();
        int index = 0;
        int maxItems = limits.maxListItems;
        for (Object key : map.keySet()) {
            if (maxItems > 0 && index >= maxItems) {
                // 超过限制后停止采样并标记截断。
                truncation.markTruncated();
                break;
            }
            // 统一转为文本并做单字段截断。
            String keyText = key == null ? "null" : safeToString(key);
            keys.add(trimText(keyText, limits.maxFieldChars, truncation));
            index++;
        }
        return new KeySnapshot(map.size(), keys);
    }

    /**
     * 解析工具名称，优先使用显式入参。
     * 当入参缺失时尝试从输出映射中提取。
     *
     * @param toolName 工具名称
     * @param output 原始输出
     * @return 工具名称
     */
    private String resolveToolName(String toolName, Object output) {
        if (StringUtils.hasText(toolName)) {
            // 优先使用调用方传入的工具名称。
            return toolName;
        }
        if (output instanceof Map<?, ?> map) {
            // 从输出中尝试读取工具字段。
            Object value = map.get("tool");
            if (value == null) {
                value = map.get("toolName");
            }
            if (value != null) {
                return safeToString(value);
            }
        }
        return null;
    }

    /**
     * 解析异常文本摘要，限制长度并标记截断。
     * 异常为可抛出对象时优先拼接类名与消息。
     *
     * @param error 异常对象
     * @param limits 摘要限制
     * @param truncation 截断状态
     * @return 异常文本
     */
    private String resolveErrorText(Object error, SummaryLimits limits, TruncationState truncation) {
        if (error == null) {
            return null;
        }
        String text;
        if (error instanceof Throwable throwable) {
            // 组合异常类型与消息，便于定位问题。
            String message = throwable.getMessage();
            text = message == null
                    ? throwable.getClass().getSimpleName()
                    : throwable.getClass().getSimpleName() + ": " + message;
        } else {
            // 非异常对象统一走字符串化。
            text = safeToString(error);
        }
        return trimText(text, limits.maxFieldChars, truncation);
    }

    /**
     * 构建步骤摘要文本，按字段拼接并控制长度。
     * 当无可用字段时返回空值。
     *
     * @param stepType 步骤类型
     * @param status 步骤状态
     * @param toolName 工具名称
     * @param snapshot 输出快照
     * @param limits 摘要限制
     * @param truncation 截断状态
     * @return 摘要文本
     */
    private String buildStepSummaryText(String stepType,
                                        String status,
                                        String toolName,
                                        OutputSnapshot snapshot,
                                        SummaryLimits limits,
                                        TruncationState truncation) {
        List<String> parts = new ArrayList<>();
        if (StringUtils.hasText(stepType)) {
            // 追加步骤类型信息。
            parts.add("type=" + stepType);
        }
        if (StringUtils.hasText(status)) {
            // 追加步骤状态信息。
            parts.add("status=" + status);
        }
        if (StringUtils.hasText(toolName)) {
            // 追加工具名称信息。
            parts.add("tool=" + toolName);
        }
        if (snapshot.keyCount > 0) {
            // 追加关键字段数量。
            parts.add(String.format(Locale.ROOT, "keyCount=%d", snapshot.keyCount));
        }
        if (snapshot.charCount > 0) {
            // 追加样本文本长度。
            parts.add(String.format(Locale.ROOT, "charCount=%d", snapshot.charCount));
        }
        if (parts.isEmpty()) {
            // 无可输出字段时直接返回空值。
            return null;
        }
        // 生成拼接后的摘要并截断至单字段长度。
        return trimText(String.join(", ", parts), limits.maxFieldChars, truncation);
    }

    /**
     * 构建输入摘要，提取查询条件与工具参数等关键字段。
     *
     * @param input 步骤输入
     * @param toolName 工具名称
     * @param limits 摘要限制
     * @param truncation 截断状态
     * @param source 输入来源标记
     * @return 输入摘要
     */
    private Map<String, Object> buildInputSummary(Map<String, Object> input,
                                                  String toolName,
                                                  SummaryLimits limits,
                                                  TruncationState truncation,
                                                  String source) {
        if ((input == null || input.isEmpty()) && !StringUtils.hasText(toolName)) {
            return Collections.emptyMap();
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        String resolvedToolName = resolveToolName(toolName, input);
        if (StringUtils.hasText(resolvedToolName)) {
            summary.put("tool", resolvedToolName);
        }
        if (input != null && !input.isEmpty()) {
            putTextSummary(summary, "query", input.get("query"), limits, truncation);
            putTextSummary(summary, "question", input.get("question"), limits, truncation);
            putTextSummary(summary, "topic", input.get("topic"), limits, truncation);

            Object arguments = input.get("arguments");
            putStructuredSummary(summary, "arguments", arguments, limits, truncation);

            Object filters = input.containsKey("filters") ? input.get("filters")
                    : input.containsKey("filter") ? input.get("filter")
                    : input.get("conditions");
            putStructuredSummary(summary, "filters", filters, limits, truncation);

            Object timeRange = input.containsKey("timeRange") ? input.get("timeRange")
                    : input.containsKey("dateRange") ? input.get("dateRange")
                    : input.get("range");
            putStructuredSummary(summary, "timeRange", timeRange, limits, truncation);

            putTextSummary(summary, "from", input.get("from"), limits, truncation);
            putTextSummary(summary, "to", input.get("to"), limits, truncation);
            putTextSummary(summary, "startTime", input.get("startTime"), limits, truncation);
            putTextSummary(summary, "endTime", input.get("endTime"), limits, truncation);
        }
        if (summary.isEmpty()) {
            return Collections.emptyMap();
        }
        if (StringUtils.hasText(source)) {
            summary.put("source", source);
        }
        return summary;
    }

    /**
     * 追加文本类摘要字段，并控制最大长度。
     *
     * @param target 目标摘要
     * @param key 字段名
     * @param value 原始值
     * @param limits 摘要限制
     * @param truncation 截断状态
     */
    private void putTextSummary(Map<String, Object> target,
                                String key,
                                Object value,
                                SummaryLimits limits,
                                TruncationState truncation) {
        if (value == null) {
            return;
        }
        String text = value instanceof String textValue ? textValue : safeToString(value);
        if (!StringUtils.hasText(text)) {
            return;
        }
        target.put(key, trimText(text, limits.maxFieldChars, truncation));
    }

    /**
     * 追加结构化摘要字段，并控制嵌套深度与条目数量。
     *
     * @param target 目标摘要
     * @param key 字段名
     * @param value 原始值
     * @param limits 摘要限制
     * @param truncation 截断状态
     */
    private void putStructuredSummary(Map<String, Object> target,
                                      String key,
                                      Object value,
                                      SummaryLimits limits,
                                      TruncationState truncation) {
        if (value == null) {
            return;
        }
        Object sanitized = sanitizeInputValue(value, limits, truncation, 2,
                new java.util.IdentityHashMap<>());
        if (sanitized instanceof Map<?, ?> map && map.isEmpty()) {
            return;
        }
        if (sanitized instanceof List<?> list && list.isEmpty()) {
            return;
        }
        target.put(key, sanitized);
    }

    /**
     * 清理输入值，避免过深嵌套或过大集合进入摘要。
     *
     * @param value 原始值
     * @param limits 摘要限制
     * @param truncation 截断状态
     * @param depth 剩余深度
     * @param visited 访问记录
     * @return 清理后的值
     */
    private Object sanitizeInputValue(Object value,
                                      SummaryLimits limits,
                                      TruncationState truncation,
                                      int depth,
                                      java.util.IdentityHashMap<Object, Boolean> visited) {
        if (value == null) {
            return null;
        }
        if (depth <= 0) {
            return trimText(safeToString(value), limits.maxFieldChars, truncation);
        }
        if (value instanceof String text) {
            return trimText(text, limits.maxFieldChars, truncation);
        }
        if (value instanceof Number || value instanceof Boolean) {
            return value;
        }
        if (value instanceof Map<?, ?> map) {
            return sanitizeInputMap(map, limits, truncation, depth, visited);
        }
        if (value instanceof List<?> list) {
            return sanitizeInputList(list, limits, truncation, depth, visited);
        }
        if (value.getClass().isArray()) {
            return sanitizeInputArray(value, limits, truncation, depth, visited);
        }
        return trimText(safeToString(value), limits.maxFieldChars, truncation);
    }

    /**
     * 清理映射结构，限制条目数量并控制深度。
     *
     * @param map 映射对象
     * @param limits 摘要限制
     * @param truncation 截断状态
     * @param depth 剩余深度
     * @param visited 访问记录
     * @return 清理后的映射
     */
    private Map<String, Object> sanitizeInputMap(Map<?, ?> map,
                                                 SummaryLimits limits,
                                                 TruncationState truncation,
                                                 int depth,
                                                 java.util.IdentityHashMap<Object, Boolean> visited) {
        if (visited.put(map, Boolean.TRUE) != null) {
            truncation.markTruncated();
            return Map.of("value", "<circular>");
        }
        Map<String, Object> sanitized = new LinkedHashMap<>();
        int index = 0;
        int maxItems = limits.maxListItems;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (maxItems > 0 && index >= maxItems) {
                truncation.markTruncated();
                break;
            }
            String keyText = entry.getKey() == null ? "null" : safeToString(entry.getKey());
            keyText = trimText(keyText, limits.maxFieldChars, truncation);
            Object cleaned = sanitizeInputValue(entry.getValue(), limits, truncation, depth - 1, visited);
            sanitized.put(keyText, cleaned);
            index++;
        }
        visited.remove(map);
        return sanitized;
    }

    /**
     * 清理列表结构，限制条目数量并控制深度。
     *
     * @param list 列表对象
     * @param limits 摘要限制
     * @param truncation 截断状态
     * @param depth 剩余深度
     * @param visited 访问记录
     * @return 清理后的列表
     */
    private List<Object> sanitizeInputList(List<?> list,
                                           SummaryLimits limits,
                                           TruncationState truncation,
                                           int depth,
                                           java.util.IdentityHashMap<Object, Boolean> visited) {
        if (visited.put(list, Boolean.TRUE) != null) {
            truncation.markTruncated();
            return List.of("<circular>");
        }
        List<Object> sanitized = new ArrayList<>();
        int index = 0;
        int maxItems = limits.maxListItems;
        for (Object item : list) {
            if (maxItems > 0 && index >= maxItems) {
                truncation.markTruncated();
                break;
            }
            sanitized.add(sanitizeInputValue(item, limits, truncation, depth - 1, visited));
            index++;
        }
        visited.remove(list);
        return sanitized;
    }

    /**
     * 清理数组结构，限制条目数量并控制深度。
     *
     * @param array 数组对象
     * @param limits 摘要限制
     * @param truncation 截断状态
     * @param depth 剩余深度
     * @param visited 访问记录
     * @return 清理后的列表
     */
    private List<Object> sanitizeInputArray(Object array,
                                            SummaryLimits limits,
                                            TruncationState truncation,
                                            int depth,
                                            java.util.IdentityHashMap<Object, Boolean> visited) {
        if (visited.put(array, Boolean.TRUE) != null) {
            truncation.markTruncated();
            return List.of("<circular>");
        }
        int length = java.lang.reflect.Array.getLength(array);
        int maxItems = limits.maxListItems;
        List<Object> sanitized = new ArrayList<>();
        for (int i = 0; i < length; i++) {
            if (maxItems > 0 && i >= maxItems) {
                truncation.markTruncated();
                break;
            }
            Object item = java.lang.reflect.Array.get(array, i);
            sanitized.add(sanitizeInputValue(item, limits, truncation, depth - 1, visited));
        }
        visited.remove(array);
        return sanitized;
    }


    /**
     * 构建受限长度的样本文本快照。
     * 该过程会递归遍历输出结构，并在超限时停止。
     *
     * @param output 原始输出
     * @param limits 摘要限制
     * @param truncation 截断状态
     * @return 有界样本文本快照
     */
    private BoundedSnapshot buildBoundedSnapshot(Object output, SummaryLimits limits, TruncationState truncation) {
        if (output == null) {
            // 输出为空时返回空文本快照。
            return new BoundedSnapshot("", 0);
        }
        // 使用写入器控制总字符数。
        SnapshotWriter writer = new SnapshotWriter(limits.maxChars, truncation);
        // 使用对象身份跟踪访问，避免循环引用。
        java.util.IdentityHashMap<Object, Boolean> visited = new java.util.IdentityHashMap<>();
        appendValue(output, writer, limits, truncation, visited);
        return new BoundedSnapshot(writer.toString(), writer.length());
    }

    /**
     * 追加任意类型值到样本文本中。
     * 根据值类型走不同分支处理，并遵守截断与循环检测。
     *
     * @param value 当前值
     * @param writer 文本写入器
     * @param limits 摘要限制
     * @param truncation 截断状态
     * @param visited 访问记录
     */
    private void appendValue(Object value,
                             SnapshotWriter writer,
                             SummaryLimits limits,
                             TruncationState truncation,
                             java.util.IdentityHashMap<Object, Boolean> visited) {
        if (writer.isStopped()) {
            // 已达到写入上限时提前退出。
            return;
        }
        if (value == null) {
            // 空值直接写入占位文本。
            writer.append("null");
            return;
        }
        if (value instanceof String text) {
            // 字符串按字段长度限制写入。
            writer.append(trimText(text, limits.maxFieldChars, truncation));
            return;
        }
        if (value instanceof Number || value instanceof Boolean) {
            // 数值与布尔值统一转为文本处理。
            writer.append(trimText(safeToString(value), limits.maxFieldChars, truncation));
            return;
        }
        if (value instanceof Map<?, ?> map) {
            // 映射结构递归展开。
            appendMap(map, writer, limits, truncation, visited);
            return;
        }
        if (value instanceof List<?> list) {
            // 列表结构递归展开。
            appendList(list, writer, limits, truncation, visited);
            return;
        }
        if (value.getClass().isArray()) {
            // 数组结构递归展开。
            appendArray(value, writer, limits, truncation, visited);
            return;
        }
        // 其他对象走安全字符串化。
        writer.append(trimText(safeToString(value), limits.maxFieldChars, truncation));
    }

    /**
     * 追加映射结构到样本文本中。
     * 支持循环检测与最大条目限制。
     *
     * @param map 映射对象
     * @param writer 文本写入器
     * @param limits 摘要限制
     * @param truncation 截断状态
     * @param visited 访问记录
     */
    private void appendMap(Map<?, ?> map,
                           SnapshotWriter writer,
                           SummaryLimits limits,
                           TruncationState truncation,
                           java.util.IdentityHashMap<Object, Boolean> visited) {
        if (writer.isStopped()) {
            // 写入已停止时直接返回。
            return;
        }
        if (visited.put(map, Boolean.TRUE) != null) {
            // 检测到循环引用时写入占位并标记截断。
            writer.append("<circular>");
            truncation.markTruncated();
            return;
        }
        writer.append("{");
        int index = 0;
        int maxItems = limits.maxListItems;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (writer.isStopped()) {
                break;
            }
            if (maxItems > 0 && index >= maxItems) {
                // 达到最大条目后停止采样。
                truncation.markTruncated();
                break;
            }
            if (index > 0) {
                writer.append(",");
            }
            // 先写入键，再递归写入值。
            String keyText = entry.getKey() == null ? "null" : safeToString(entry.getKey());
            writer.append(trimText(keyText, limits.maxFieldChars, truncation));
            writer.append(":");
            appendValue(entry.getValue(), writer, limits, truncation, visited);
            index++;
        }
        writer.append("}");
        visited.remove(map);
    }

    /**
     * 追加列表结构到样本文本中。
     * 支持循环检测与最大条目限制。
     *
     * @param list 列表对象
     * @param writer 文本写入器
     * @param limits 摘要限制
     * @param truncation 截断状态
     * @param visited 访问记录
     */
    private void appendList(List<?> list,
                            SnapshotWriter writer,
                            SummaryLimits limits,
                            TruncationState truncation,
                            java.util.IdentityHashMap<Object, Boolean> visited) {
        if (writer.isStopped()) {
            // 写入已停止时直接返回。
            return;
        }
        if (visited.put(list, Boolean.TRUE) != null) {
            // 检测到循环引用时写入占位并标记截断。
            writer.append("<circular>");
            truncation.markTruncated();
            return;
        }
        writer.append("[");
        int index = 0;
        int maxItems = limits.maxListItems;
        for (Object item : list) {
            if (writer.isStopped()) {
                break;
            }
            if (maxItems > 0 && index >= maxItems) {
                // 达到最大条目后停止采样。
                truncation.markTruncated();
                break;
            }
            if (index > 0) {
                writer.append(",");
            }
            // 递归写入列表元素。
            appendValue(item, writer, limits, truncation, visited);
            index++;
        }
        writer.append("]");
        visited.remove(list);
    }

    /**
     * 追加数组结构到样本文本中。
     * 支持循环检测与最大条目限制。
     *
     * @param array 数组对象
     * @param writer 文本写入器
     * @param limits 摘要限制
     * @param truncation 截断状态
     * @param visited 访问记录
     */
    private void appendArray(Object array,
                             SnapshotWriter writer,
                             SummaryLimits limits,
                             TruncationState truncation,
                             java.util.IdentityHashMap<Object, Boolean> visited) {
        if (writer.isStopped()) {
            // 写入已停止时直接返回。
            return;
        }
        if (visited.put(array, Boolean.TRUE) != null) {
            // 检测到循环引用时写入占位并标记截断。
            writer.append("<cycle>");
            truncation.markTruncated();
            return;
        }
        writer.append("[");
        int length = java.lang.reflect.Array.getLength(array);
        int maxItems = limits.maxListItems;
        for (int i = 0; i < length; i++) {
            if (writer.isStopped()) {
                break;
            }
            if (maxItems > 0 && i >= maxItems) {
                // 达到最大条目后停止采样。
                truncation.markTruncated();
                break;
            }
            if (i > 0) {
                writer.append(",");
            }
            Object item = java.lang.reflect.Array.get(array, i);
            // 递归写入数组元素。
            appendValue(item, writer, limits, truncation, visited);
        }
        writer.append("]");
        visited.remove(array);
    }

    /**
     * 按最大长度截断文本，并标记截断状态。
     *
     * @param text 原始文本
     * @param maxChars 最大字符数
     * @param truncation 截断状态
     * @return 截断后的文本
     */
    private String trimText(String text, int maxChars, TruncationState truncation) {
        if (text == null) {
            return null;
        }
        if (maxChars > 0 && text.length() > maxChars) {
            // 超出长度上限时截断并标记。
            truncation.markTruncated();
            return text.substring(0, maxChars);
        }
        return text;
    }

    /**
     * 非空值才写入目标映射，避免产生无意义字段。
     *
     * @param target 目标映射
     * @param key 字段名称
     * @param value 字段值
     */
    private void putIfNotNull(Map<String, Object> target, String key, Object value) {
        if (value != null) {
            // 仅在值存在时写入，保持摘要简洁。
            target.put(key, value);
        }
    }

    /**
     * 安全地将对象转换为字符串。
     * 转换异常时会返回占位文本并记录调试日志。
     *
     * @param value 待转换对象
     * @return 字符串结果
     */
    private String safeToString(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return String.valueOf(value);
        } catch (Exception ex) {
            // 异常捕获：记录上下文并按当前策略处理。
            String className = value.getClass().getSimpleName();
            if (!StringUtils.hasText(className)) {
                className = value.getClass().getName();
            }
            if (log.isDebugEnabled()) {
                log.debug("toString 失败, className={}", value.getClass().getName(), ex);
            }
            return "<toString_error:" + className + ">";
        }
    }

    /**
     * 摘要限制的只读视图，用于约束输出长度与采样数量。
     */
    private static final class SummaryLimits {
        /**
         * 摘要允许的最大总字符数。
         */
        private final int maxChars;
        /**
         * 列表或映射允许采样的最大条目数。
         */
        private final int maxListItems;
        /**
         * 单字段允许的最大字符数。
         */
        private final int maxFieldChars;

        /**
         * 构造摘要限制对象。
         *
         * @param maxChars 最大总字符数
         * @param maxListItems 最大条目数
         * @param maxFieldChars 单字段最大字符数
         */
        private SummaryLimits(int maxChars, int maxListItems, int maxFieldChars) {
            // 保存各类限制值。
            this.maxChars = maxChars;
            this.maxListItems = maxListItems;
            this.maxFieldChars = maxFieldChars;
        }

        /**
         * 从配置对象解析限制，配置为空时返回全零限制。
         *
         * @param properties 配置对象
         * @return 限制对象
         */
        private static SummaryLimits from(StepSummaryProperties properties) {
            if (properties == null) {
                // 无配置时使用全零限制，交由上层处理。
                return new SummaryLimits(0, 0, 0);
            }
            return new SummaryLimits(properties.getMaxChars(),
                    properties.getMaxListItems(),
                    properties.getMaxFieldChars());
        }
    }

    /**
     * 输出快照，聚合关键字段与样本文本信息。
     */
    private static final class OutputSnapshot {
        /**
         * 输出键数量。
         */
        private final int keyCount;
        /**
         * 关键字段集合。
         */
        private final List<String> keys;
        /**
         * 样本文本长度。
         */
        private final int charCount;
        /**
         * 样本文本内容。
         */
        private final String sample;

        /**
         * 构造输出快照。
         *
         * @param keyCount 键数量
         * @param keys 关键字段集合
         * @param charCount 样本文本长度
         * @param sample 样本文本
         */
        private OutputSnapshot(int keyCount, List<String> keys, int charCount, String sample) {
            // 保存输出快照各字段。
            this.keyCount = keyCount;
            this.keys = keys;
            this.charCount = charCount;
            this.sample = sample;
        }
    }

    /**
     * 键快照，仅包含键数量与键列表。
     */
    private static final class KeySnapshot {
        /**
         * 键数量。
         */
        private final int keyCount;
        /**
         * 键列表。
         */
        private final List<String> keys;

        /**
         * 构造键快照。
         *
         * @param keyCount 键数量
         * @param keys 键列表
         */
        private KeySnapshot(int keyCount, List<String> keys) {
            // 保存键快照字段。
            this.keyCount = keyCount;
            this.keys = keys;
        }
    }

    /**
     * 截断状态记录器，用于标记摘要是否发生截断。
     */
    private static final class TruncationState {
        /**
         * 是否已经发生截断。
         */
        private boolean truncated;

        /**
         * 标记截断发生。
         */
        private void markTruncated() {
            // 记录已发生截断。
            this.truncated = true;
        }
    }

    /**
     * 有界样本文本快照。
     */
    private static final class BoundedSnapshot {
        /**
         * 样本文本内容。
         */
        private final String text;
        /**
         * 样本文本长度。
         */
        private final int charCount;

        /**
         * 构造有界样本文本快照。
         *
         * @param text 文本内容
         * @param charCount 文本长度
         */
        private BoundedSnapshot(String text, int charCount) {
            // 保存样本文本与长度。
            this.text = text;
            this.charCount = charCount;
        }
    }

    /**
     * 样本文本写入器，负责控制总长度并更新截断状态。
     */
    private static final class SnapshotWriter {
        /**
         * 文本缓冲区。
         */
        private final StringBuilder builder = new StringBuilder();
        /**
         * 最大可写字符数。
         */
        private final int maxChars;
        /**
         * 截断状态记录。
         */
        private final TruncationState truncation;
        /**
         * 是否已停止写入。
         */
        private boolean stopped;

        /**
         * 构造样本文本写入器。
         *
         * @param maxChars 最大可写字符数
         * @param truncation 截断状态记录
         */
        private SnapshotWriter(int maxChars, TruncationState truncation) {
            // 保存写入限制与截断状态。
            this.maxChars = maxChars;
            this.truncation = truncation;
        }

        /**
         * 追加文本到缓冲区，必要时触发截断停止。
         *
         * @param text 待追加文本fv
         */
        private void append(String text) {
            if (stopped || text == null) {
                // 已停止或空文本时不再处理。
                return;
            }
            if (maxChars > 0) {
                int remaining = maxChars - builder.length();
                if (remaining <= 0) {
                    // 无剩余空间时直接停止。
                    truncation.markTruncated();
                    stopped = true;
                    return;
                }
                if (text.length() > remaining) {
                    // 文本超出剩余空间时截断并停止。
                    builder.append(text, 0, remaining);
                    truncation.markTruncated();
                    stopped = true;
                    return;
                }
            }
            builder.append(text);
        }

        /**
         * 判断是否已停止写入。
         *
         * @return 是否停止
         */
        private boolean isStopped() {
            // 直接返回停止标记。
            return stopped;
        }

        /**
         * 获取当前缓冲区长度。
         *
         * @return 文本长度
         */
        private int length() {
            // 返回当前缓冲区长度。
            return builder.length();
        }

        /**
         * 获取当前缓冲区文本内容。
         *
         * @return 当前文本
         */
        @Override
        public String toString() {
            return builder.toString();
        }
    }
}
