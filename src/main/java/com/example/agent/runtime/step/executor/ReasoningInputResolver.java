package com.example.agent.runtime.step.executor;

import com.example.agent.api.http.dto.TaskRequest;
import com.example.agent.reasoning.common.ReasoningInput;
import com.example.agent.reasoning.common.ReasoningRequest;
import com.example.agent.runtime.step.contract.StepExecutionRequest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 推理执行输入解析器。
 *
 * <p>用途：统一解析推理步骤的主题/问题入参与链路请求对象，避免执行器重复实现。
 */
@Component
public class ReasoningInputResolver {

    private static final List<String> DEFAULT_KEYS = List.of("question", "topic", "prompt", "query");

    /**
     * 解析并复制执行输入。
     *
     * @param request 步骤执行请求
     * @return 复制后的输入映射
     */
    public Map<String, Object> resolveExecutionInput(StepExecutionRequest request) {
        if (request == null || request.getStepInput() == null || request.getStepInput().isEmpty()) {
            return new LinkedHashMap<>();
        }
        return new LinkedHashMap<>(request.getStepInput());
    }

    /**
     * 解析推理提示文本。
     *
     * @param stepInput 步骤输入
     * @param taskRequest 任务请求
     * @param preferredKeys 优先键顺序
     * @return 规整后的提示文本，空值返回空字符串
     */
    public String resolvePrompt(Map<String, Object> stepInput,
                                TaskRequest taskRequest,
                                String... preferredKeys) {
        return resolvePrompt(new ReasoningInput(stepInput), taskRequest, preferredKeys);
    }

    /**
     * 解析推理提示文本。
     *
     * @param stepInput 步骤输入
     * @param taskRequest 任务请求
     * @param preferredKeys 优先键顺序
     * @return 规整后的提示文本，空值返回空字符串
     */
    public String resolvePrompt(ReasoningInput stepInput,
                                TaskRequest taskRequest,
                                String... preferredKeys) {
        List<String> keys = mergeKeys(preferredKeys);
        for (String key : keys) {
            String value = stepInput != null ? stepInput.getString(key) : null;
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        if (taskRequest != null) {
            String query = resolveText(taskRequest.getQuery());
            if (StringUtils.hasText(query)) {
                return query;
            }
        }
        return "";
    }

    /**
     * 构建统一推理请求。
     *
     * @param strategyType 策略类型
     * @param request 步骤执行请求
     * @param preferredKeys 提示词优先键顺序
     * @return 推理请求对象
     */
    public ReasoningRequest buildRequest(String strategyType,
                                         StepExecutionRequest request,
                                         String... preferredKeys) {
        Map<String, Object> stepInput = resolveExecutionInput(request);
        String prompt = resolvePrompt(stepInput, request != null ? request.getTaskRequest() : null, preferredKeys);
        return new ReasoningRequest(
                strategyType,
                prompt,
                stepInput,
                request != null ? request.getTenantContext() : null,
                request != null ? request.getWorkflowId() : null,
                request != null ? request.getSeqCounter() : null
        );
    }

    /**
     * 解析是否启用并行推理。
     *
     * @param stepInput 步骤输入
     * @return 是否启用并行
     */
    public boolean resolveParallelEnabled(Map<String, Object> stepInput) {
        return resolveParallelEnabled(new ReasoningInput(stepInput));
    }

    /**
     * 解析是否启用并行推理。
     *
     * @param stepInput 步骤输入
     * @return 是否启用并行
     */
    public boolean resolveParallelEnabled(ReasoningInput stepInput) {
        if (stepInput == null) {
            return false;
        }
        return stepInput.getBoolean("parallelEnabled", false);
    }

    /**
     * 解析候选策略列表。
     *
     * @param stepInput 步骤输入
     * @param defaultStrategy 默认策略
     * @return 候选策略
     */
    public List<String> resolveCandidateStrategies(Map<String, Object> stepInput, String defaultStrategy) {
        return resolveCandidateStrategies(new ReasoningInput(stepInput), defaultStrategy);
    }

    /**
     * 解析候选策略列表。
     *
     * @param stepInput 步骤输入
     * @param defaultStrategy 默认策略
     * @return 候选策略
     */
    public List<String> resolveCandidateStrategies(ReasoningInput stepInput, String defaultStrategy) {
        List<String> candidates = new ArrayList<>();
        if (stepInput != null) {
            for (String item : stepInput.getStringList("parallelStrategies")) {
                String strategy = normalizeStrategy(item);
                if (StringUtils.hasText(strategy) && !candidates.contains(strategy)) {
                    candidates.add(strategy);
                }
            }
        }
        String normalizedDefault = normalizeStrategy(defaultStrategy);
        if (StringUtils.hasText(normalizedDefault) && !candidates.contains(normalizedDefault)) {
            candidates.add(0, normalizedDefault);
        }
        return candidates;
    }

    /**
     * 解析首选策略。
     *
     * @param stepInput 步骤输入
     * @param defaultStrategy 默认策略
     * @return 首选策略
     */
    public String resolvePreferredStrategy(Map<String, Object> stepInput, String defaultStrategy) {
        return resolvePreferredStrategy(new ReasoningInput(stepInput), defaultStrategy);
    }

    /**
     * 解析首选策略。
     *
     * @param stepInput 步骤输入
     * @param defaultStrategy 默认策略
     * @return 首选策略
     */
    public String resolvePreferredStrategy(ReasoningInput stepInput, String defaultStrategy) {
        if (stepInput != null) {
            String fromInput = normalizeStrategy(stepInput.getString("strategyType"));
            if (StringUtils.hasText(fromInput)) {
                return fromInput;
            }
            String fromAlias = normalizeStrategy(stepInput.getString("strategy"));
            if (StringUtils.hasText(fromAlias)) {
                return fromAlias;
            }
        }
        String normalizedDefault = normalizeStrategy(defaultStrategy);
        return StringUtils.hasText(normalizedDefault) ? normalizedDefault : "cot";
    }

    private List<String> mergeKeys(String... preferredKeys) {
        List<String> result = new ArrayList<>();
        if (preferredKeys != null) {
            for (String key : preferredKeys) {
                if (StringUtils.hasText(key) && !result.contains(key)) {
                    result.add(key);
                }
            }
        }
        for (String key : DEFAULT_KEYS) {
            if (!result.contains(key)) {
                result.add(key);
            }
        }
        return result;
    }

    private String resolveText(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String text) {
            return text.trim();
        }
        return value.toString().trim();
    }

    private String normalizeStrategy(String strategy) {
        if (!StringUtils.hasText(strategy)) {
            return null;
        }
        String normalized = strategy.trim().toLowerCase();
        return switch (normalized) {
            case "chain_of_thought", "cot" -> "cot";
            case "debate" -> "debate";
            case "thought_tree" -> "thought_tree";
            default -> null;
        };
    }
}
