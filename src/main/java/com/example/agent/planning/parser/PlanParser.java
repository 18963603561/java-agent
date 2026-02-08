package com.example.agent.planning.parser;

import com.example.agent.api.http.dto.TaskRequest;
import com.example.agent.planning.PlanningContextKeys;
import com.example.agent.planning.PlanningFieldKeys;
import com.example.agent.runtime.model.StepPolicy;
import com.example.agent.runtime.model.StepSpec;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 规划输出解析器。
 *
 * <p>用途：将模型输出 JSON 解析为可执行步骤，并进行基础校验与标准化。
 */
@Component
public class PlanParser {

    private static final Logger log = LoggerFactory.getLogger(PlanParser.class);

    @Value("${agent.planner.strict-tool-arguments:false}")
    private boolean strictToolArguments;

    private final ObjectMapper objectMapper;

    /**
     * 构造解析器。
     *
     * @param objectMapper 序列化工具
     */
    public PlanParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 解析规划输出。
     *
     * @param content 模型输出
     * @param request 任务请求
     * @param context 上下文映射
     * @return 解析结果，无法解析返回 {@code null}
     * @throws Exception 解析异常
     */
    public PlanParseResult parse(String content,
                                 TaskRequest request,
                                 Map<String, Object> context) throws Exception {
        PlanParseAttemptResult attemptResult = parseAttempt(content, request, context);
        return attemptResult != null && attemptResult.isSuccess() ? attemptResult.getResult() : null;
    }

    /**
     * 尝试解析规划输出并返回显式结果。
     *
     * @param content 模型输出
     * @param request 任务请求
     * @param context 上下文映射
     * @return 解析尝试结果
     */
    public PlanParseAttemptResult parseAttempt(String content,
                                               TaskRequest request,
                                               Map<String, Object> context) {
        if (!StringUtils.hasText(content)) {
            return PlanParseAttemptResult.failure(PlanParseErrorTypes.EMPTY_OUTPUT);
        }
        try {
            return parseInternal(content, request, context);
        } catch (Exception ex) {
            log.warn("规划 JSON 解析异常, reason={}", ex.getMessage(), ex);
            return PlanParseAttemptResult.failure(PlanParseErrorTypes.JSON_PARSE_ERROR);
        }
    }

    private PlanParseAttemptResult parseInternal(String content,
                                                 TaskRequest request,
                                                 Map<String, Object> context) throws Exception {
        Map<String, Object> root = objectMapper.readValue(content, new TypeReference<Map<String, Object>>() {
        });
        Object stepsObj = root.get(PlanningFieldKeys.STEPS);
        if (!(stepsObj instanceof List<?> stepList)) {
            return PlanParseAttemptResult.failure(PlanParseErrorTypes.MISSING_STEPS);
        }
        List<StepSpec> steps = new ArrayList<>(stepList.size());
        int index = 0;
        boolean strictArgumentRejected = false;
        for (Object item : stepList) {
            index++;
            if (!(item instanceof Map<?, ?> stepMap)) {
                continue;
            }
            String type = stepMap.get(PlanningFieldKeys.TYPE) instanceof String typeValue
                    ? typeValue
                    : "TOOL";
            Map<String, Object> input;
            if (stepMap.get(PlanningFieldKeys.INPUT) instanceof Map<?, ?> inputMap) {
                input = new HashMap<>(Math.max(4, inputMap.size() + 4));
                inputMap.forEach((key, value) -> input.put(String.valueOf(key), value));
            } else {
                input = new HashMap<>(4);
            }
            if (!input.containsKey(PlanningFieldKeys.QUERY) && request != null) {
                input.put(PlanningFieldKeys.QUERY, request.getQuery());
            }
            if (!input.containsKey(PlanningFieldKeys.CONTEXT)) {
                input.put(PlanningFieldKeys.CONTEXT, context);
            }
            Object toolName = stepMap.get(PlanningContextKeys.TOOL);
            if (toolName instanceof String name && !name.isBlank()) {
                input.putIfAbsent(PlanningContextKeys.TOOL, name);
            }
            if (stepMap.get(PlanningFieldKeys.DEPENDS_ON) instanceof List<?> deps) {
                input.putIfAbsent(PlanningFieldKeys.DEPENDS_ON, deps);
            }
            if (isToolStep(type) && strictToolArguments) {
                String reason = validateToolStepInput(input);
                if (reason != null) {
                    log.warn("规划 TOOL 步骤缺少必要参数, stepIndex={}, reason={}", index, reason);
                    strictArgumentRejected = true;
                    continue;
                }
            }
            steps.add(buildStepSpec(type, input));
        }
        if (steps.isEmpty()) {
            if (strictArgumentRejected) {
                return PlanParseAttemptResult.failure(PlanParseErrorTypes.INVALID_TOOL_ARGUMENTS);
            }
            return PlanParseAttemptResult.failure(PlanParseErrorTypes.NO_VALID_STEPS);
        }
        String summary = root.get(PlanningFieldKeys.SUMMARY) instanceof String value ? value : "llm-plan";
        return PlanParseAttemptResult.success(new PlanParseResult(summary, steps));
    }

    /**
     * 构建步骤规格。
     *
     * @param type 步骤类型
     * @param input 输入映射
     * @return 步骤规格
     */
    public StepSpec buildStepSpec(String type, Map<String, Object> input) {
        StepSpec step = new StepSpec();
        step.setStepType(type);
        if (input == null || input.isEmpty()) {
            return step;
        }

        Map<String, Object> arguments = new HashMap<>(input);
        Map<String, Object> context = null;
        Object contextObj = arguments.remove(PlanningFieldKeys.CONTEXT);
        if (contextObj instanceof Map<?, ?> map) {
            Map<String, Object> contextMap = new HashMap<>();
            map.forEach((key, value) -> contextMap.put(String.valueOf(key), value));
            context = contextMap;
        }
        step.setContext(context);

        List<String> dependsOn = null;
        Object dependsObj = arguments.remove(PlanningFieldKeys.DEPENDS_ON);
        if (dependsObj instanceof List<?> list && !list.isEmpty()) {
            dependsOn = new ArrayList<>();
            for (Object item : list) {
                if (item != null) {
                    dependsOn.add(String.valueOf(item));
                }
            }
            if (dependsOn.isEmpty()) {
                dependsOn = null;
            }
        }
        step.setDependsOn(dependsOn);

        Object requiresApprovalObj = arguments.remove(PlanningContextKeys.REQUIRES_APPROVAL);
        Object approvalSourceObj = arguments.remove(PlanningContextKeys.APPROVAL_SOURCE);
        if (requiresApprovalObj != null || approvalSourceObj != null) {
            StepPolicy policy = new StepPolicy();
            if (requiresApprovalObj instanceof Boolean boolValue) {
                policy.setRequiresApproval(boolValue);
            } else if (requiresApprovalObj instanceof String text && !text.isBlank()) {
                policy.setRequiresApproval(Boolean.parseBoolean(text));
            }
            if (approvalSourceObj != null) {
                policy.setApprovalSource(String.valueOf(approvalSourceObj));
            }
            step.setPolicy(policy);
        }

        step.setArguments(arguments.isEmpty() ? null : arguments);
        return step;
    }

    /**
     * 校验 TOOL 步骤输入。
     *
     * @param input 步骤输入
     * @return 校验失败原因，校验通过返回 {@code null}
     */
    public String validateToolStepInput(Map<String, Object> input) {
        if (input == null) {
            return "input_empty";
        }
        Object tool = input.get(PlanningContextKeys.TOOL);
        Object toolName = input.get(PlanningContextKeys.TOOL_NAME);
        String resolvedTool = tool instanceof String value && StringUtils.hasText(value)
                ? value
                : toolName != null ? toolName.toString() : null;
        if (!StringUtils.hasText(resolvedTool)) {
            return "missing_tool_name";
        }
        Object arguments = input.get(PlanningFieldKeys.ARGUMENTS);
        if (!(arguments instanceof Map<?, ?> map) || map.isEmpty()) {
            return "missing_arguments";
        }
        return null;
    }

    private boolean isToolStep(String type) {
        return type != null && "TOOL".equalsIgnoreCase(type);
    }
}
