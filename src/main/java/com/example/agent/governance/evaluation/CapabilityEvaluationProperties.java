package com.example.agent.governance.evaluation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 能力边界评估配置，用于控制评估启用与风险阈值。
 */
@Component
@ConfigurationProperties(prefix = "agent.capability-evaluation")
public class CapabilityEvaluationProperties {

    /**
     * 是否启用能力边界评估。
     */
    private boolean enabled = true;

    /**
     * 风险阈值，超过该值视为高风险。
     */
    private double riskThreshold = 0.7;

    /**
     * 复杂度阈值，超过该值触发高复杂度策略。
     */
    private double complexityThreshold = 0.7;

    /**
     * 高风险时是否强制触发审批。
     */
    private boolean forceApprovalAboveRisk = true;

    /**
     * 预算阈值，用于风险评估参考。
     */
    private int budgetThresholdTokens = 10000;

    /**
     * 默认推荐策略。
     */
    private String defaultStrategy = "tool";

    /**
     * 风险基线分。
     */
    private double baseRiskScore = 0.2;

    /**
     * 复杂度超阈值风险权重。
     */
    private double complexityRiskWeight = 0.4;

    /**
     * 缺少工具摘要风险权重。
     */
    private double missingToolSummaryRiskWeight = 0.2;

    /**
     * 单个失败类型风险权重。
     */
    private double failureTypeRiskWeightPerItem = 0.05;

    /**
     * 失败类型风险上限。
     */
    private double failureTypeRiskWeightMax = 0.2;

    /**
     * 预算风险复杂度阈值。
     */
    private double budgetRiskComplexityThreshold = 0.7;

    /**
     * 预算风险令牌阈值。
     */
    private int budgetRiskTokenThreshold = 3000;

    /**
     * 预算风险权重。
     */
    private double budgetRiskWeight = 0.1;

    /**
     * 高风险默认策略。
     */
    private String highRiskStrategy = "thought_tree";

    /**
     * 调研关键词。
     */
    private List<String> researchKeywords = new ArrayList<>(List.of("调研", "研究", "资料", "来源", "证据", "报告"));

    /**
     * 辩论关键词。
     */
    private List<String> debateKeywords = new ArrayList<>(List.of("辩论", "利弊", "对比", "比较", "观点"));

    /**
     * 风险规则启停与过滤配置。
     */
    private RuleSelection riskRules = new RuleSelection();

    /**
     * 策略规则启停与过滤配置。
     */
    private RuleSelection strategyRules = new RuleSelection();

    /**
     * 按租户覆盖规则集。
     */
    private Map<String, RuleToggle> tenants = new LinkedHashMap<>();

    /**
     * 按场景覆盖规则集。
     */
    private Map<String, RuleToggle> scenes = new LinkedHashMap<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public double getRiskThreshold() {
        return riskThreshold;
    }

    public void setRiskThreshold(double riskThreshold) {
        this.riskThreshold = riskThreshold;
    }

    public double getComplexityThreshold() {
        return complexityThreshold;
    }

    public void setComplexityThreshold(double complexityThreshold) {
        this.complexityThreshold = complexityThreshold;
    }

    public boolean isForceApprovalAboveRisk() {
        return forceApprovalAboveRisk;
    }

    public void setForceApprovalAboveRisk(boolean forceApprovalAboveRisk) {
        this.forceApprovalAboveRisk = forceApprovalAboveRisk;
    }

    public int getBudgetThresholdTokens() {
        return budgetThresholdTokens;
    }

    public void setBudgetThresholdTokens(int budgetThresholdTokens) {
        this.budgetThresholdTokens = budgetThresholdTokens;
    }

    public String getDefaultStrategy() {
        return defaultStrategy;
    }

    public void setDefaultStrategy(String defaultStrategy) {
        this.defaultStrategy = defaultStrategy;
    }

    public double getBaseRiskScore() {
        return baseRiskScore;
    }

    public void setBaseRiskScore(double baseRiskScore) {
        this.baseRiskScore = baseRiskScore;
    }

    public double getComplexityRiskWeight() {
        return complexityRiskWeight;
    }

    public void setComplexityRiskWeight(double complexityRiskWeight) {
        this.complexityRiskWeight = complexityRiskWeight;
    }

    public double getMissingToolSummaryRiskWeight() {
        return missingToolSummaryRiskWeight;
    }

    public void setMissingToolSummaryRiskWeight(double missingToolSummaryRiskWeight) {
        this.missingToolSummaryRiskWeight = missingToolSummaryRiskWeight;
    }

    public double getFailureTypeRiskWeightPerItem() {
        return failureTypeRiskWeightPerItem;
    }

    public void setFailureTypeRiskWeightPerItem(double failureTypeRiskWeightPerItem) {
        this.failureTypeRiskWeightPerItem = failureTypeRiskWeightPerItem;
    }

    public double getFailureTypeRiskWeightMax() {
        return failureTypeRiskWeightMax;
    }

    public void setFailureTypeRiskWeightMax(double failureTypeRiskWeightMax) {
        this.failureTypeRiskWeightMax = failureTypeRiskWeightMax;
    }

    public double getBudgetRiskComplexityThreshold() {
        return budgetRiskComplexityThreshold;
    }

    public void setBudgetRiskComplexityThreshold(double budgetRiskComplexityThreshold) {
        this.budgetRiskComplexityThreshold = budgetRiskComplexityThreshold;
    }

    public int getBudgetRiskTokenThreshold() {
        return budgetRiskTokenThreshold;
    }

    public void setBudgetRiskTokenThreshold(int budgetRiskTokenThreshold) {
        this.budgetRiskTokenThreshold = budgetRiskTokenThreshold;
    }

    public double getBudgetRiskWeight() {
        return budgetRiskWeight;
    }

    public void setBudgetRiskWeight(double budgetRiskWeight) {
        this.budgetRiskWeight = budgetRiskWeight;
    }

    public String getHighRiskStrategy() {
        return highRiskStrategy;
    }

    public void setHighRiskStrategy(String highRiskStrategy) {
        this.highRiskStrategy = highRiskStrategy;
    }

    public List<String> getResearchKeywords() {
        return researchKeywords;
    }

    public void setResearchKeywords(List<String> researchKeywords) {
        this.researchKeywords = researchKeywords;
    }

    public List<String> getDebateKeywords() {
        return debateKeywords;
    }

    public void setDebateKeywords(List<String> debateKeywords) {
        this.debateKeywords = debateKeywords;
    }

    public RuleSelection getRiskRules() {
        return riskRules;
    }

    public void setRiskRules(RuleSelection riskRules) {
        this.riskRules = riskRules;
    }

    public RuleSelection getStrategyRules() {
        return strategyRules;
    }

    public void setStrategyRules(RuleSelection strategyRules) {
        this.strategyRules = strategyRules;
    }

    public Map<String, RuleToggle> getTenants() {
        return tenants;
    }

    public void setTenants(Map<String, RuleToggle> tenants) {
        this.tenants = tenants;
    }

    public Map<String, RuleToggle> getScenes() {
        return scenes;
    }

    public void setScenes(Map<String, RuleToggle> scenes) {
        this.scenes = scenes;
    }

    /**
     * 单类规则选择配置。
     */
    public static class RuleSelection {

        /**
         * 是否启用该类规则。
         */
        private boolean enabled = true;

        /**
         * 规则白名单，非空时仅白名单生效。
         */
        private List<String> whitelist = new ArrayList<>();

        /**
         * 规则黑名单，黑名单优先于白名单。
         */
        private List<String> blacklist = new ArrayList<>();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public List<String> getWhitelist() {
            return whitelist;
        }

        public void setWhitelist(List<String> whitelist) {
            this.whitelist = whitelist;
        }

        public List<String> getBlacklist() {
            return blacklist;
        }

        public void setBlacklist(List<String> blacklist) {
            this.blacklist = blacklist;
        }
    }

    /**
     * 覆盖规则配置。
     */
    public static class RuleToggle {

        /**
         * 风险规则覆盖配置。
         */
        private RuleSelection riskRules = new RuleSelection();

        /**
         * 策略规则覆盖配置。
         */
        private RuleSelection strategyRules = new RuleSelection();

        public RuleSelection getRiskRules() {
            return riskRules;
        }

        public void setRiskRules(RuleSelection riskRules) {
            this.riskRules = riskRules;
        }

        public RuleSelection getStrategyRules() {
            return strategyRules;
        }

        public void setStrategyRules(RuleSelection strategyRules) {
            this.strategyRules = strategyRules;
        }
    }
}
