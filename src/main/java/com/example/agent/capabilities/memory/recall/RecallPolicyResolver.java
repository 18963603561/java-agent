package com.example.agent.capabilities.memory.recall;

import com.example.agent.capabilities.context.ContextPolicy;
import com.example.agent.capabilities.memory.RetrievalPriority;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 召回策略解析器，负责从上下文构建标准化策略快照。
 */
@Component
public class RecallPolicyResolver {

    public static final String CONTEXT_POLICY_KEY = "contextPolicy";
    public static final String CONTEXT_POLICY_FALLBACK_KEY = "policy";

    /**
     * 解析策略快照。
     *
     * @param effectiveContext 生效上下文
     * @return 策略快照
     */
    public RecallPolicySnapshot resolve(Map<String, Object> effectiveContext) {
        ContextPolicy contextPolicy = resolvePolicyFromContext(effectiveContext);
        List<RetrievalPriority> retrievalPriority = resolveRetrievalPriority(contextPolicy);
        boolean enableSensitiveMask = resolveSensitiveMask(contextPolicy);
        return new RecallPolicySnapshot(contextPolicy, retrievalPriority, enableSensitiveMask);
    }

    /**
     * 从上下文解析策略对象。
     */
    private ContextPolicy resolvePolicyFromContext(Map<String, Object> context) {
        if (context == null) {
            return null;
        }
        Object value = context.get(CONTEXT_POLICY_KEY);
        if (value == null) {
            value = context.get(CONTEXT_POLICY_FALLBACK_KEY);
        }
        if (value instanceof ContextPolicy policy) {
            return policy;
        }
        if (value instanceof Map<?, ?> map) {
            ContextPolicy policy = buildPolicyFromMap(map);
            return hasPolicyContent(policy) ? policy : null;
        }
        return null;
    }

    /**
     * 从 Map 构建策略对象。
     */
    private ContextPolicy buildPolicyFromMap(Map<?, ?> map) {
        if (map == null) {
            return null;
        }
        ContextPolicy policy = new ContextPolicy();
        policy.setPolicyId(readString(map, "policyId"));
        policy.setRetrievalPriority(readStringList(map.get("retrievalPriority")));
        policy.setPruneOrder(readStringList(map.get("pruneOrder")));
        policy.setMaxEvidenceCount(readInteger(map, "maxEvidenceCount"));
        policy.setMaxMemoryCount(readInteger(map, "maxMemoryCount"));
        policy.setEnableSensitiveMask(readBoolean(map.get("enableSensitiveMask")));
        return policy;
    }

    /**
     * 判断策略对象是否包含有效内容。
     */
    private boolean hasPolicyContent(ContextPolicy policy) {
        if (policy == null) {
            return false;
        }
        return StringUtils.hasText(policy.getPolicyId())
                || (policy.getRetrievalPriority() != null && !policy.getRetrievalPriority().isEmpty())
                || (policy.getPruneOrder() != null && !policy.getPruneOrder().isEmpty())
                || policy.getMaxEvidenceCount() != null
                || policy.getMaxMemoryCount() != null
                || policy.getEnableSensitiveMask() != null;
    }

    /**
     * 解析检索优先级并补全默认顺序。
     */
    private List<RetrievalPriority> resolveRetrievalPriority(ContextPolicy policy) {
        List<RetrievalPriority> resolved = new ArrayList<>();
        List<String> configured = policy != null ? policy.getRetrievalPriority() : null;
        if (configured != null) {
            for (String value : configured) {
                RetrievalPriority parsed = RetrievalPriority.parse(value);
                if (parsed != null && !resolved.contains(parsed)) {
                    resolved.add(parsed);
                }
            }
        }
        for (RetrievalPriority priority : RetrievalPriority.defaultOrder()) {
            if (!resolved.contains(priority)) {
                resolved.add(priority);
            }
        }
        return resolved;
    }

    /**
     * 解析敏感遮罩开关。
     */
    private boolean resolveSensitiveMask(ContextPolicy policy) {
        if (policy == null || policy.getEnableSensitiveMask() == null) {
            return true;
        }
        return Boolean.TRUE.equals(policy.getEnableSensitiveMask());
    }

    /**
     * 读取字符串列表。
     */
    private List<String> readStringList(Object value) {
        if (value instanceof List<?> list) {
            List<String> result = new ArrayList<>();
            for (Object item : list) {
                if (item != null && StringUtils.hasText(item.toString())) {
                    result.add(item.toString());
                }
            }
            return result.isEmpty() ? null : result;
        }
        if (value instanceof String text && StringUtils.hasText(text)) {
            return List.of(text.trim());
        }
        return null;
    }

    /**
     * 读取整型参数。
     */
    private Integer readInteger(Map<?, ?> map, String key) {
        if (map == null || key == null) {
            return null;
        }
        Object value = map.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && StringUtils.hasText(text)) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    /**
     * 读取布尔参数。
     */
    private Boolean readBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String text && StringUtils.hasText(text)) {
            return Boolean.parseBoolean(text.trim().toLowerCase(Locale.ROOT));
        }
        return null;
    }

    /**
     * 读取字符串参数。
     */
    private String readString(Map<?, ?> context, String key) {
        if (context == null || key == null) {
            return null;
        }
        Object value = context.get(key);
        if (value instanceof String text && StringUtils.hasText(text)) {
            return text;
        }
        return null;
    }
}

