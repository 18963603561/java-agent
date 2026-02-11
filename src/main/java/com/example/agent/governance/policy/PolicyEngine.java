package com.example.agent.governance.policy;

import com.example.agent.governance.policy.domain.PolicyEvaluationCommand;
import com.example.agent.governance.policy.domain.PolicyEvaluationResult;
import com.example.agent.governance.policy.config.PolicyProviderProperties;
import com.example.agent.governance.policy.spi.PolicyDecisionProvider;
import com.example.agent.common.error.ErrorCodeException;
import com.example.agent.security.auth.TenantContext;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 策略评估引擎，提供基础的风险评估逻辑。
 */
@Service
public class PolicyEngine {

    private static final Logger log = LoggerFactory.getLogger(PolicyEngine.class);

    private final Map<String, PolicyDecisionProvider> providerMap;
    private final PolicyProviderProperties properties;

    public PolicyEngine(List<PolicyDecisionProvider> providers,
                        PolicyProviderProperties properties) {
        this.properties = properties;
        this.providerMap = new HashMap<>();
        if (providers != null) {
            for (PolicyDecisionProvider provider : providers) {
                if (provider == null || !StringUtils.hasText(provider.providerId())) {
                    continue;
                }
                providerMap.put(provider.providerId().trim().toLowerCase(), provider);
            }
        }
    }

    /**
     * 评估策略请求。
     *
     * @param command 策略评估命令
     * @param tenantContext 租户上下文
     * @return 策略决策
     */
    public PolicyEvaluationResult evaluate(PolicyEvaluationCommand command, TenantContext tenantContext) {
        String tenantId = validateAndResolveTenantId(tenantContext);
        PolicyEvaluationCommand normalized = normalizeCommand(command);
        PolicyDecisionProvider provider = resolveProvider();
        PolicyEvaluationResult result = provider.evaluate(normalized, tenantContext);
        log.info("策略评估完成, tenantId={}, provider={}, policyId={}, action={}, resource={}, decision={}",
                tenantId,
                provider.providerId(),
                normalized.getPolicyId(),
                normalized.getAction(),
                normalized.getResource(),
                result != null ? result.getDecision() : null);
        return result;
    }

    /**
     * 校验并提取租户标识。
     *
     * @param tenantContext 租户上下文
     * @return 租户标识
     */
    private String validateAndResolveTenantId(TenantContext tenantContext) {
        if (tenantContext == null || !StringUtils.hasText(tenantContext.getTenantId())) {
            throw new ErrorCodeException(HttpStatus.BAD_REQUEST,
                    "POLICY_TENANT_MISSING",
                    "策略评估缺少租户信息");
        }
        return tenantContext.getTenantId();
    }

    /**
     * 校验并标准化策略评估命令。
     *
     * @param command 原始命令
     * @return 标准化命令
     */
    private PolicyEvaluationCommand normalizeCommand(PolicyEvaluationCommand command) {
        if (command == null) {
            throw new ErrorCodeException(HttpStatus.BAD_REQUEST,
                    "POLICY_COMMAND_MISSING",
                    "策略评估请求不能为空");
        }
        if (!StringUtils.hasText(command.getAction())) {
            throw new ErrorCodeException(HttpStatus.BAD_REQUEST,
                    "POLICY_ACTION_MISSING",
                    "策略评估缺少 action");
        }
        if (!StringUtils.hasText(command.getResource())) {
            throw new ErrorCodeException(HttpStatus.BAD_REQUEST,
                    "POLICY_RESOURCE_MISSING",
                    "策略评估缺少 resource");
        }
        command.setAction(command.getAction().trim());
        command.setResource(command.getResource().trim());
        if (StringUtils.hasText(command.getPolicyId())) {
            command.setPolicyId(command.getPolicyId().trim());
        }
        return command;
    }

    private PolicyDecisionProvider resolveProvider() {
        String configured = properties != null ? properties.getProvider() : null;
        String providerId = StringUtils.hasText(configured)
                ? configured.trim().toLowerCase()
                : "local";
        PolicyDecisionProvider provider = providerMap.get(providerId);
        if (provider != null) {
            return provider;
        }
        PolicyDecisionProvider fallback = providerMap.get("local");
        if (fallback != null) {
            log.warn("策略提供者未命中，回退local, configuredProvider={}", providerId);
            return fallback;
        }
        throw new IllegalStateException("policy_provider_missing");
    }
}
