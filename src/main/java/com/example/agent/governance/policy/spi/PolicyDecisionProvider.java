package com.example.agent.governance.policy.spi;

import com.example.agent.governance.policy.domain.PolicyEvaluationCommand;
import com.example.agent.governance.policy.domain.PolicyEvaluationResult;
import com.example.agent.security.auth.TenantContext;

/**
 * 策略决策提供者。
 *
 * <p>用途：抽象策略评估实现，支持本地规则与 OPA 等多实现按配置切换。</p>
 */
public interface PolicyDecisionProvider {

    /**
     * 提供者标识。
     *
     * @return 提供者唯一标识（如 local/opa）
     */
    String providerId();

    /**
     * 执行策略评估。
     *
     * @param command 策略评估命令
     * @param tenantContext 租户上下文
     * @return 策略评估结果
     */
    PolicyEvaluationResult evaluate(PolicyEvaluationCommand command, TenantContext tenantContext);
}

