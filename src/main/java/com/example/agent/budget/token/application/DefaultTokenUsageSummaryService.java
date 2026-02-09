package com.example.agent.budget.token.application;

import com.example.agent.budget.token.model.TokenUsageRecord;
import com.example.agent.budget.token.model.TokenUsageSummary;
import com.example.agent.budget.token.repository.TokenUsageRepository;
import com.example.agent.security.auth.TenantContext;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 默认预算汇总服务，按任务维度聚合令牌与成本。
 */
@Component
public class DefaultTokenUsageSummaryService implements TokenUsageSummaryService {

    private final TokenUsageRepository repository;

    public DefaultTokenUsageSummaryService(TokenUsageRepository repository) {
        this.repository = repository;
    }

    @Override
    public TokenUsageSummary summarize(String taskId, TenantContext tenantContext) {
        List<TokenUsageRecord> records = repository.findByTask(tenantContext.getTenantId(), taskId);
        int totalTokens = 0;
        double totalCost = 0;
        Map<String, Integer> byModel = new HashMap<>();
        Map<String, Double> byProvider = new HashMap<>();
        for (TokenUsageRecord record : records) {
            totalTokens += record.getTotalTokens();
            totalCost += record.getCostUsd();
            if (record.getModel() != null) {
                byModel.merge(record.getModel(), record.getTotalTokens(), Integer::sum);
            }
            if (record.getProvider() != null) {
                byProvider.merge(record.getProvider(), record.getCostUsd(), Double::sum);
            }
        }
        return new TokenUsageSummary(taskId, totalTokens, totalCost, byModel, byProvider);
    }
}
