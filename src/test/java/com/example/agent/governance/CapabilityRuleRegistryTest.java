package com.example.agent.governance;

import com.example.agent.governance.evaluation.CapabilityEvaluationProperties;
import com.example.agent.governance.evaluation.domain.BudgetPressureRiskRule;
import com.example.agent.governance.evaluation.domain.CapabilityRuleBinding;
import com.example.agent.governance.evaluation.domain.CapabilityRuleRegistry;
import com.example.agent.governance.evaluation.domain.CapabilityRiskRule;
import com.example.agent.governance.evaluation.domain.CapabilityStrategyRule;
import com.example.agent.governance.evaluation.domain.ComplexityThresholdRiskRule;
import com.example.agent.governance.evaluation.domain.DebateKeywordStrategyRule;
import com.example.agent.governance.evaluation.domain.HighRiskThoughtTreeStrategyRule;
import com.example.agent.governance.evaluation.domain.ResearchKeywordStrategyRule;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 能力规则注册中心测试。
 */
class CapabilityRuleRegistryTest {

    @Test
    void shouldSortRulesByPriority() {
        CapabilityRuleRegistry registry = buildRegistry();
        CapabilityEvaluationProperties properties = new CapabilityEvaluationProperties();

        List<CapabilityRuleBinding<CapabilityRiskRule>> rules = registry.activeRiskRules("tenant-a", properties);

        assertEquals(List.of("complexity_threshold", "budget_pressure"),
                rules.stream().map(binding -> binding.getMetadata().getRuleId()).toList());
    }

    @Test
    void shouldApplyWhitelistAndBlacklist() {
        CapabilityRuleRegistry registry = buildRegistry();
        CapabilityEvaluationProperties properties = new CapabilityEvaluationProperties();
        CapabilityEvaluationProperties.RuleSelection ruleSelection = new CapabilityEvaluationProperties.RuleSelection();
        ruleSelection.setWhitelist(List.of("budget_pressure", "complexity_threshold"));
        ruleSelection.setBlacklist(List.of("complexity_threshold"));
        properties.setRiskRules(ruleSelection);

        List<CapabilityRuleBinding<CapabilityRiskRule>> rules = registry.activeRiskRules("tenant-a", properties);

        assertEquals(1, rules.size());
        assertEquals("budget_pressure", rules.get(0).getMetadata().getRuleId());
    }

    @Test
    void shouldApplyTenantOverride() {
        CapabilityRuleRegistry registry = buildRegistry();
        CapabilityEvaluationProperties properties = new CapabilityEvaluationProperties();

        CapabilityEvaluationProperties.RuleToggle tenantToggle = new CapabilityEvaluationProperties.RuleToggle();
        CapabilityEvaluationProperties.RuleSelection tenantRiskSelection = new CapabilityEvaluationProperties.RuleSelection();
        tenantRiskSelection.setWhitelist(List.of("budget_pressure"));
        tenantToggle.setRiskRules(tenantRiskSelection);
        properties.setTenants(Map.of("tenant-special", tenantToggle));

        List<CapabilityRuleBinding<CapabilityRiskRule>> tenantRules = registry.activeRiskRules(
                "tenant-special",
                properties);
        List<CapabilityRuleBinding<CapabilityRiskRule>> defaultRules = registry.activeRiskRules(
                "tenant-default",
                properties);

        assertEquals(1, tenantRules.size());
        assertEquals("budget_pressure", tenantRules.get(0).getMetadata().getRuleId());
        assertEquals(2, defaultRules.size());
    }

    @Test
    void shouldApplySceneOverrideForStrategyRules() {
        CapabilityRuleRegistry registry = buildRegistry();
        CapabilityEvaluationProperties properties = new CapabilityEvaluationProperties();

        CapabilityEvaluationProperties.RuleToggle sceneToggle = new CapabilityEvaluationProperties.RuleToggle();
        CapabilityEvaluationProperties.RuleSelection sceneStrategySelection = new CapabilityEvaluationProperties.RuleSelection();
        sceneStrategySelection.setWhitelist(List.of("debate_keyword"));
        sceneToggle.setStrategyRules(sceneStrategySelection);
        properties.setScenes(Map.of("analysis", sceneToggle));

        List<CapabilityRuleBinding<CapabilityStrategyRule>> strategyRules = registry.activeStrategyRules(
                "tenant-a",
                "analysis",
                properties);

        assertEquals(1, strategyRules.size());
        assertEquals("debate_keyword", strategyRules.get(0).getMetadata().getRuleId());
    }

    @Test
    void shouldDisableAllRiskRulesWhenSelectionDisabled() {
        CapabilityRuleRegistry registry = buildRegistry();
        CapabilityEvaluationProperties properties = new CapabilityEvaluationProperties();
        CapabilityEvaluationProperties.RuleSelection ruleSelection = new CapabilityEvaluationProperties.RuleSelection();
        ruleSelection.setEnabled(false);
        properties.setRiskRules(ruleSelection);

        List<CapabilityRuleBinding<CapabilityRiskRule>> rules = registry.activeRiskRules("tenant-a", properties);

        assertTrue(rules.isEmpty());
    }

    private CapabilityRuleRegistry buildRegistry() {
        return new CapabilityRuleRegistry(
                List.of(new BudgetPressureRiskRule(), new ComplexityThresholdRiskRule()),
                List.of(new HighRiskThoughtTreeStrategyRule(), new DebateKeywordStrategyRule(), new ResearchKeywordStrategyRule()));
    }
}

