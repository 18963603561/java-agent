package com.example.agent.capabilities.memory.model;

import java.util.List;

/**
 * 工作记忆结构化摘要，用于描述当前任务的关键内容。
 * <p>
 * 说明：由压缩流程填充，字段可能为空，长度与数量受压缩策略影响。
 */
public class WorkingMemorySummary {

    /**
     * 摘要版本，用于向后兼容与演进管理，默认值为 "v1"。
     */
    private String version;

    /**
     * 工作记忆摘要文本，可能被截断。
     */
    private String summary;

    /**
     * 工作记忆条目列表，建议为可执行或可复述的要点，数量受压缩策略限制。
     */
    private List<String> items;

    /**
     * 摘要字符长度统计，基于 summary 计算。
     */
    private Integer summaryChars;

    /**
     * 工作记忆条目数量统计，基于 items 计算。
     */
    private Integer itemCount;

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public List<String> getItems() {
        return items;
    }

    public void setItems(List<String> items) {
        this.items = items;
    }

    public Integer getSummaryChars() {
        return summaryChars;
    }

    public void setSummaryChars(Integer summaryChars) {
        this.summaryChars = summaryChars;
    }

    public Integer getItemCount() {
        return itemCount;
    }

    public void setItemCount(Integer itemCount) {
        this.itemCount = itemCount;
    }

    /**
     * 转换为兼容的旧版摘要文本。
     *
     * @return 旧版摘要文本
     */
    public String toLegacyText() {
        if (summary != null && !summary.isBlank()) {
            return summary;
        }
        if (items == null || items.isEmpty()) {
            return null;
        }
        return String.join("\n", items);
    }
}
