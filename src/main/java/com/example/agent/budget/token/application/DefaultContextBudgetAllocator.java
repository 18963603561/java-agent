package com.example.agent.budget.token.application;

import com.example.agent.budget.core.ContextBudgetAllocation;
import com.example.agent.budget.core.ContextBudgetAllocationState;
import com.example.agent.budget.core.ContextBudgetPolicy;
import com.example.agent.budget.config.ContextBudgetProperties;
import com.example.agent.budget.core.ContextSection;
import com.example.agent.streaming.observability.MetricsPublisher;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;


/**
 * 默认上下文预算分配器。
 */
@Service
public class DefaultContextBudgetAllocator implements ContextBudgetAllocator {

    private static final Logger log = LoggerFactory.getLogger(DefaultContextBudgetAllocator.class);

    private final ContextBudgetProperties properties;
    private final MetricsPublisher metricsPublisher;
    private final TokenBudgetManager tokenBudgetManager;

    public DefaultContextBudgetAllocator(ContextBudgetProperties properties,
                                         MetricsPublisher metricsPublisher,
                                         TokenBudgetManager tokenBudgetManager) {
        this.properties = properties;
        this.metricsPublisher = metricsPublisher;
        this.tokenBudgetManager = tokenBudgetManager;
    }

    @Override
    public ContextBudgetAllocation allocate(ContextBudgetRequest request) {
        if (request == null || !request.isEnabled()) {
            return ContextBudgetAllocation.disabled(
                    ContextBudgetAllocationState.DISABLED_BY_REQUEST,
                    "request_disabled");
        }
        if (properties != null && !properties.isEnabled()) {
            return ContextBudgetAllocation.disabled(
                    ContextBudgetAllocationState.DISABLED_BY_CONFIG,
                    "config_disabled");
        }
        int total = resolveTotalTokens(request);
        int reserved = resolveReservedTokens(request, total);
        int available = Math.max(0, total - reserved);

        ContextBudgetPolicy policy = resolvePolicy(request);
        Map<ContextSection, Double> ratios = resolveRatios(policy);
        Map<ContextSection, Integer> sections = allocateSections(available, ratios);
        int allocated = sumTokens(sections);
        int slack = Math.max(0, available - allocated);
        sections.put(ContextSection.SLACK, sections.getOrDefault(ContextSection.SLACK, 0) + slack);

        ContextBudgetAllocation allocation = new ContextBudgetAllocation();
        allocation.setVersion(policy != null ? policy.getVersion() : "v1");
        allocation.setTotalTokens(total);
        allocation.setReservedTokens(reserved);
        allocation.setSectionTokens(sections);
        allocation.setAllocationState(ContextBudgetAllocationState.ENABLED);
        allocation.setAllocationReason("enabled");

        metricsPublisher.increment("context_budget_allocate_total");
        metricsPublisher.recordSummary("context_budget_total_tokens", total);
        for (Map.Entry<ContextSection, Integer> entry : sections.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            String section = entry.getKey().name().toLowerCase(Locale.ROOT);
            metricsPublisher.recordSummary("context_budget_section_tokens", entry.getValue(), "section", section);
        }
        log.info("上下文预算分配完成, tenantId={}, workflowId={}, totalTokens={}, policyVersion={}, sectionTokens={}",
                request.getTenantId(),
                request.getWorkflowId(),
                total,
                allocation.getVersion(),
                sections);
        return allocation;
    }

    private int resolveTotalTokens(ContextBudgetRequest request) {
        Integer total = request.getTotalTokens();
        if (total == null || total <= 0) {
            total = properties != null ? properties.getTotalBudgetTokens() : null;
        }
        if (total == null || total <= 0) {
            total = 0;
        }
        if (tokenBudgetManager != null) {
            int threshold = tokenBudgetManager.getThresholdTokens();
            if (threshold > 0) {
                total = Math.min(total, threshold);
            }
        }
        return Math.max(0, total);
    }

    private int resolveReservedTokens(ContextBudgetRequest request, int total) {
        int reserved = request.getReservedTokens() != null ? request.getReservedTokens() : 0;
        if (reserved < 0) {
            reserved = 0;
        }
        if (reserved > total) {
            reserved = total;
        }
        return reserved;
    }

    private ContextBudgetPolicy resolvePolicy(ContextBudgetRequest request) {
        if (request != null && request.getBudgetPolicy() != null) {
            return request.getBudgetPolicy();
        }
        return properties != null ? properties.toPolicy() : new ContextBudgetPolicy();
    }

    private Map<ContextSection, Double> resolveRatios(ContextBudgetPolicy policy) {
        Map<ContextSection, Double> ratios = new EnumMap<>(ContextSection.class);
        if (policy != null && policy.getSectionRatios() != null) {
            ratios.putAll(policy.getSectionRatios());
        }
        for (ContextSection section : ContextSection.values()) {
            ratios.putIfAbsent(section, 0.0);
        }
        double totalRatio = 0;
        for (Map.Entry<ContextSection, Double> entry : ratios.entrySet()) {
            Double value = entry.getValue();
            double ratio = value == null ? 0.0 : Math.max(0.0, value);
            entry.setValue(ratio);
            totalRatio += ratio;
        }
        if (totalRatio > 1.000001) {
            log.warn("上下文预算比例总和超过 1，执行归一化处理, totalRatio={}", totalRatio);
            for (Map.Entry<ContextSection, Double> entry : ratios.entrySet()) {
                entry.setValue(entry.getValue() / totalRatio);
            }
        }
        return ratios;
    }

    private Map<ContextSection, Integer> allocateSections(int available, Map<ContextSection, Double> ratios) {
        Map<ContextSection, Integer> sections = new EnumMap<>(ContextSection.class);
        if (available <= 0) {
            for (ContextSection section : ContextSection.values()) {
                sections.put(section, 0);
            }
            return sections;
        }
        for (Map.Entry<ContextSection, Double> entry : ratios.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            double ratio = entry.getValue() == null ? 0.0 : entry.getValue();
            int tokens = (int) Math.floor(available * ratio);
            sections.put(entry.getKey(), Math.max(tokens, 0));
        }
        for (ContextSection section : ContextSection.values()) {
            sections.putIfAbsent(section, 0);
        }
        return sections;
    }

    private int sumTokens(Map<ContextSection, Integer> sections) {
        int allocated = 0;
        if (sections == null || sections.isEmpty()) {
            return allocated;
        }
        for (Integer value : sections.values()) {
            allocated += value == null ? 0 : value;
        }
        return allocated;
    }
}

