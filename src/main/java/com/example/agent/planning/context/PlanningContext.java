package com.example.agent.planning.context;

import com.example.agent.budget.token.ContextBudgetAllocation;
import com.example.agent.budget.trim.ContextPruneResult;
import com.example.agent.capabilities.context.ContextSnapshot;
import com.example.agent.capabilities.llm.contract.ModelToolChoice;
import com.example.agent.planning.PlanningContextKeys;
import com.example.agent.planning.PlanningFieldKeys;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.util.StringUtils;

/**
 * 规划上下文强类型对象。
 *
 * <p>用途：封装规划阶段常用字段与访问逻辑，降低核心链路对裸 Map 的依赖。
 * <p>输入：运行时上下文映射。
 * <p>输出：类型化字段访问能力与可回写上下文。
 */
public class PlanningContext {

    private final Map<String, Object> values;

    /**
     * 构造规划上下文。
     *
     * @param values 上下文映射
     */
    public PlanningContext(Map<String, Object> values) {
        this.values = values != null ? values : new HashMap<>();
    }

    /**
     * 返回可回写上下文。
     *
     * @return 可变上下文映射
     */
    public Map<String, Object> mutableValues() {
        return values;
    }

    /**
     * 判断上下文是否包含键。
     *
     * @param key 上下文键
     * @return 是否包含
     */
    public boolean containsKey(String key) {
        return values.containsKey(key);
    }

    /**
     * 写入上下文值。
     *
     * @param key 上下文键
     * @param value 上下文值
     */
    public void put(String key, Object value) {
        values.put(key, value);
    }

    /**
     * 按需写入上下文值。
     *
     * @param key 上下文键
     * @param value 上下文值
     */
    public void putIfAbsent(String key, Object value) {
        values.putIfAbsent(key, value);
    }

    /**
     * 读取原始值。
     *
     * @param key 上下文键
     * @return 原始对象
     */
    public Object get(String key) {
        return values.get(key);
    }

    /**
     * 读取字符串值。
     *
     * @param key 上下文键
     * @return 字符串值
     */
    public String getString(String key) {
        Object value = values.get(key);
        if (value instanceof String text && StringUtils.hasText(text)) {
            return text;
        }
        return null;
    }

    /**
     * 读取字符串值并转小写。
     *
     * @param key 上下文键
     * @return 小写字符串
     */
    public String getLowercaseString(String key) {
        String value = getString(key);
        return value != null ? value.toLowerCase(Locale.ROOT) : null;
    }

    /**
     * 读取布尔值。
     *
     * @param key 上下文键
     * @return 布尔值
     */
    public Boolean getBoolean(String key) {
        Object value = values.get(key);
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String text && !text.isBlank()) {
            return Boolean.parseBoolean(text.trim());
        }
        return null;
    }

    /**
     * 读取整数值。
     *
     * @param key 上下文键
     * @return 整数值
     */
    public Integer getInteger(String key) {
        Object value = values.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /**
     * 读取字符串列表。
     *
     * @param key 上下文键
     * @return 字符串列表
     */
    public List<String> getStringList(String key) {
        Object value = values.get(key);
        if (value instanceof List<?> list) {
            List<String> output = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof String text && StringUtils.hasText(text)) {
                    output.add(text);
                }
            }
            return output;
        }
        if (value instanceof String text && StringUtils.hasText(text)) {
            return List.of(text.trim());
        }
        return List.of();
    }

    /**
     * 读取工具选择。
     *
     * @return 工具选择
     */
    public ModelToolChoice getToolChoice() {
        Object raw = values.get(PlanningContextKeys.TOOL_CHOICE);
        return parseToolChoice(raw);
    }

    /**
     * 解析上下文快照。
     *
     * @return 上下文快照
     */
    public ContextSnapshot getContextSnapshot() {
        Object value = values.get(PlanningContextKeys.CONTEXT_SNAPSHOT);
        if (value instanceof ContextSnapshot snapshot) {
            return snapshot;
        }
        return null;
    }

    /**
     * 解析上下文预算对象。
     *
     * @return 上下文预算
     */
    public ContextBudgetAllocation getContextBudget() {
        Object value = values.get(PlanningContextKeys.CONTEXT_BUDGET);
        if (value instanceof ContextBudgetAllocation allocation) {
            return allocation;
        }
        return null;
    }

    /**
     * 解析上下文裁剪对象。
     *
     * @return 上下文裁剪结果
     */
    public ContextPruneResult getContextPrune() {
        Object value = values.get(PlanningContextKeys.CONTEXT_PRUNE);
        if (value instanceof ContextPruneResult pruneResult) {
            return pruneResult;
        }
        return null;
    }

    /**
     * 读取工具名列表。
     *
     * @return 工具名列表
     */
    public List<String> getTools() {
        List<String> tools = new ArrayList<>();
        addToolName(tools, values.get(PlanningContextKeys.TOOL));
        addToolName(tools, values.get(PlanningContextKeys.TOOL_NAME));
        Object toolsObj = values.get(PlanningContextKeys.TOOLS);
        if (toolsObj instanceof List<?> list) {
            for (Object item : list) {
                addToolName(tools, item);
            }
        }
        return tools;
    }

    private void addToolName(List<String> tools, Object value) {
        if (value == null) {
            return;
        }
        String text = value.toString();
        if (!StringUtils.hasText(text) || tools.contains(text)) {
            return;
        }
        tools.add(text);
    }

    private ModelToolChoice parseToolChoice(Object raw) {
        return ModelToolChoice.fromRaw(raw);
    }

    /**
     * 读取证据包对象。
     *
     * @return 证据包对象
     */
    public com.example.agent.capabilities.context.EvidencePack getEvidencePack() {
        Object value = values.get(PlanningContextKeys.EVIDENCE_PACK);
        if (value instanceof com.example.agent.capabilities.context.EvidencePack pack) {
            return pack;
        }
        return null;
    }

    /**
     * 读取记忆统计数量。
     *
     * @return 记忆条目数
     */
    public Integer getMemoryCount() {
        Object memoryObj = values.get(PlanningContextKeys.MEMORY);
        if (memoryObj instanceof Map<?, ?> memoryMap) {
            Object count = memoryMap.get(PlanningContextKeys.COUNT);
            if (count instanceof Number number) {
                return number.intValue();
            }
            if (count != null) {
                try {
                    return Integer.parseInt(count.toString());
                } catch (NumberFormatException ex) {
                    return null;
                }
            }
        }
        ContextSnapshot snapshot = getContextSnapshot();
        if (snapshot != null && snapshot.getWorkingMemory() != null) {
            return snapshot.getWorkingMemory().getWorkingMemoryItems();
        }
        return null;
    }

    /**
     * 读取证据条目数量。
     *
     * @return 证据数量
     */
    public Integer getEvidenceCount() {
        var evidencePack = getEvidencePack();
        if (evidencePack == null) {
            return null;
        }
        var stats = evidencePack.getStats();
        if (stats != null && stats.getResearchCount() != null) {
            return stats.getResearchCount();
        }
        if (stats != null && stats.getTotalCount() != null) {
            return stats.getTotalCount();
        }
        if (evidencePack.getEvidences() != null) {
            return evidencePack.getEvidences().size();
        }
        return null;
    }

    /**
     * 读取工具选择模式名称。
     *
     * @return 模式名称
     */
    public String getToolChoiceMode() {
        ModelToolChoice toolChoice = getToolChoice();
        if (toolChoice == null || toolChoice.getMode() == null) {
            return null;
        }
        return toolChoice.getMode().name().toLowerCase(Locale.ROOT);
    }

    /**
     * 读取工具选择指定名称。
     *
     * @return 指定工具名称
     */
    public String getToolChoiceToolName() {
        ModelToolChoice toolChoice = getToolChoice();
        if (toolChoice == null || !StringUtils.hasText(toolChoice.getToolName())) {
            return null;
        }
        return toolChoice.getToolName().trim();
    }

    /**
     * 读取提示词摘要上下文。
     *
     * @return 摘要上下文映射
     */
    public Map<String, Object> toPromptSummary() {
        Map<String, Object> summary = new HashMap<>();
        String snapshotId = getString(PlanningContextKeys.SNAPSHOT_ID);
        if (snapshotId == null) {
            ContextSnapshot snapshot = getContextSnapshot();
            if (snapshot != null && StringUtils.hasText(snapshot.getSnapshotId())) {
                snapshotId = snapshot.getSnapshotId();
            }
        }
        if (StringUtils.hasText(snapshotId)) {
            summary.put(PlanningContextKeys.SNAPSHOT_ID, snapshotId);
        }
        ContextBudgetAllocation allocation = getContextBudget();
        Integer tokenBudget = allocation != null ? allocation.getTotalTokens() : null;
        if (tokenBudget == null) {
            tokenBudget = getInteger(PlanningContextKeys.BUDGET_THRESHOLD_TOKENS);
        }
        if (tokenBudget != null) {
            summary.put(PlanningFieldKeys.TOKEN_BUDGET, tokenBudget);
        }
        Integer memoryItems = getMemoryCount();
        if (memoryItems != null) {
            summary.put(PlanningFieldKeys.MEMORY_ITEMS, memoryItems);
        }
        List<String> tools = getTools();
        if (!tools.isEmpty()) {
            summary.put(PlanningContextKeys.TOOLS, tools);
        }
        Integer evidenceCount = getEvidenceCount();
        if (evidenceCount != null) {
            summary.put(PlanningFieldKeys.EVIDENCE_COUNT, evidenceCount);
        }
        if (summary.isEmpty()) {
            summary.put(PlanningFieldKeys.SUMMARY, "(summary disabled)");
        }
        return summary;
    }
}
