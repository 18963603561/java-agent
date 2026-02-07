package com.example.agent.runtime.llm;

import com.example.agent.runtime.output.OutputFieldExtractor;
import com.example.agent.runtime.output.OutputKeys;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 工具输出组装服务。
 *
 * <p>用途：统一构建 answer/tool_call 输出结构，并补齐标准字段与引用集合。
 * <p>输入：决策结果、工具调用结果、工具元信息与引用信息。
 * <p>输出：标准化输出映射。
 * <p>边界：输出映射允许动态字段，但核心字段由本服务统一兜底。
 */
@Service
public class ToolOutputAssembler {

    /**
     * answer 模式。
     */
    public static final String MODE_ANSWER = "answer";

    /**
     * tool_call 模式。
     */
    public static final String MODE_TOOL_CALL = "tool_call";

    /**
     * 工具摘要默认置信度（成功）。
     */
    private static final double TOOL_SUMMARY_CONFIDENCE_SUCCESS = 0.6;

    /**
     * 工具摘要默认置信度（失败）。
     */
    private static final double TOOL_SUMMARY_CONFIDENCE_FAILED = 0.2;

    /**
     * 构建直接回答输出。
     *
     * @param decision 决策结果
     * @param raw 原始文本
     * @param source 来源
     * @param rawRef 原始引用
     * @param decisionService 决策服务
     * @return 输出
     */
    public Map<String, Object> buildAnswerOutput(Map<String, Object> decision,
                                                 String raw,
                                                 String source,
                                                 String rawRef,
                                                 LlmDecisionService decisionService) {
        Map<String, Object> output = new HashMap<>();
        String answer = decisionService.readString(decision, "answer");
        if (!StringUtils.hasText(answer)) {
            answer = StringUtils.hasText(raw) ? raw : "no_response";
        }
        output.put("mode", MODE_ANSWER);
        output.put("answer", answer);
        output.put("reason", decisionService.readString(decision, "reason"));
        output.put("confidence", decisionService.readNumber(decision, "confidence", 0.5));
        output.put("source", source);
        if (StringUtils.hasText(rawRef)) {
            output.put(OutputKeys.RAW_REF, rawRef);
            mergeRef(output, OutputKeys.MODEL_RAW_REF, rawRef);
        }
        return output;
    }

    /**
     * 构建工具失败输出。
     *
     * @param decision 决策结果
     * @param message 错误消息
     * @param errorCode 错误码
     * @param source 来源
     * @param rawRef 决策原始引用
     * @return 输出
     */
    public Map<String, Object> buildToolFailureOutput(Map<String, Object> decision,
                                                      String message,
                                                      String errorCode,
                                                      String source,
                                                      String rawRef) {
        Map<String, Object> output = new HashMap<>();
        output.put("mode", MODE_TOOL_CALL);
        output.put("answer", message);
        output.put("highlights", message);
        output.put("confidence", 0.1);
        output.put("toolErrorCode", errorCode);
        if (decision != null) {
            output.put("toolDecision", decision);
        }
        output.put("source", source);
        if (StringUtils.hasText(rawRef)) {
            output.put(OutputKeys.RAW_REF, rawRef);
            mergeRef(output, OutputKeys.DECISION_RAW_REF, rawRef);
        }
        return output;
    }

    /**
     * 构建工具输出。
     *
     * @param summaryMode 摘要模式
     * @param toolName 工具名称
     * @param toolArguments 工具参数
     * @param toolResult 工具结果
     * @param source 来源
     * @param summaryService 摘要服务
     * @return 输出
     */
    public Map<String, Object> buildToolOutput(LlmStepService.ToolSummaryMode summaryMode,
                                               String toolName,
                                               Map<String, Object> toolArguments,
                                               ToolCallOrchestrator.ToolCallOutcome toolResult,
                                               String source,
                                               ToolSummaryService summaryService) {
        Map<String, Object> result = toolResult != null && toolResult.getResult() != null ? toolResult.getResult() : Map.of();
        String status = toolResult != null ? toolResult.getStatus() : ToolCallOrchestrator.TOOL_STATUS_FAILED;
        String errorCode = toolResult != null ? toolResult.getErrorCode() : null;
        String errorMessage = toolResult != null ? toolResult.getErrorMessage() : null;
        return buildToolOutput(summaryMode, toolName, toolArguments, status, errorCode, errorMessage,
                result, source, OutputFieldExtractor.resolveRawRef(result), summaryService);
    }

    /**
     * 构建工具输出（细粒度参数版本）。
     *
     * @param summaryMode 摘要模式
     * @param toolName 工具名称
     * @param toolArguments 工具参数
     * @param toolStatus 工具状态
     * @param errorCode 错误码
     * @param errorMessage 错误消息
     * @param toolResult 工具结果
     * @param source 来源
     * @param toolRawRef 工具原始引用
     * @param summaryService 摘要服务
     * @return 输出
     */
    public Map<String, Object> buildToolOutput(LlmStepService.ToolSummaryMode summaryMode,
                                               String toolName,
                                               Map<String, Object> toolArguments,
                                               String toolStatus,
                                               String errorCode,
                                               String errorMessage,
                                               Map<String, Object> toolResult,
                                               String source,
                                               String toolRawRef,
                                               ToolSummaryService summaryService) {
        Map<String, Object> output = new HashMap<>();
        Map<String, Object> safeResult = toolResult == null ? Map.of() : toolResult;
        boolean success = ToolCallOrchestrator.TOOL_STATUS_SUCCESS.equals(toolStatus);
        String answer = resolveAnswerFromResult(safeResult);
        if (!StringUtils.hasText(answer)) {
            answer = summaryMode == LlmStepService.ToolSummaryMode.TEMPLATE
                    ? summaryService.buildTemplateAnswer(success, safeResult)
                    : summaryService.buildDefaultAnswer(success, safeResult);
        }
        String highlights = summaryMode == LlmStepService.ToolSummaryMode.TEMPLATE
                ? summaryService.buildTemplateHighlights(safeResult)
                : summaryService.buildDefaultHighlights(success, errorCode);
        double confidence = success ? TOOL_SUMMARY_CONFIDENCE_SUCCESS : TOOL_SUMMARY_CONFIDENCE_FAILED;

        output.put("mode", MODE_TOOL_CALL);
        output.put("answer", answer);
        output.put("highlights", highlights);
        output.put("confidence", confidence);
        output.put("toolStatus", toolStatus);
        if (StringUtils.hasText(toolName)) {
            output.putIfAbsent(OutputKeys.TOOL_NAME, toolName);
        }
        if (errorCode != null) {
            output.put("toolErrorCode", errorCode);
        }
        if (errorMessage != null) {
            output.put("toolErrorMessage", errorMessage);
        }
        Map<String, Object> toolPayload = new HashMap<>();
        toolPayload.put("name", toolName);
        toolPayload.put("arguments", toolArguments == null ? Map.of() : toolArguments);
        output.put("tool", toolPayload);
        output.put(OutputKeys.RAW_RESULT, safeResult);
        output.put("source", source);
        output.put("evidence", List.of());
        if (StringUtils.hasText(toolRawRef)) {
            output.put(OutputKeys.RAW_REF, toolRawRef);
            mergeRef(output, OutputKeys.TOOL_RAW_REF, toolRawRef);
        }
        return output;
    }

    /**
     * 补齐工具输出默认字段。
     *
     * @param output 输出
     * @param toolName 工具名
     * @param toolArguments 参数
     * @param toolResult 结果
     * @param source 来源
     * @param decisionRawRef 决策引用
     * @param summaryRawRef 摘要引用
     * @param summaryService 摘要服务
     */
    public void applyToolOutputDefaults(Map<String, Object> output,
                                        String toolName,
                                        Map<String, Object> toolArguments,
                                        ToolCallOrchestrator.ToolCallOutcome toolResult,
                                        String source,
                                        String decisionRawRef,
                                        String summaryRawRef,
                                        ToolSummaryService summaryService) {
        if (output == null) {
            return;
        }
        Map<String, Object> safeResult = toolResult != null && toolResult.getResult() != null ? toolResult.getResult() : Map.of();
        String status = toolResult != null ? toolResult.getStatus() : ToolCallOrchestrator.TOOL_STATUS_FAILED;
        boolean success = ToolCallOrchestrator.TOOL_STATUS_SUCCESS.equals(status);
        String toolRawRef = OutputFieldExtractor.resolveRawRef(safeResult);

        output.putIfAbsent("mode", MODE_TOOL_CALL);
        output.putIfAbsent("toolStatus", status);
        if (StringUtils.hasText(toolName)) {
            output.putIfAbsent(OutputKeys.TOOL_NAME, toolName);
        }
        if (!output.containsKey("tool")) {
            Map<String, Object> toolPayload = new HashMap<>();
            toolPayload.put("name", toolName);
            toolPayload.put("arguments", toolArguments == null ? Map.of() : toolArguments);
            output.put("tool", toolPayload);
        }
        output.putIfAbsent(OutputKeys.RAW_RESULT, safeResult);
        if (!output.containsKey("answer")) {
            output.put("answer", summaryService.buildDefaultAnswer(success, safeResult));
        }
        if (!output.containsKey("highlights")) {
            output.put("highlights", summaryService.buildDefaultHighlights(success,
                    toolResult != null ? toolResult.getErrorCode() : null));
        }
        if (!output.containsKey("confidence")) {
            output.put("confidence", success ? TOOL_SUMMARY_CONFIDENCE_SUCCESS : TOOL_SUMMARY_CONFIDENCE_FAILED);
        }
        if (toolResult != null && toolResult.getErrorCode() != null) {
            output.putIfAbsent("toolErrorCode", toolResult.getErrorCode());
        }
        if (toolResult != null && toolResult.getErrorMessage() != null) {
            output.putIfAbsent("toolErrorMessage", toolResult.getErrorMessage());
        }
        output.putIfAbsent("source", source);
        output.putIfAbsent("evidence", List.of());

        if (StringUtils.hasText(toolRawRef)) {
            output.putIfAbsent(OutputKeys.RAW_REF, toolRawRef);
            mergeRef(output, OutputKeys.TOOL_RAW_REF, toolRawRef);
        }
        if (StringUtils.hasText(decisionRawRef)) {
            mergeRef(output, OutputKeys.DECISION_RAW_REF, decisionRawRef);
        }
        if (StringUtils.hasText(summaryRawRef)) {
            mergeRef(output, OutputKeys.SUMMARY_RAW_REF, summaryRawRef);
        }
        if (StringUtils.hasText(decisionRawRef) && !output.containsKey(OutputKeys.RAW_REF)) {
            output.put(OutputKeys.RAW_REF, decisionRawRef);
        }
        if (StringUtils.hasText(summaryRawRef) && !output.containsKey(OutputKeys.RAW_REF)) {
            output.put(OutputKeys.RAW_REF, summaryRawRef);
        }
    }

    /**
     * 合并引用字段。
     *
     * @param output 输出
     * @param key 键
     * @param value 值
     */
    public void mergeRef(Map<String, Object> output, String key, String value) {
        if (output == null || !StringUtils.hasText(key) || !StringUtils.hasText(value)) {
            return;
        }
        Object refsObj = output.get(OutputKeys.REFS);
        Map<String, String> refs;
        if (refsObj instanceof Map<?, ?> rawMap) {
            refs = new HashMap<>();
            rawMap.forEach((mapKey, mapValue) -> {
                if (mapKey != null && mapValue != null) {
                    refs.put(String.valueOf(mapKey), String.valueOf(mapValue));
                }
            });
        } else {
            refs = new HashMap<>();
        }
        refs.put(key, value);
        output.put(OutputKeys.REFS, refs);
    }

    private String resolveAnswerFromResult(Map<String, Object> toolResult) {
        if (toolResult == null || toolResult.isEmpty()) {
            return null;
        }
        Object answer = toolResult.get("answer");
        if (answer instanceof String text && StringUtils.hasText(text)) {
            return text;
        }
        Object finalAnswer = toolResult.get("finalAnswer");
        if (finalAnswer != null && StringUtils.hasText(finalAnswer.toString())) {
            return finalAnswer.toString();
        }
        Object message = toolResult.get("message");
        if (message != null && StringUtils.hasText(message.toString())) {
            return message.toString();
        }
        return null;
    }
}
