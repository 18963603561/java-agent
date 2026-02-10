package com.example.agent.orchestration.multiagent;

import com.example.agent.runtime.model.StepSpec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 多智能体输入摘要构建器。
 * <p>用途：从步骤参数提炼多智能体所需最小上下文，降低提示词噪音。
 */
@Component
public class MultiAgentInputSummaryBuilder {

    /**
     * 构建输入摘要。
     */
    public Map<String, Object> build(StepSpec step) {
        Map<String, Object> summary = new HashMap<>();
        if (step != null) {
            Map<String, Object> input = step.getArguments() != null ? step.getArguments() : Map.of();
            putIfNotBlank(summary, "query", input.get("query"));
            putIfNotBlank(summary, "goal", input.get("goal"));
            Object constraints = normalizeTextOrList(input.get("constraints"));
            if (constraints != null) {
                summary.put("constraints", constraints);
            }
            List<String> tools = new ArrayList<>();
            addToolName(tools, input.get("tool"));
            addToolName(tools, input.get("toolName"));
            Object toolsObj = input.get("tools");
            if (toolsObj instanceof List<?> list) {
                for (Object item : list) {
                    addToolName(tools, item);
                }
            }
            if (!tools.isEmpty()) {
                summary.put("tools", tools);
            }
        }
        if (summary.isEmpty()) {
            summary.put("constraints", "(summary disabled)");
        }
        return summary;
    }

    /**
     * 构建调用元数据。
     */
    public Map<String, Object> buildMetadata(StepSpec step) {
        Map<String, Object> metadata = new HashMap<>();
        if (step != null && step.getStepType() != null) {
            metadata.put("stepType", step.getStepType());
        }
        metadata.put("promptScene", "multiagent");
        return metadata;
    }

    private Object normalizeTextOrList(Object value) {
        if (value instanceof String text) {
            return StringUtils.hasText(text) ? text : null;
        }
        if (value instanceof List<?> list) {
            List<String> normalized = new ArrayList<>();
            for (Object item : list) {
                if (item == null) {
                    continue;
                }
                String text = item.toString();
                if (StringUtils.hasText(text)) {
                    normalized.add(text);
                }
            }
            return normalized.isEmpty() ? null : normalized;
        }
        return null;
    }

    private void putIfNotBlank(Map<String, Object> target, String key, Object value) {
        if (target == null || key == null || value == null) {
            return;
        }
        String text = value.toString();
        if (StringUtils.hasText(text)) {
            target.put(key, text);
        }
    }

    private void addToolName(List<String> tools, Object value) {
        if (tools == null || value == null) {
            return;
        }
        String text = value.toString();
        if (!StringUtils.hasText(text) || tools.contains(text)) {
            return;
        }
        tools.add(text);
    }
}

