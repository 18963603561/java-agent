package com.example.agent.capabilities.context.builder.budget;

import com.example.agent.security.auth.TenantContext;
import com.example.agent.budget.token.ContextBudgetPolicy;
import com.example.agent.budget.token.ContextBudgetProperties;
import com.example.agent.budget.token.ContextBudgetRequest;
import com.example.agent.capabilities.context.ContextBuildRequest;
import com.example.agent.capabilities.context.model.ContextPolicy;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 上下文预算请求工厂。
 *
 * <p>用途：统一构造预算请求并保证输出对象语义完整。
 * <p>输入：构建请求、运行时上下文、解析后的策略、预算配置和默认预算。
 * <p>输出：合法预算请求；当预算功能关闭时返回空。
 */
@Component
public class ContextBudgetRequestFactory {

    /**
     * 构建预算请求。
     *
     * @param request 构建请求
     * @param runtimeContext 运行时上下文
     * @param resolvedPolicy 解析后的上下文策略
     * @param budgetProperties 预算配置
     * @param defaultTokenBudget 默认预算
     * @return 合法预算请求
     */
    public ContextBudgetRequest create(ContextBuildRequest request,
                                       Map<String, Object> runtimeContext,
                                       ContextPolicy resolvedPolicy,
                                       ContextBudgetProperties budgetProperties,
                                       int defaultTokenBudget) {
        if (request == null) {
            throw new IllegalArgumentException("context build request must not be null");
        }
        if (budgetProperties != null && !budgetProperties.isEnabled()) {
            return null;
        }
        ContextBudgetRequest source = request.getBudgetRequest();
        ContextBudgetRequest target = new ContextBudgetRequest();
        if (source != null) {
            copyFromProvided(source, target);
        }

        Integer totalTokens = resolveTotalTokens(source, runtimeContext, budgetProperties, defaultTokenBudget);
        if (totalTokens == null || totalTokens <= 0) {
            throw new IllegalArgumentException("context total tokens must be positive");
        }
        target.setTotalTokens(totalTokens);

        Integer reservedTokens = source != null ? source.getReservedTokens() : null;
        if (reservedTokens == null) {
            reservedTokens = 0;
        }
        if (reservedTokens < 0) {
            throw new IllegalArgumentException("context reserved tokens must be greater than or equal to zero");
        }
        target.setReservedTokens(reservedTokens);

        if (target.getPolicy() == null) {
            target.setPolicy(resolvedPolicy);
        }
        fillTraceFields(target, request);

        if (target.getBudgetPolicy() == null && budgetProperties != null) {
            ContextBudgetPolicy policy = budgetProperties.toPolicy();
            target.setBudgetPolicy(policy);
        }
        return target;
    }

    /**
     * 复制调用方显式提供的预算请求字段。
     */
    private void copyFromProvided(ContextBudgetRequest source, ContextBudgetRequest target) {
        target.setEnabled(source.isEnabled());
        target.setTotalTokens(source.getTotalTokens());
        target.setReservedTokens(source.getReservedTokens());
        target.setTenantId(source.getTenantId());
        target.setWorkflowId(source.getWorkflowId());
        target.setTaskId(source.getTaskId());
        target.setPolicy(source.getPolicy());
        target.setBudgetPolicy(source.getBudgetPolicy());
    }

    /**
     * 解析总预算令牌数，优先级链为：显式请求 > 运行时上下文 > 配置 > 默认值。
     */
    private Integer resolveTotalTokens(ContextBudgetRequest source,
                                       Map<String, Object> runtimeContext,
                                       ContextBudgetProperties budgetProperties,
                                       int defaultTokenBudget) {
        if (source != null && source.getTotalTokens() != null && source.getTotalTokens() > 0) {
            return source.getTotalTokens();
        }
        Integer runtimeBudget = readInteger(runtimeContext, "tokenBudget");
        if (runtimeBudget != null && runtimeBudget > 0) {
            return runtimeBudget;
        }
        if (budgetProperties != null && budgetProperties.getTotalBudgetTokens() > 0) {
            return budgetProperties.getTotalBudgetTokens();
        }
        if (defaultTokenBudget > 0) {
            return defaultTokenBudget;
        }
        return null;
    }

    /**
     * 补齐租户与流程链路字段。
     */
    private void fillTraceFields(ContextBudgetRequest budgetRequest, ContextBuildRequest request) {
        TenantContext tenantContext = request.getTenantContext();
        if (tenantContext != null && isBlank(budgetRequest.getTenantId())) {
            budgetRequest.setTenantId(tenantContext.getTenantId());
        }
        if (isBlank(budgetRequest.getWorkflowId())) {
            budgetRequest.setWorkflowId(request.getWorkflowId());
        }
        if (isBlank(budgetRequest.getTaskId())) {
            budgetRequest.setTaskId(request.getTaskId());
        }
    }

    /**
     * 从映射中读取整数。
     */
    private Integer readInteger(Map<String, Object> map, String key) {
        if (map == null || key == null) {
            return null;
        }
        Object value = map.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    /**
     * 判断字符串是否为空白。
     */
    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}

