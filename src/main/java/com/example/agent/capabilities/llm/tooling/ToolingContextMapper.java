package com.example.agent.capabilities.llm.tooling;

import com.example.agent.capabilities.context.runtime.ContextRuntimeKeys;
import com.example.agent.capabilities.llm.contract.LlmTaskContext;
import com.example.agent.capabilities.llm.contract.ModelToolChoice;
import com.example.agent.capabilities.tools.skill.SkillDefinition;
import com.example.agent.capabilities.tools.skill.SkillRoute;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 工具治理上下文映射器。
 *
 * <p>用途：集中处理任务请求与步骤输入中的 Map 协议键，输出强类型上下文与约束对象。
 * <p>输入：任务请求、步骤输入与技能定义。
 * <p>输出：工具治理上下文与工具治理约束。
 * <p>边界：输入为空时返回空对象，不抛异常。
 */
@Component
public class ToolingContextMapper {

    /**
     * 从任务上下文与步骤输入映射工具治理上下文。
     *
     * @param taskContext LLM 任务上下文
     * @param stepInput 步骤输入
     * @return 工具治理上下文
     */
    public ModelToolingContext toToolingContext(LlmTaskContext taskContext, Map<String, Object> stepInput) {
        String tenantId = resolveTenantId(taskContext, stepInput);
        String skillName = resolveSkillName(taskContext, stepInput);
        ModelToolChoice toolChoice = resolveToolChoice(taskContext, stepInput);
        boolean disableTools = resolveDisableTools(taskContext, stepInput)
                || (toolChoice != null && toolChoice.getMode() == ModelToolChoice.Mode.NONE);
        return new ModelToolingContext(tenantId, skillName, toolChoice, disableTools);
    }

    /**
     * 从技能定义映射工具治理约束。
     *
     * @param skillDefinition 技能定义
     * @return 工具治理约束
     */
    public ToolingConstraints toConstraints(SkillDefinition skillDefinition) {
        if (skillDefinition == null) {
            return ToolingConstraints.empty();
        }
        SkillToolPolicy policy = new SkillToolPolicy(
                resolveAllowTools(skillDefinition),
                resolveToolChoiceFromConstraints(skillDefinition));
        return new ToolingConstraints(policy);
    }

    private String resolveTenantId(LlmTaskContext taskContext, Map<String, Object> stepInput) {
        String fromStep = asText(stepInput != null ? stepInput.get("tenantId") : null);
        if (StringUtils.hasText(fromStep)) {
            return fromStep;
        }
        return asText(taskContext != null && taskContext.getContext() != null
                ? taskContext.getContext().get("tenantId")
                : null);
    }

    private String resolveSkillName(LlmTaskContext taskContext, Map<String, Object> stepInput) {
        String fromStep = asText(stepInput != null ? stepInput.get("skill") : null);
        if (!StringUtils.hasText(fromStep)) {
            fromStep = asText(stepInput != null ? stepInput.get("skillName") : null);
        }
        if (StringUtils.hasText(fromStep)) {
            return fromStep;
        }
        return taskContext != null ? taskContext.getSkillName() : null;
    }

    private ModelToolChoice resolveToolChoice(LlmTaskContext taskContext, Map<String, Object> stepInput) {
        ModelToolChoice fromStep = ModelToolChoice.fromRaw(
                stepInput != null ? stepInput.get(ContextRuntimeKeys.TOOL_CHOICE) : null);
        if (fromStep != null) {
            return fromStep;
        }
        if (stepInput != null && stepInput.get("context") instanceof Map<?, ?> contextMap) {
            ModelToolChoice fromContext = ModelToolChoice.fromRaw(contextMap.get(ContextRuntimeKeys.TOOL_CHOICE));
            if (fromContext != null) {
                return fromContext;
            }
        }
        if (taskContext != null) {
            if (taskContext.getToolChoice() != null) {
                return taskContext.getToolChoice();
            }
            if (taskContext.getContext() != null) {
                return ModelToolChoice.fromRaw(taskContext.getContext().get(ContextRuntimeKeys.TOOL_CHOICE));
            }
        }
        return null;
    }

    private boolean resolveDisableTools(LlmTaskContext taskContext, Map<String, Object> stepInput) {
        Object disableFromStep = resolveDisableToolsValue(stepInput);
        if (isTruthy(disableFromStep)) {
            return true;
        }
        if (taskContext != null && taskContext.getContext() != null) {
            return isTruthy(taskContext.getContext().get("disableTools"));
        }
        return false;
    }

    private Object resolveDisableToolsValue(Map<String, Object> stepInput) {
        if (stepInput == null) {
            return null;
        }
        if (stepInput.containsKey("disableTools")) {
            return stepInput.get("disableTools");
        }
        Object context = stepInput.get("context");
        if (context instanceof Map<?, ?> contextMap) {
            return contextMap.get("disableTools");
        }
        return null;
    }

    private List<String> resolveAllowTools(SkillDefinition skillDefinition) {
        if (skillDefinition == null) {
            return List.of();
        }
        List<String> fromConstraints = resolveAllowToolsFromConstraints(skillDefinition.getConstraints());
        if (!fromConstraints.isEmpty()) {
            return fromConstraints;
        }
        if (skillDefinition.getRoutes() == null || skillDefinition.getRoutes().isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> names = new LinkedHashSet<>();
        for (SkillRoute route : skillDefinition.getRoutes()) {
            if (route != null && StringUtils.hasText(route.getToolName())) {
                names.add(route.getToolName());
            }
        }
        return List.copyOf(names);
    }

    private List<String> resolveAllowToolsFromConstraints(Map<String, Object> constraints) {
        if (constraints == null) {
            return List.of();
        }
        Object raw = constraints.get("allowTools");
        if (raw instanceof List<?> list) {
            List<String> allowTools = new ArrayList<>();
            for (Object item : list) {
                String text = asText(item);
                if (StringUtils.hasText(text)) {
                    allowTools.add(text);
                }
            }
            return allowTools;
        }
        String single = asText(raw);
        if (StringUtils.hasText(single)) {
            return List.of(single);
        }
        return List.of();
    }

    private ModelToolChoice resolveToolChoiceFromConstraints(SkillDefinition skillDefinition) {
        if (skillDefinition == null || skillDefinition.getConstraints() == null) {
            return null;
        }
        return ModelToolChoice.fromRaw(skillDefinition.getConstraints().get(ContextRuntimeKeys.TOOL_CHOICE));
    }

    private String asText(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString();
        if (!StringUtils.hasText(text)) {
            return null;
        }
        return text.trim();
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
}
