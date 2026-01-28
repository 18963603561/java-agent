package com.example.agent.budget;

import java.util.EnumMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 上下文预算配置，用于控制分配开关与默认比例。
 */
@Component
@ConfigurationProperties(prefix = "agent.context.budget")
public class ContextBudgetProperties {

    /**
     * 是否启用上下文预算分配。
     */
    private boolean enabled = true;

    /**
     * 总预算令牌数，需大于等于 0，0 表示不分配预算。
     */
    private int totalBudgetTokens = 8192;

    /**
     * 分段比例配置，比例之和应不超过 1。
     */
    private Ratios ratios = new Ratios();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getTotalBudgetTokens() {
        return totalBudgetTokens;
    }

    public void setTotalBudgetTokens(int totalBudgetTokens) {
        this.totalBudgetTokens = totalBudgetTokens;
    }

    public Ratios getRatios() {
        return ratios;
    }

    public void setRatios(Ratios ratios) {
        this.ratios = ratios;
    }

    /**
     * 将配置转换为预算策略。
     *
     * @return 预算策略
     */
    public ContextBudgetPolicy toPolicy() {
        ContextBudgetPolicy policy = new ContextBudgetPolicy();
        policy.setSectionRatios(buildRatioMap());
        return policy;
    }

    /**
     * 构建分段比例映射，未设置的分段默认为 0。
     *
     * @return 分段比例映射
     */
    public Map<ContextSection, Double> buildRatioMap() {
        EnumMap<ContextSection, Double> ratiosMap = new EnumMap<>(ContextSection.class);
        Ratios ratioConfig = ratios == null ? new Ratios() : ratios;
        ratiosMap.put(ContextSection.SYSTEM_POLICY, ratioConfig.getSystemPolicy());
        ratiosMap.put(ContextSection.DEVELOPER_POLICY, ratioConfig.getDeveloperPolicy());
        ratiosMap.put(ContextSection.USER_INPUT, ratioConfig.getTaskIntent());
        ratiosMap.put(ContextSection.WORKING_MEMORY, ratioConfig.getWorkingMemory());
        ratiosMap.put(ContextSection.DOMAIN_KNOWLEDGE, ratioConfig.getDomainKnowledge());
        ratiosMap.put(ContextSection.LONG_TERM_MEMORY, ratioConfig.getLongTermMemory());
        ratiosMap.put(ContextSection.TOOL_SUMMARY, ratioConfig.getToolSummaries());
        ratiosMap.put(ContextSection.TOOL_SCHEMA, ratioConfig.getToolSchema());
        ratiosMap.put(ContextSection.EVIDENCE_PACK, ratioConfig.getEvidencePack());
        ratiosMap.put(ContextSection.SLACK, ratioConfig.getSlack());
        return ratiosMap;
    }

    /**
     * 分段比例配置。
     */
    public static class Ratios {

        /**
         * 系统策略分段比例。
         */
        private double systemPolicy = 0.05;

        /**
         * 开发者策略分段比例。
         */
        private double developerPolicy = 0.05;

        /**
         * 任务意图分段比例。
         */
        private double taskIntent = 0.40;

        /**
         * 工作记忆分段比例。
         */
        private double workingMemory = 0.20;

        /**
         * 领域知识分段比例。
         */
        private double domainKnowledge = 0.10;

        /**
         * 长期记忆分段比例。
         */
        private double longTermMemory = 0.10;

        /**
         * 工具摘要分段比例。
         */
        private double toolSummaries = 0.05;

        /**
         * 工具模式分段比例。
         */
        private double toolSchema = 0.03;

        /**
         * 证据包分段比例。
         */
        private double evidencePack = 0.02;

        /**
         * 余量分段比例。
         */
        private double slack = 0.0;

        public double getSystemPolicy() {
            return systemPolicy;
        }

        public void setSystemPolicy(double systemPolicy) {
            this.systemPolicy = systemPolicy;
        }

        public double getDeveloperPolicy() {
            return developerPolicy;
        }

        public void setDeveloperPolicy(double developerPolicy) {
            this.developerPolicy = developerPolicy;
        }

        public double getTaskIntent() {
            return taskIntent;
        }

        public void setTaskIntent(double taskIntent) {
            this.taskIntent = taskIntent;
        }

        public double getWorkingMemory() {
            return workingMemory;
        }

        public void setWorkingMemory(double workingMemory) {
            this.workingMemory = workingMemory;
        }

        public double getDomainKnowledge() {
            return domainKnowledge;
        }

        public void setDomainKnowledge(double domainKnowledge) {
            this.domainKnowledge = domainKnowledge;
        }

        public double getLongTermMemory() {
            return longTermMemory;
        }

        public void setLongTermMemory(double longTermMemory) {
            this.longTermMemory = longTermMemory;
        }

        public double getToolSummaries() {
            return toolSummaries;
        }

        public void setToolSummaries(double toolSummaries) {
            this.toolSummaries = toolSummaries;
        }

        public double getToolSchema() {
            return toolSchema;
        }

        public void setToolSchema(double toolSchema) {
            this.toolSchema = toolSchema;
        }

        public double getEvidencePack() {
            return evidencePack;
        }

        public void setEvidencePack(double evidencePack) {
            this.evidencePack = evidencePack;
        }

        public double getSlack() {
            return slack;
        }

        public void setSlack(double slack) {
            this.slack = slack;
        }
    }
}
