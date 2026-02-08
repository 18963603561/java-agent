package com.example.agent.capabilities.memory.model;

import java.util.List;

/**
 * 会话摘要结构化对象，用于承载压缩结果的稳定字段。
 * <p>
 * 说明：由压缩流程填充，字段可能为空，长度与数量受压缩策略影响。
 */
public class ConversationSummary {

    /**
     * 摘要版本，用于向后兼容与演进管理，默认值为 "v1"。
     */
    private String version;

    /**
     * 会话摘要文本，适合直接阅读，可能被截断。
     */
    private String summary;

    /**
     * 关键要点列表，用于结构化呈现，数量受压缩策略限制。
     */
    private List<String> bullets;

    /**
     * 摘要字符长度统计，基于 summary 计算。
     */
    private Integer summaryChars;

    /**
     * 要点数量统计，基于 bullets 计算。
     */
    private Integer bulletCount;

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

    public List<String> getBullets() {
        return bullets;
    }

    public void setBullets(List<String> bullets) {
        this.bullets = bullets;
    }

    public Integer getSummaryChars() {
        return summaryChars;
    }

    public void setSummaryChars(Integer summaryChars) {
        this.summaryChars = summaryChars;
    }

    public Integer getBulletCount() {
        return bulletCount;
    }

    public void setBulletCount(Integer bulletCount) {
        this.bulletCount = bulletCount;
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
        if (bullets == null || bullets.isEmpty()) {
            return null;
        }
        return String.join("\n", bullets);
    }
}
