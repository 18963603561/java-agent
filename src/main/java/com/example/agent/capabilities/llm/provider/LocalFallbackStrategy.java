package com.example.agent.capabilities.llm.provider;

import com.example.agent.capabilities.llm.provider.ModelRequest;
import com.example.agent.runtime.api.RuntimeContextView;
import com.example.agent.runtime.output.OutputKeys;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 本地兜底策略。
 *
 * <p>用途：在远程模型不可用或未配置时提供稳定的结构化兜底输出。</p>
 */
@Component
public class LocalFallbackStrategy {

    private static final Logger log = LoggerFactory.getLogger(LocalFallbackStrategy.class);

    private static final String PLAN_MARKER = "PLAN_CONTEXT_JSON:";
    private static final String REFLECTION_MARKER = "REFLECTION_CONTEXT_JSON:";
    private static final String FINAL_MARKER = "FINAL_CONTEXT_JSON:";
    private static final String RESEARCH_MARKER = "RESEARCH_CONTEXT_JSON:";
    private static final String DEBATE_MARKER = "DEBATE_CONTEXT_JSON:";
    private static final String MULTI_AGENT_MARKER = "MULTI_AGENT_CONTEXT_JSON:";
    private static final String COT_MARKER = "COT_CONTEXT_JSON:";
    private static final String LLM_STEP_MARKER = "LLM_STEP_CONTEXT_JSON:";
    private static final String LLM_STEP_TOOL_RESULT_MARKER = "LLM_STEP_TOOL_RESULT_JSON:";

    private final ObjectMapper objectMapper;
    private final ModelMessageBuilder messageBuilder;

    public LocalFallbackStrategy(ObjectMapper objectMapper, ModelMessageBuilder messageBuilder) {
        this.objectMapper = objectMapper;
        this.messageBuilder = messageBuilder;
    }

    /**
     * 生成本地兜底输出。
     *
     * @param request 模型请求
     * @return 输出文本
     */
    public String buildResponse(ModelRequest request) {
        String prompt = messageBuilder.resolvePrompt(request != null ? request.getPrompt() : null,
                request != null ? request.getMessages() : null);
        if (prompt == null) {
            return "response:";
        }
        if (prompt.contains(LLM_STEP_TOOL_RESULT_MARKER)) {
            Map<String, Object> context = parseJsonAfterMarker(prompt, LLM_STEP_TOOL_RESULT_MARKER);
            return buildLocalLlmStepToolSummary(context);
        }
        if (prompt.contains(LLM_STEP_MARKER)) {
            Map<String, Object> context = parseJsonAfterMarker(prompt, LLM_STEP_MARKER);
            return buildLocalLlmStepDecision(context);
        }
        if (prompt.contains(PLAN_MARKER)) {
            Map<String, Object> context = parseJsonAfterMarker(prompt, PLAN_MARKER);
            return buildLocalPlan(context);
        }
        if (prompt.contains(REFLECTION_MARKER)) {
            Map<String, Object> context = parseJsonAfterMarker(prompt, REFLECTION_MARKER);
            return buildLocalReflection(context);
        }
        if (prompt.contains(FINAL_MARKER)) {
            Map<String, Object> context = parseJsonAfterMarker(prompt, FINAL_MARKER);
            return buildLocalFinal(context);
        }
        if (prompt.contains(RESEARCH_MARKER)) {
            Map<String, Object> context = parseJsonAfterMarker(prompt, RESEARCH_MARKER);
            return buildLocalResearch(context);
        }
        if (prompt.contains(DEBATE_MARKER)) {
            Map<String, Object> context = parseJsonAfterMarker(prompt, DEBATE_MARKER);
            return buildLocalDebate(context);
        }
        if (prompt.contains(MULTI_AGENT_MARKER)) {
            Map<String, Object> context = parseJsonAfterMarker(prompt, MULTI_AGENT_MARKER);
            return buildLocalMultiAgent(context);
        }
        if (prompt.contains(COT_MARKER)) {
            Map<String, Object> context = parseJsonAfterMarker(prompt, COT_MARKER);
            return buildLocalChainOfThought(context);
        }
        return "response:" + prompt;
    }

    private Map<String, Object> parseJsonAfterMarker(String prompt, String marker) {
        int index = prompt.indexOf(marker);
        if (index < 0) {
            return Map.of();
        }
        String json = prompt.substring(index + marker.length()).trim();
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception ex) {
            log.warn("解析标记 JSON 失败, marker={}, jsonLength={}", marker, json.length(), ex);
            return Map.of();
        }
    }

    private String buildLocalPlan(Map<String, Object> context) {
        boolean disableTools = isToolsDisabled(context);
        Map<String, Object> step = new HashMap<>();
        step.put("type", "LLM");
        Map<String, Object> input = new HashMap<>();
        if (context.containsKey("query")) {
            input.put("question", context.get("query"));
        }
        if (context.containsKey("context")) {
            input.put("context", context.get("context"));
        }
        if (disableTools) {
            input.put("disableTools", true);
        }
        step.put("input", input);
        Map<String, Object> result = new HashMap<>();
        result.put("summary", "local-plan");
        result.put("steps", List.of(step));
        return toJsonOrDefault(result, "{\"summary\":\"local-plan\",\"steps\":[]}");
    }

    private String buildLocalLlmStepDecision(Map<String, Object> context) {
        String query = context != null && context.get("query") instanceof String value ? value : "";
        boolean disableTools = false;
        if (context != null && context.get("constraints") instanceof Map<?, ?> constraints) {
            disableTools = isTruthy(constraints.get("disableTools"));
        }
        List<String> toolNames = extractToolNames(context != null ? context.get("availableTools") : null);
        String specifiedTool = null;
        if (context != null && context.get("toolChoice") instanceof Map<?, ?> choice) {
            Object mode = choice.get("mode");
            if (mode != null && "specified".equalsIgnoreCase(mode.toString())) {
                Object toolName = choice.get(OutputKeys.TOOL_NAME);
                if (toolName == null) {
                    toolName = choice.get("name");
                }
                if (toolName != null && StringUtils.hasText(toolName.toString())) {
                    specifiedTool = toolName.toString();
                }
            }
        }

        if (log.isDebugEnabled()) {
            log.debug("本地兜底 LLM Step 决策, disableTools={}, toolCount={}, specifiedTool={}",
                    disableTools, toolNames.size(), specifiedTool);
        }

        Map<String, Object> result = new HashMap<>();
        if (disableTools || toolNames.isEmpty()) {
            result.put("mode", "answer");
            result.put("answer", disableTools ? "工具已禁用，无法执行工具调用。" : "未提供可用工具列表，无法执行工具调用。");
            result.put("reason", disableTools ? "tools_disabled" : "no_tool_list_provided");
            result.put("confidence", 0.4);
            return toJsonOrDefault(result, "{\"mode\":\"answer\",\"answer\":\"no_tool_list_provided\",\"confidence\":0.4}");
        }

        String toolName = null;
        if (StringUtils.hasText(specifiedTool) && toolNames.contains(specifiedTool)) {
            toolName = specifiedTool;
        }
        if (!StringUtils.hasText(toolName)) {
            if (StringUtils.hasText(query) && query.contains("用户") && toolNames.contains("user_query")) {
                toolName = "user_query";
            } else if (toolNames.contains("demo_tool")) {
                toolName = "demo_tool";
            } else {
                toolName = toolNames.get(0);
            }
        }

        Map<String, Object> tool = new HashMap<>();
        tool.put("name", toolName);
        Map<String, Object> arguments = new HashMap<>();
        if ("user_query".equals(toolName)) {
            arguments.put("query", query);
        } else if ("demo_tool".equals(toolName)) {
            arguments.put("text", query);
        } else {
            arguments.put("query", query);
        }
        tool.put("arguments", arguments);

        result.put("mode", "tool_call");
        result.put("tool", tool);
        result.put("answer", "");
        result.put("reason", "local_llm_step");
        result.put("confidence", 0.6);
        return toJsonOrDefault(result, "{\"mode\":\"tool_call\",\"tool\":{\"name\":\"" + toolName + "\",\"arguments\":{}}}");
    }

    private String buildLocalLlmStepToolSummary(Map<String, Object> context) {
        String query = context != null && context.get("query") instanceof String value ? value : "";
        String toolName = context != null && context.get("tool") != null ? String.valueOf(context.get("tool")) : "";
        String status = context != null && context.get("status") != null ? String.valueOf(context.get("status")) : "";
        boolean success = "SUCCESS".equalsIgnoreCase(status);

        if (log.isDebugEnabled()) {
            log.debug("本地兜底 LLM Step 工具总结, status={}, tool={}, queryLength={}", status, toolName, query.length());
        }

        Map<String, Object> toolResult = toStringObjectMap(context != null ? context.get("result") : null);
        if (toolResult.containsKey("result") && toolResult.get("result") instanceof Map<?, ?>) {
            Map<String, Object> nested = toStringObjectMap(toolResult.get("result"));
            if (!nested.isEmpty()) {
                toolResult = nested;
            }
        }

        String answer;
        String highlights;
        double confidence;
        if (!success) {
            String errorCode = context != null && context.get("errorCode") != null ? String.valueOf(context.get("errorCode")) : "TOOL_FAILED";
            String errorMessage = context != null && context.get("errorMessage") != null ? String.valueOf(context.get("errorMessage")) : "";
            answer = StringUtils.hasText(errorMessage) ? errorMessage : ("工具执行失败: " + errorCode);
            highlights = "status=FAILED, errorCode=" + errorCode;
            confidence = 0.2;
        } else if ("user_query".equalsIgnoreCase(toolName) && !toolResult.isEmpty()) {
            Object user = toolResult.get("user");
            Object matchedUsers = toolResult.get("matchedUsers");
            String userJson = safeJson(user);
            String matchedJson = safeJson(matchedUsers);
            answer = "查询结果：bob用户信息=" + userJson + "；包含'h'的用户=" + matchedJson;
            Object matchedCount = toolResult.get("matchedCount");
            highlights = "status=SUCCESS, matchedCount=" + (matchedCount != null ? matchedCount : "0");
            confidence = 0.85;
        } else {
            answer = "工具执行成功，已获得结果。query=" + query;
            highlights = "status=SUCCESS, tool=" + toolName;
            confidence = 0.6;
        }

        Map<String, Object> output = new HashMap<>();
        output.put("answer", answer);
        output.put("highlights", highlights);
        output.put("confidence", confidence);
        return toJsonOrDefault(output, "{\"answer\":\"ok\",\"highlights\":\"local\",\"confidence\":0.6}");
    }

    private List<String> extractToolNames(Object availableTools) {
        if (!(availableTools instanceof List<?> tools) || tools.isEmpty()) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        for (Object tool : tools) {
            if (tool instanceof Map<?, ?> map) {
                Object name = map.get("name");
                if (name == null) {
                    name = map.get(OutputKeys.TOOL_NAME);
                }
                if (name != null && StringUtils.hasText(name.toString())) {
                    names.add(name.toString());
                }
                continue;
            }
            if (tool != null && StringUtils.hasText(tool.toString())) {
                names.add(tool.toString());
            }
        }
        return names;
    }

    private Map<String, Object> toStringObjectMap(Object value) {
        if (!(value instanceof Map<?, ?> map) || map.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> normalized = new HashMap<>();
        map.forEach((key, item) -> normalized.put(String.valueOf(key), item));
        return normalized;
    }

    private boolean isToolsDisabled(Map<String, Object> context) {
        if (context == null) {
            return false;
        }
        if (isTruthy(context.get("disableTools"))) {
            return true;
        }
        if (context.get("context") instanceof Map<?, ?> inner && isTruthy(inner.get("disableTools"))) {
            return true;
        }
        Object rawChoice = context.get("toolChoice");
        if (rawChoice == null && context.get("context") instanceof Map<?, ?> inner) {
            rawChoice = inner.get("toolChoice");
        }
        if (rawChoice instanceof Map<?, ?> choice) {
            Object mode = choice.get("mode");
            if (mode != null && "none".equalsIgnoreCase(mode.toString())) {
                return true;
            }
        }
        return false;
    }

    private boolean isTruthy(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String text) {
            return "true".equalsIgnoreCase(text.trim());
        }
        return false;
    }

    private String buildLocalReflection(Map<String, Object> context) {
        double score = 0.9;
        boolean retry = false;
        Object output = context.get("output");
        if (output == null || output.toString().isBlank()) {
            score = 0.4;
            retry = true;
        } else if (output.toString().toLowerCase(Locale.ROOT).contains("error")) {
            score = 0.5;
            retry = true;
        }
        Map<String, Object> result = new HashMap<>();
        result.put("score", score);
        result.put("retry", retry);
        result.put("notes", retry ? "需要改进输出" : "输出质量良好");
        return toJsonOrDefault(result, "{\"score\":0.8,\"retry\":false,\"notes\":\"ok\"}");
    }

    private String buildLocalFinal(Map<String, Object> context) {
        String answer = "已生成答复";
        Object query = context.get("query");
        if (query instanceof String value && !value.isBlank()) {
            answer = "针对问题\"" + value + "\"给出答复";
        }
        Map<String, Object> result = new HashMap<>();
        result.put("answer", answer);
        result.put("confidence", 0.6);
        return toJsonOrDefault(result, "{\"answer\":\"ok\"}");
    }

    private String buildLocalResearch(Map<String, Object> context) {
        Map<String, Object> citation = new HashMap<>();
        citation.put("title", "local-source");
        citation.put("url", "local");
        citation.put("snippet", "local research result");
        Map<String, Object> result = new HashMap<>();
        result.put("citations", List.of(citation));
        result.put("summary", "local research");
        return toJsonOrDefault(result, "{\"citations\":[],\"summary\":\"local\"}");
    }

    private String buildLocalDebate(Map<String, Object> context) {
        Map<String, Object> result = new HashMap<>();
        result.put("conclusion", "local debate");
        result.put("round", 1);
        return toJsonOrDefault(result, "{\"conclusion\":\"local\"}");
    }

    private String buildLocalMultiAgent(Map<String, Object> context) {
        Map<String, Object> agent = new HashMap<>();
        agent.put("role", "planner");
        agent.put("name", "local-agent");
        Map<String, Object> result = new HashMap<>();
        result.put("team", List.of(agent));
        result.put("summary", "local multi-agent");
        return toJsonOrDefault(result, "{\"team\":[]}");
    }

    private String buildLocalChainOfThought(Map<String, Object> context) {
        String question = context.get("question") instanceof String value ? value : "";
        Map<String, Object> result = new HashMap<>();
        result.put("stepSummary", "本地链式推理摘要");
        result.put("shouldContinue", false);
        result.put("finalAnswer", question.isBlank() ? "本地推理完成" : "已完成问题解析: " + question);
        result.put("confidence", 0.6);
        result.put("stopReason", "completed");
        return toJsonOrDefault(result, "{\"shouldContinue\":false,\"finalAnswer\":\"local\"}");
    }

    private String safeJson(Object value) {
        if (value == null) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            log.warn("本地兜底 JSON 序列化失败, valueType={}", value.getClass().getName(), ex);
            return String.valueOf(value);
        }
    }

    private String toJsonOrDefault(Object value, String defaultJson) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            log.error("本地兜底 JSON 序列化失败", ex);
            return defaultJson;
        }
    }

    /**
     * 返回规划工具名称。
     *
     * @param context 上下文
     * @return 工具名称
     */
    public String resolvePlanToolName(Map<String, Object> context) {
        return RuntimeContextView.of(context).resolveToolName();
    }
}

