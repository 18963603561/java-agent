package com.example.agent.capabilities.context.compression.config;

import com.example.agent.capabilities.context.compression.config.ContextCompressionProperties;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 压缩配置校验器。
 */
@Component
public class CompressionConfigValidator {

    private static final Logger log = LoggerFactory.getLogger(CompressionConfigValidator.class);

    private final ContextCompressionProperties properties;

    public CompressionConfigValidator(ContextCompressionProperties properties) {
        this.properties = properties;
    }

    /**
     * 启动期校验压缩配置。
     */
    @PostConstruct
    public void validateOnStartup() {
        CompressionConfigValidationResult result = validate();
        // 错误处理：存在错误时直接阻断启动，避免运行期隐式故障。
        if (!result.isValid()) {
            throw new IllegalStateException("compression_config_invalid:" + String.join(";", result.getErrors()));
        }
        // 告警输出：将可恢复问题打印为告警，便于上线前排查。
        for (String warning : result.getWarnings()) {
            log.warn("压缩配置告警: {}", warning);
        }
    }

    /**
     * 执行配置校验。
     *
     * @return 校验结果
     */
    public CompressionConfigValidationResult validate() {
        CompressionConfigValidationResult result = new CompressionConfigValidationResult();
        if (properties == null) {
            result.addError("properties_missing");
            return result;
        }

        if (properties.getMode() == null || !StringUtils.hasText(properties.getMode().getType())) {
            result.addError("mode_type_missing");
        } else {
            String mode = properties.getMode().getType().trim().toLowerCase();
            // 模式校验：仅允许 rule/llm/hybrid 三类模式。
            if (!"rule".equals(mode) && !"llm".equals(mode) && !"hybrid".equals(mode)) {
                result.addError("mode_type_invalid");
            }
        }

        if (properties.getTrigger() == null) {
            result.addError("trigger_missing");
        } else {
            // 比例校验：触发比例必须在 (0,1] 区间内。
            validateRatio(result,
                    properties.getTrigger().getCompressionTriggerRatio(),
                    "trigger_ratio_invalid");
            // 比例校验：目标比例必须在 (0,1] 区间内。
            validateRatio(result,
                    properties.getTrigger().getCompressionTargetRatio(),
                    "target_ratio_invalid");
            // 关系校验：触发比例必须大于等于目标比例，保证压缩触发与达标语义一致。
            if (properties.getTrigger().getCompressionTriggerRatio() != null
                    && properties.getTrigger().getCompressionTargetRatio() != null
                    && properties.getTrigger().getCompressionTriggerRatio()
                    < properties.getTrigger().getCompressionTargetRatio()) {
                result.addError("trigger_target_ratio_relation_invalid");
            }
            // 间隔校验：最小间隔不得为负。
            if (properties.getTrigger().getMinIntervalSeconds() < 0) {
                result.addError("min_interval_invalid");
            }
        }

        if (properties.getLlm() == null) {
            result.addError("llm_config_missing");
        } else {
            // 场景校验：必须显式配置 context_compress 场景。
            if (!StringUtils.hasText(properties.getLlm().getScene())) {
                result.addError("llm_scene_missing");
            }
            if (properties.getLlm().getTimeoutMs() <= 0) {
                result.addError("llm_timeout_invalid");
            }
            if (properties.getLlm().getRetry() < 0) {
                result.addError("llm_retry_invalid");
            }
            if (!StringUtils.hasText(properties.getLlm().getFallback())) {
                result.addError("llm_fallback_missing");
            } else {
                String fallback = properties.getLlm().getFallback().trim().toLowerCase();
                // 降级校验：当前只允许 rule 或 none。
                if (!"rule".equals(fallback) && !"none".equals(fallback)) {
                    result.addError("llm_fallback_invalid");
                }
            }
        }

        if (properties.getWindow() == null) {
            result.addWarning("window_missing_use_default");
        } else {
            if (properties.getWindow().getPrimersCount() < 0) {
                result.addError("window_primers_invalid");
            }
            if (properties.getWindow().getRecentsCount() < 0) {
                result.addError("window_recents_invalid");
            }
            // 组合校验：首尾窗口都为零时失去三段滑窗意义。
            if (properties.getWindow().getPrimersCount() == 0 && properties.getWindow().getRecentsCount() == 0) {
                result.addWarning("window_segments_both_zero");
            }
        }

        if (properties.getInjection() == null) {
            result.addWarning("injection_missing_use_default");
        } else {
            // 注入角色校验：仅允许 developer/user。
            if (!StringUtils.hasText(properties.getInjection().getRole())) {
                result.addError("injection_role_missing");
            } else {
                String role = properties.getInjection().getRole().trim().toLowerCase();
                if (!"developer".equals(role) && !"user".equals(role)) {
                    result.addError("injection_role_invalid");
                }
            }
            if (properties.getInjection().getMaxChars() <= 0) {
                result.addError("injection_max_chars_invalid");
            }
        }

        // 灰度配置校验：缺少灰度配置时输出告警并沿用默认值。
        if (properties.getRollout() == null) {
            result.addWarning("rollout_missing_use_default");
        } else {
            // 版本校验：灰度版本必须非空，便于审计回放。
            if (!StringUtils.hasText(properties.getRollout().getVersion())) {
                result.addError("rollout_version_missing");
            }
            // 比例校验：全局比例必须位于 [0,1] 区间。
            if (properties.getRollout().getGlobalRatio() == null
                    || properties.getRollout().getGlobalRatio() < 0D
                    || properties.getRollout().getGlobalRatio() > 1D) {
                result.addError("rollout_global_ratio_invalid");
            }
        }
        return result;
    }

    /**
     * 校验比例值。
     */
    private void validateRatio(CompressionConfigValidationResult result, Double ratio, String errorCode) {
        if (ratio == null || ratio <= 0D || ratio > 1D) {
            result.addError(errorCode);
        }
    }
}

