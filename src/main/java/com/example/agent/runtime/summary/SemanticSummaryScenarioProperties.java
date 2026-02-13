package com.example.agent.runtime.summary;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 语义摘要场景化配置。
 *
 * <p>用途：根据场景为摘要生成提供可配置的长度与列表预算。</p>
 * <p>输入：配置中心或本地配置。</p>
 * <p>输出：解析后的场景策略。</p>
 */
@Component
@ConfigurationProperties(prefix = "agent.summary.scenario")
public class SemanticSummaryScenarioProperties {

    /**
     * 默认场景策略。
     */
    private ScenarioPolicy defaultPolicy = new ScenarioPolicy();

    /**
     * 场景策略映射（key 为场景编码）。
     */
    private Map<String, ScenarioPolicy> policies = new LinkedHashMap<>();

    public ScenarioPolicy getDefaultPolicy() {
        return defaultPolicy;
    }

    public void setDefaultPolicy(ScenarioPolicy defaultPolicy) {
        this.defaultPolicy = defaultPolicy;
    }

    public Map<String, ScenarioPolicy> getPolicies() {
        return policies;
    }

    public void setPolicies(Map<String, ScenarioPolicy> policies) {
        this.policies = policies;
    }

    /**
     * 解析场景策略。
     *
     * @param scenario 场景对象
     * @return 场景策略
     */
    public ScenarioPolicy resolvePolicy(SemanticSummaryScenario scenario) {
        // 判断场景是否为空或未配置，空时返回默认策略。
        if (scenario == null || !StringUtils.hasText(scenario.getCode())) {
            // 返回默认策略，保证摘要生成可用。
            return defaultPolicy;
        }
        // 读取场景编码，作为策略查找键。
        String code = scenario.getCode();
        // 判断策略映射是否为空，空时返回默认策略。
        if (policies == null || policies.isEmpty()) {
            // 返回默认策略，避免空指针。
            return defaultPolicy;
        }
        // 标准化编码键，确保大小写一致。
        String normalized = code.trim().toLowerCase();
        // 读取场景策略配置。
        ScenarioPolicy policy = policies.get(normalized);
        // 判断场景策略是否存在，存在时直接返回。
        if (policy != null) {
            // 返回命中的场景策略。
            return policy;
        }
        // 返回默认策略作为兜底。
        return defaultPolicy;
    }

    /**
     * 场景策略配置。
     */
    public static class ScenarioPolicy {

        /**
         * 摘要最大字符数。
         */
        private Integer maxChars;

        /**
         * 高亮列表最大数量。
         */
        private Integer maxHighlights;

        /**
         * 未解决问题列表最大数量。
         */
        private Integer maxOpenQuestions;

        /**
         * 风险列表最大数量。
         */
        private Integer maxRisks;

        /**
         * 来源引用最大数量。
         */
        private Integer maxSourceRefs;

        /**
         * 场景说明。
         */
        private String description;

        public Integer getMaxChars() {
            return maxChars;
        }

        public void setMaxChars(Integer maxChars) {
            this.maxChars = maxChars;
        }

        public Integer getMaxHighlights() {
            return maxHighlights;
        }

        public void setMaxHighlights(Integer maxHighlights) {
            this.maxHighlights = maxHighlights;
        }

        public Integer getMaxOpenQuestions() {
            return maxOpenQuestions;
        }

        public void setMaxOpenQuestions(Integer maxOpenQuestions) {
            this.maxOpenQuestions = maxOpenQuestions;
        }

        public Integer getMaxRisks() {
            return maxRisks;
        }

        public void setMaxRisks(Integer maxRisks) {
            this.maxRisks = maxRisks;
        }

        public Integer getMaxSourceRefs() {
            return maxSourceRefs;
        }

        public void setMaxSourceRefs(Integer maxSourceRefs) {
            this.maxSourceRefs = maxSourceRefs;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }
    }
}
