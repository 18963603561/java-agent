package com.example.agent.governance.evaluation.domain;

import com.example.agent.governance.evaluation.CapabilityEvaluationProperties;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 能力评估规则注册中心。
 */
@Component
public class CapabilityRuleRegistry {

    private static final Logger log = LoggerFactory.getLogger(CapabilityRuleRegistry.class);

    private final List<CapabilityRiskRule> riskRules;
    private final List<CapabilityStrategyRule> strategyRules;

    public CapabilityRuleRegistry(List<CapabilityRiskRule> riskRules,
                                  List<CapabilityStrategyRule> strategyRules) {
        this.riskRules = riskRules != null ? List.copyOf(riskRules) : List.of();
        this.strategyRules = strategyRules != null ? List.copyOf(strategyRules) : List.of();
    }

    /**
     * 获取生效风险规则。
     *
     * @param tenantId 租户标识
     * @param scene 场景标识
     * @param properties 评估配置
     * @return 生效规则绑定列表
     */
    public List<CapabilityRuleBinding<CapabilityRiskRule>> activeRiskRules(String tenantId,
                                                                           String scene,
                                                                           CapabilityEvaluationProperties properties) {
        CapabilityEvaluationProperties.RuleSelection selection = resolveRuleSelection(
                tenantId,
                scene,
                properties,
                CapabilityRuleType.RISK);
        return riskRules.stream()
                .filter(rule -> rule != null && StringUtils.hasText(rule.ruleId()))
                .map(rule -> new CapabilityRuleBinding<>(
                        rule,
                        new CapabilityRuleMetadata(
                                rule.ruleId(),
                                CapabilityRuleType.RISK,
                                rule.version(),
                                rule.priority(),
                                rule.enabled())))
                .filter(binding -> isEnabled(binding.getMetadata(), selection))
                .sorted(Comparator.comparingInt(binding -> binding.getMetadata().getPriority()))
                .toList();
    }

    /**
     * 获取生效风险规则（兼容无场景参数调用）。
     *
     * @param tenantId 租户标识
     * @param properties 评估配置
     * @return 生效规则绑定列表
     */
    public List<CapabilityRuleBinding<CapabilityRiskRule>> activeRiskRules(String tenantId,
                                                                           CapabilityEvaluationProperties properties) {
        return activeRiskRules(tenantId, null, properties);
    }

    /**
     * 获取生效策略规则。
     *
     * @param tenantId 租户标识
     * @param scene 场景标识
     * @param properties 评估配置
     * @return 生效规则绑定列表
     */
    public List<CapabilityRuleBinding<CapabilityStrategyRule>> activeStrategyRules(String tenantId,
                                                                                   String scene,
                                                                                   CapabilityEvaluationProperties properties) {
        CapabilityEvaluationProperties.RuleSelection selection = resolveRuleSelection(
                tenantId,
                scene,
                properties,
                CapabilityRuleType.STRATEGY);
        return strategyRules.stream()
                .filter(rule -> rule != null && StringUtils.hasText(rule.ruleId()))
                .map(rule -> new CapabilityRuleBinding<>(
                        rule,
                        new CapabilityRuleMetadata(
                                rule.ruleId(),
                                CapabilityRuleType.STRATEGY,
                                rule.version(),
                                rule.priority(),
                                rule.enabled())))
                .filter(binding -> isEnabled(binding.getMetadata(), selection))
                .sorted(Comparator.comparingInt(binding -> binding.getMetadata().getPriority()))
                .toList();
    }

    /**
     * 获取生效策略规则（兼容无场景参数调用）。
     *
     * @param tenantId 租户标识
     * @param properties 评估配置
     * @return 生效规则绑定列表
     */
    public List<CapabilityRuleBinding<CapabilityStrategyRule>> activeStrategyRules(String tenantId,
                                                                                   CapabilityEvaluationProperties properties) {
        return activeStrategyRules(tenantId, null, properties);
    }

    private CapabilityEvaluationProperties.RuleSelection resolveRuleSelection(String tenantId,
                                                                              String scene,
                                                                              CapabilityEvaluationProperties properties,
                                                                              CapabilityRuleType ruleType) {
        if (properties == null) {
            return new CapabilityEvaluationProperties.RuleSelection();
        }
        CapabilityEvaluationProperties.RuleSelection baseSelection = ruleType == CapabilityRuleType.RISK
                ? properties.getRiskRules()
                : properties.getStrategyRules();
        CapabilityEvaluationProperties.RuleSelection merged = copyOf(baseSelection);
        CapabilityEvaluationProperties.RuleSelection tenantSelection = resolveTenantSelection(tenantId, properties, ruleType);
        mergeInto(merged, tenantSelection);
        CapabilityEvaluationProperties.RuleSelection sceneSelection = resolveSceneSelection(scene, properties, ruleType);
        mergeInto(merged, sceneSelection);
        return merged;
    }

    private CapabilityEvaluationProperties.RuleSelection resolveTenantSelection(String tenantId,
                                                                                CapabilityEvaluationProperties properties,
                                                                                CapabilityRuleType ruleType) {
        if (!StringUtils.hasText(tenantId)
                || properties == null
                || properties.getTenants() == null
                || properties.getTenants().isEmpty()) {
            return null;
        }
        CapabilityEvaluationProperties.RuleToggle toggle = properties.getTenants().get(tenantId);
        if (toggle == null) {
            return null;
        }
        return ruleType == CapabilityRuleType.RISK ? toggle.getRiskRules() : toggle.getStrategyRules();
    }

    private CapabilityEvaluationProperties.RuleSelection resolveSceneSelection(String scene,
                                                                               CapabilityEvaluationProperties properties,
                                                                               CapabilityRuleType ruleType) {
        if (!StringUtils.hasText(scene)
                || properties == null
                || properties.getScenes() == null
                || properties.getScenes().isEmpty()) {
            return null;
        }
        CapabilityEvaluationProperties.RuleToggle toggle = properties.getScenes().get(scene);
        if (toggle == null) {
            return null;
        }
        return ruleType == CapabilityRuleType.RISK ? toggle.getRiskRules() : toggle.getStrategyRules();
    }

    private CapabilityEvaluationProperties.RuleSelection copyOf(CapabilityEvaluationProperties.RuleSelection selection) {
        CapabilityEvaluationProperties.RuleSelection copied = new CapabilityEvaluationProperties.RuleSelection();
        if (selection == null) {
            return copied;
        }
        copied.setEnabled(selection.isEnabled());
        copied.setWhitelist(copyIds(selection.getWhitelist()));
        copied.setBlacklist(copyIds(selection.getBlacklist()));
        return copied;
    }

    private void mergeInto(CapabilityEvaluationProperties.RuleSelection base,
                           CapabilityEvaluationProperties.RuleSelection override) {
        if (base == null || override == null) {
            return;
        }
        if (override.isEnabled() != base.isEnabled()) {
            base.setEnabled(override.isEnabled());
        }
        if (override.getWhitelist() != null && !override.getWhitelist().isEmpty()) {
            base.setWhitelist(copyIds(override.getWhitelist()));
        }
        if (override.getBlacklist() != null && !override.getBlacklist().isEmpty()) {
            Set<String> merged = new LinkedHashSet<>(copyIds(base.getBlacklist()));
            merged.addAll(copyIds(override.getBlacklist()));
            base.setBlacklist(new ArrayList<>(merged));
        }
    }

    private boolean isEnabled(CapabilityRuleMetadata metadata,
                              CapabilityEvaluationProperties.RuleSelection selection) {
        if (metadata == null || !StringUtils.hasText(metadata.getRuleId())) {
            return false;
        }
        if (!metadata.isEnabled()) {
            return false;
        }
        if (selection == null) {
            return true;
        }
        if (!selection.isEnabled()) {
            return false;
        }
        List<String> whitelist = copyIds(selection.getWhitelist());
        if (!whitelist.isEmpty() && !whitelist.contains(metadata.getRuleId())) {
            return false;
        }
        List<String> blacklist = copyIds(selection.getBlacklist());
        if (blacklist.contains(metadata.getRuleId())) {
            return false;
        }
        return true;
    }

    private List<String> copyIds(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<String> results = new ArrayList<>();
        for (String id : ids) {
            if (!StringUtils.hasText(id)) {
                continue;
            }
            results.add(id.trim());
        }
        return results;
    }

    /**
     * 输出当前规则注册摘要日志。
     *
     * @param tenantId 租户标识
     * @param scene 场景标识
     * @param properties 评估配置
     */
    public void logRegistrySummary(String tenantId, String scene, CapabilityEvaluationProperties properties) {
        List<CapabilityRuleBinding<CapabilityRiskRule>> activeRiskRules = activeRiskRules(tenantId, scene, properties);
        List<CapabilityRuleBinding<CapabilityStrategyRule>> activeStrategyRules = activeStrategyRules(tenantId, scene, properties);
        log.info("能力规则注册中心生效摘要, tenantId={}, scene={}, riskRules={}, strategyRules={}",
                tenantId,
                scene,
                activeRiskRules.stream().map(binding -> binding.getMetadata().getRuleId()).toList(),
                activeStrategyRules.stream().map(binding -> binding.getMetadata().getRuleId()).toList());
    }
}
