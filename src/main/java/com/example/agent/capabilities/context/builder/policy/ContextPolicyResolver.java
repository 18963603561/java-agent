package com.example.agent.capabilities.context.builder.policy;

import com.example.agent.api.http.dto.TaskRequest;
import com.example.agent.capabilities.context.ContextBuildRequest;
import com.example.agent.capabilities.context.model.ContextPolicy;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 上下文策略解析器。
 *
 * <p>用途：统一解析上下文策略来源，保证策略解析逻辑可复用且无入参副作用。
 * <p>输入：构建请求与运行时上下文。
 * <p>输出：解析后的策略对象，无可用策略时返回空。
 */
@Component
public class ContextPolicyResolver {

    /**
     * 解析上下文策略，优先级为：请求显式策略 > 运行时上下文 > 任务上下文。
     *
     * @param request 构建请求
     * @param runtimeContext 运行时上下文
     * @return 解析后的策略
     */
    public ContextPolicy resolve(ContextBuildRequest request, Map<String, Object> runtimeContext) {
        if (request == null) {
            return null;
        }
        ContextPolicy requestPolicy = request.getPolicy();
        if (requestPolicy != null) {
            return requestPolicy;
        }
        ContextPolicy resolved = resolveFromContext(runtimeContext);
        if (resolved != null) {
            return resolved;
        }
        TaskRequest taskRequest = request.getTaskRequest();
        if (taskRequest == null) {
            return null;
        }
        return resolveFromContext(taskRequest.getContext());
    }

    /**
     * 从上下文映射中解析策略对象。
     *
     * @param context 上下文映射
     * @return 策略对象
     */
    private ContextPolicy resolveFromContext(Map<String, Object> context) {
        if (context == null || context.isEmpty()) {
            return null;
        }
        Object raw = context.get("contextPolicy");
        if (raw == null) {
            raw = context.get("policy");
        }
        if (raw instanceof ContextPolicy policy) {
            return policy;
        }
        if (raw instanceof Map<?, ?> map) {
            ContextPolicy policy = buildPolicyFromMap(map);
            return hasPolicyContent(policy) ? policy : null;
        }
        return null;
    }

    /**
     * 将 Map 结构构造成策略对象。
     *
     * @param map 策略映射
     * @return 策略对象
     */
    private ContextPolicy buildPolicyFromMap(Map<?, ?> map) {
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
     * 判断策略是否包含有效内容。
     *
     * @param policy 策略对象
     * @return 是否包含有效内容
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
     * 从映射中读取字符串。
     */
    private String readString(Map<?, ?> map, String key) {
        if (map == null || key == null) {
            return null;
        }
        Object value = map.get(key);
        return value != null ? value.toString() : null;
    }

    /**
     * 将对象解析为字符串列表。
     */
    private List<String> readStringList(Object value) {
        if (value instanceof List<?> list) {
            List<String> result = new ArrayList<>();
            for (Object item : list) {
                if (item != null && StringUtils.hasText(item.toString())) {
                    result.add(item.toString().trim());
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
     * 将对象解析为布尔值。
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
     * 从映射中读取整数。
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
}

