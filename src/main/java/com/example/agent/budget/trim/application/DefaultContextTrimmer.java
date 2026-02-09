package com.example.agent.budget.trim.application;

import com.example.agent.budget.core.ContextBudgetAllocation;
import com.example.agent.budget.config.ContextBudgetProperties;
import com.example.agent.budget.core.ContextSection;
import com.example.agent.budget.core.ContextTrimSection;
import com.example.agent.budget.trim.application.ContextTrimmer;
import com.example.agent.budget.trim.estimator.ContextTokenEstimator;
import com.example.agent.budget.trim.handler.ContextTrimHandler;
import com.example.agent.budget.trim.handler.EvidencePackTrimHandler;
import com.example.agent.budget.trim.handler.RecalledMemoriesTrimHandler;
import com.example.agent.budget.trim.handler.TaskAndSystemTrimHandler;
import com.example.agent.budget.trim.handler.ToolSummaryTrimHandler;
import com.example.agent.budget.trim.handler.TrimContext;
import com.example.agent.budget.trim.handler.WorkingMemoryTrimHandler;
import com.example.agent.budget.trim.model.ContextTrimReport;
import com.example.agent.budget.trim.model.ContextTrimRequest;
import com.example.agent.budget.trim.model.ContextTrimResult;
import com.example.agent.budget.trim.model.ContextTrimStats;
import com.example.agent.capabilities.context.model.ContextSnapshot;
import com.example.agent.capabilities.memory.policy.TokenEstimator;
import com.example.agent.streaming.observability.MetricsPublisher;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 上下文裁剪器默认实现，负责裁剪流程编排与结果聚合。
 */
@Service
public class DefaultContextTrimmer implements ContextTrimmer {

    private static final Logger log = LoggerFactory.getLogger(DefaultContextTrimmer.class);

    /**
     * 统一令牌估算器。
     */
    private final ContextTokenEstimator contextTokenEstimator;

    /**
     * 指标记录器。
     */
    private final ContextTrimMetricsRecorder metricsRecorder;

    /**
     * 裁剪处理器映射，按分组索引。
     */
    private final Map<ContextTrimSection, ContextTrimHandler> handlers;

    /**
     * 预算配置。
     */
    private final ContextBudgetProperties properties;

    public DefaultContextTrimmer(TokenEstimator tokenEstimator,
                                 MetricsPublisher metricsPublisher,
                                 ContextBudgetProperties properties) {
        this.contextTokenEstimator = new ContextTokenEstimator(tokenEstimator);
        this.metricsRecorder = new ContextTrimMetricsRecorder(metricsPublisher);
        this.properties = properties;
        this.handlers = initializeHandlers();
    }

    @Override
    public ContextTrimResult trim(ContextTrimRequest request) {
        ContextTrimResult result = new ContextTrimResult();
        if (request == null || request.getSnapshot() == null
                || !request.getAllocation().isAllocationEnabled()) {
            return result;
        }
        ContextSnapshot snapshot = request.getSnapshot();
        result.setTrimmedSnapshot(snapshot);
        if (properties != null && !properties.isEnabled()) {
            return result;
        }

        ContextBudgetAllocation allocation = request.getAllocation();
        Map<ContextSection, Integer> budgets = allocation.getSectionTokens();
        if (budgets == null || budgets.isEmpty()) {
            return result;
        }

        Map<ContextSection, Integer> beforeTokens = contextTokenEstimator.estimateSectionTokens(snapshot);
        int totalBefore = contextTokenEstimator.sumTokens(beforeTokens);

        ContextTrimReport report = new ContextTrimReport();
        report.setTotalBeforeTokens(totalBefore);
        report.setSectionTokensBefore(beforeTokens);

        boolean overSection = isOverSectionBudget(beforeTokens, budgets);
        Integer totalBudget = allocation.getTotalTokens();
        boolean overTotal = totalBudget != null && totalBudget > 0 && totalBefore > totalBudget;
        List<String> reasons = new ArrayList<>();
        if (overSection) {
            reasons.add("OVER_SECTION_BUDGET");
        }
        if (overTotal) {
            reasons.add("OVER_TOTAL_BUDGET");
        }
        report.setReasons(reasons.isEmpty() ? null : reasons);

        if (!overSection && !overTotal) {
            report.setTotalAfterTokens(totalBefore);
            report.setSectionTokensAfter(beforeTokens);
            result.setReport(report);
            return result;
        }

        Map<ContextSection, ContextTrimStats> removedBySection = new EnumMap<>(ContextSection.class);
        List<ContextTrimSection> trimOrder = resolveTrimOrder();
        TrimContext trimContext = new TrimContext(
                snapshot,
                budgets,
                removedBySection,
                contextTokenEstimator,
                new EnumMap<>(beforeTokens),
                totalBefore);

        if (overSection) {
            for (ContextTrimSection section : trimOrder) {
                ContextTrimHandler handler = handlers.get(section);
                if (handler != null) {
                    handler.trimBySectionBudget(trimContext);
                }
            }
        }

        if (overTotal && totalBudget != null && trimContext.getTotalTokens() > totalBudget) {
            for (ContextTrimSection section : trimOrder) {
                if (trimContext.getTotalTokens() <= totalBudget) {
                    break;
                }
                int excess = trimContext.getTotalTokens() - totalBudget;
                ContextTrimHandler handler = handlers.get(section);
                if (handler != null) {
                    handler.trimByTotalBudget(trimContext, excess);
                }
            }
        }

        report.setSectionTokensAfter(trimContext.getCurrentTokens());
        report.setTotalAfterTokens(trimContext.getTotalTokens());
        report.setRemovedItemsBySection(removedBySection.isEmpty() ? null : removedBySection);
        result.setReport(report);

        metricsRecorder.record(totalBefore, trimContext.getTotalTokens(), removedBySection, reasons);
        log.info("上下文裁剪完成，租户={}, 工作流={}, 快照={}, 裁剪前令牌={}, 裁剪后令牌={}, "
                        + "移除统计={}, 原因={}, 策略版本={}, 令牌估算=true",
                snapshot.getRuntimeMeta() != null ? snapshot.getRuntimeMeta().getTenantId() : null,
                snapshot.getRuntimeMeta() != null ? snapshot.getRuntimeMeta().getWorkflowId() : null,
                snapshot.getSnapshotId(),
                totalBefore,
                trimContext.getTotalTokens(),
                removedBySection,
                reasons,
                request.getPolicy() != null ? request.getPolicy().getVersion() : allocation.getVersion());
        return result;
    }

    /**
     * 初始化默认裁剪处理器。
     */
    private Map<ContextTrimSection, ContextTrimHandler> initializeHandlers() {
        Map<ContextTrimSection, ContextTrimHandler> map = new EnumMap<>(ContextTrimSection.class);
        registerHandler(map, new EvidencePackTrimHandler());
        registerHandler(map, new RecalledMemoriesTrimHandler());
        registerHandler(map, new WorkingMemoryTrimHandler());
        registerHandler(map, new ToolSummaryTrimHandler());
        registerHandler(map, new TaskAndSystemTrimHandler());
        return map;
    }

    /**
     * 注册处理器。
     */
    private void registerHandler(Map<ContextTrimSection, ContextTrimHandler> map,
                                 ContextTrimHandler handler) {
        if (map == null || handler == null || handler.section() == null) {
            return;
        }
        map.put(handler.section(), handler);
    }

    /**
     * 解析裁剪顺序。
     */
    private List<ContextTrimSection> resolveTrimOrder() {
        List<ContextTrimSection> order;
        if (properties == null) {
            order = ContextTrimSection.defaultOrder();
        } else {
            order = properties.resolveTrimOrder();
            if (order == null || order.isEmpty()) {
                order = ContextTrimSection.defaultOrder();
            }
        }
        List<ContextTrimSection> filtered = order.stream()
                .filter(section -> section != null && handlers.containsKey(section))
                .collect(Collectors.toList());
        if (filtered.isEmpty()) {
            return ContextTrimSection.defaultOrder();
        }
        return filtered;
    }

    /**
     * 判断是否存在分段预算超限。
     */
    private boolean isOverSectionBudget(Map<ContextSection, Integer> beforeTokens,
                                        Map<ContextSection, Integer> budgets) {
        if (beforeTokens == null || budgets == null) {
            return false;
        }
        for (Map.Entry<ContextSection, Integer> entry : beforeTokens.entrySet()) {
            ContextSection section = entry.getKey();
            Integer value = entry.getValue();
            Integer budget = budgets.get(section);
            if (budget == null) {
                continue;
            }
            if (value != null && value > budget) {
                return true;
            }
        }
        return false;
    }
}
