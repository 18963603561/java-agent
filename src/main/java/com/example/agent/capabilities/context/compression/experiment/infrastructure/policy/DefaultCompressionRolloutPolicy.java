package com.example.agent.capabilities.context.compression.experiment.infrastructure.policy;

import com.example.agent.capabilities.context.compression.config.ContextCompressionProperties;
import com.example.agent.capabilities.context.compression.experiment.domain.model.RolloutDecision;
import com.example.agent.capabilities.context.compression.experiment.domain.policy.CompressionRolloutPolicy;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 默认压缩灰度策略实现。
 */
@Component
public class DefaultCompressionRolloutPolicy implements CompressionRolloutPolicy {

    /**
     * 压缩配置。
     */
    private final ContextCompressionProperties properties;

    public DefaultCompressionRolloutPolicy(ContextCompressionProperties properties) {
        this.properties = properties;
    }

    @Override
    public RolloutDecision decide(String tenantId, String scene, String sessionId, String workflowId) {
        RolloutDecision decision = new RolloutDecision();
        // 版本回填：统一输出策略版本，便于后续审计回放。
        decision.setRolloutVersion(resolveRolloutVersion());
        decision.setTenantId(tenantId);
        decision.setScene(scene);
        decision.setRatio(resolveGlobalRatio());
        // 开关判定：双轨总开关关闭时直接返回关闭决策。
        if (!isDualTrackEnabled()) {
            decision.setDualTrackEnabled(false);
            decision.setReason("DUAL_TRACK_DISABLED");
            return decision;
        }
        // 白名单判定：租户白名单命中时强制开启双轨。
        if (isTenantWhitelisted(tenantId)) {
            decision.setDualTrackEnabled(true);
            decision.setReason("TENANT_WHITELIST");
            decision.setBucketKey(resolveBucketKey(tenantId, sessionId, workflowId));
            return decision;
        }
        // 白名单判定：场景白名单命中时强制开启双轨。
        if (isSceneWhitelisted(scene)) {
            decision.setDualTrackEnabled(true);
            decision.setReason("SCENE_WHITELIST");
            decision.setBucketKey(resolveBucketKey(tenantId, sessionId, workflowId));
            return decision;
        }
        double ratio = resolveGlobalRatio();
        // 边界判定：比例不大于零时关闭双轨，避免无效采样。
        if (ratio <= 0D) {
            decision.setDualTrackEnabled(false);
            decision.setReason("GLOBAL_RATIO_DISABLED");
            decision.setBucketKey(resolveBucketKey(tenantId, sessionId, workflowId));
            return decision;
        }
        // 边界判定：比例大于等于一时全量开启双轨。
        if (ratio >= 1D) {
            decision.setDualTrackEnabled(true);
            decision.setReason("GLOBAL_RATIO_FULL");
            decision.setBucketKey(resolveBucketKey(tenantId, sessionId, workflowId));
            return decision;
        }
        String bucketKey = resolveBucketKey(tenantId, sessionId, workflowId);
        decision.setBucketKey(bucketKey);
        double bucket = resolveBucketValue(bucketKey);
        // 分桶判定：分桶值落在比例阈值内时开启双轨。
        if (bucket < ratio) {
            decision.setDualTrackEnabled(true);
            decision.setReason("GLOBAL_RATIO_HIT");
            return decision;
        }
        // 默认关闭：分桶未命中比例阈值时关闭双轨。
        decision.setDualTrackEnabled(false);
        decision.setReason("GLOBAL_RATIO_MISS");
        return decision;
    }

    /**
     * 判断双轨总开关是否开启。
     */
    private boolean isDualTrackEnabled() {
        return properties != null
                && properties.getRollout() != null
                && properties.getRollout().isDualTrackEnabled();
    }

    /**
     * 解析灰度策略版本。
     */
    private String resolveRolloutVersion() {
        if (properties == null || properties.getRollout() == null
                || !StringUtils.hasText(properties.getRollout().getVersion())) {
            return "v1";
        }
        return properties.getRollout().getVersion().trim();
    }

    /**
     * 解析全局采样比例。
     */
    private double resolveGlobalRatio() {
        if (properties == null || properties.getRollout() == null || properties.getRollout().getGlobalRatio() == null) {
            return 0D;
        }
        double ratio = properties.getRollout().getGlobalRatio();
        if (ratio < 0D) {
            return 0D;
        }
        if (ratio > 1D) {
            return 1D;
        }
        return ratio;
    }

    /**
     * 判断租户是否命中白名单。
     */
    private boolean isTenantWhitelisted(String tenantId) {
        if (!StringUtils.hasText(tenantId)) {
            return false;
        }
        List<String> whitelist = properties != null && properties.getRollout() != null
                ? properties.getRollout().getTenantWhitelist()
                : List.of();
        // 白名单遍历：逐项归一化比较租户标识。
        for (String item : whitelist) {
            // 空值过滤：跳过无效白名单项，避免误判。
            if (!StringUtils.hasText(item)) {
                continue;
            }
            // 命中判定：忽略大小写比较租户标识。
            if (tenantId.trim().equalsIgnoreCase(item.trim())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断场景是否命中白名单。
     */
    private boolean isSceneWhitelisted(String scene) {
        if (!StringUtils.hasText(scene)) {
            return false;
        }
        List<String> whitelist = properties != null && properties.getRollout() != null
                ? properties.getRollout().getSceneWhitelist()
                : List.of();
        // 白名单遍历：逐项归一化比较场景标识。
        for (String item : whitelist) {
            // 空值过滤：跳过无效白名单项，避免误判。
            if (!StringUtils.hasText(item)) {
                continue;
            }
            // 命中判定：忽略大小写比较场景标识。
            if (scene.trim().equalsIgnoreCase(item.trim())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 构造稳定分桶键。
     */
    private String resolveBucketKey(String tenantId, String sessionId, String workflowId) {
        String normalizedTenant = normalizeSegment(tenantId);
        String normalizedSession = normalizeSegment(sessionId);
        String normalizedWorkflow = normalizeSegment(workflowId);
        if (!"unknown".equals(normalizedSession)) {
            return normalizedTenant + ":" + normalizedSession;
        }
        if (!"unknown".equals(normalizedWorkflow)) {
            return normalizedTenant + ":" + normalizedWorkflow;
        }
        return normalizedTenant + ":default";
    }

    /**
     * 计算稳定分桶值。
     */
    private double resolveBucketValue(String bucketKey) {
        String key = StringUtils.hasText(bucketKey) ? bucketKey : "default";
        int hash = java.util.Arrays.hashCode(key.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
        long positive = hash & 0x7fff_ffffL;
        return positive / (double) Integer.MAX_VALUE;
    }

    /**
     * 归一化分段值。
     */
    private String normalizeSegment(String value) {
        if (!StringUtils.hasText(value)) {
            return "unknown";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }
}

