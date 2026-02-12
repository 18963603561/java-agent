package com.example.agent.capabilities.context.compression.config;

import com.example.agent.budget.trim.config.ContextCompressionProperties;
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

    private static final String MODE_RULE = "rule";
    private static final String MODE_LLM = "llm";
    private static final String MODE_HYBRID = "hybrid";

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
        // 创建结果对象：用于累计错误与告警。
        CompressionConfigValidationResult result = new CompressionConfigValidationResult();
        // 空配置判定：缺失配置对象时直接返回错误，避免后续空指针。
        if (properties == null) {
            result.addError("properties_missing");
            return result;
        }

        // 校验模式配置：保证压缩模式可路由。
        validateMode(result);
        // 校验触发配置：保证触发阈值合法。
        validateTrigger(result);
        // 校验 LLM 配置：保证模型参数可执行。
        validateLlm(result);
        // 校验滑窗配置：保证窗口参数为非负。
        validateWindow(result);
        // 校验基线配置：保证默认阈值关系合理。
        validateBaseline(result);
        // 校验灰度配置：保证灰度比例和版本合法。
        validateRollout(result);
        // 校验质量门禁：保证质量阈值可比较。
        validateQualityGate(result);
        // 校验回滚配置：保证窗口参数与阈值合法。
        validateRollback(result);
        // 校验紧急开关组合：避免互斥配置静默生效。
        validateEmergency(result);
        return result;
    }

    /**
     * 校验压缩模式配置。
     */
    private void validateMode(CompressionConfigValidationResult result) {
        // 缺失模式配置时直接报错，避免路由阶段选择失败。
        if (properties.getMode() == null || !StringUtils.hasText(properties.getMode().getType())) {
            result.addError("mode_type_missing");
            return;
        }
        // 提取并归一化模式字符串，统一做值域判断。
        String mode = properties.getMode().getType().trim().toLowerCase();
        // 校验模式可选值，确保只允许 rule/llm/hybrid。
        if (!MODE_RULE.equals(mode) && !MODE_LLM.equals(mode) && !MODE_HYBRID.equals(mode)) {
            result.addError("mode_type_invalid");
        }
    }

    /**
     * 校验触发配置。
     */
    private void validateTrigger(CompressionConfigValidationResult result) {
        // 缺失触发配置时直接报错，避免主链路触发判断失效。
        if (properties.getTrigger() == null) {
            result.addError("trigger_missing");
            return;
        }
        // 校验触发比例，保证阈值在合法区间。
        validateRatio(result,
                properties.getTrigger().getCompressionTriggerRatio(),
                "trigger_ratio_invalid");
        // 校验目标比例，保证阈值在合法区间。
        validateRatio(result,
                properties.getTrigger().getCompressionTargetRatio(),
                "target_ratio_invalid");
        // 校验比例关系，避免目标比例高于触发比例导致无法收敛。
        if (properties.getTrigger().getCompressionTriggerRatio() != null
                && properties.getTrigger().getCompressionTargetRatio() != null
                && properties.getTrigger().getCompressionTargetRatio() > properties.getTrigger().getCompressionTriggerRatio()) {
            result.addError("trigger_ratio_relation_invalid");
        }
        // 校验最小间隔，避免负值导致冷却策略异常。
        if (properties.getTrigger().getMinIntervalSeconds() < 0) {
            result.addError("min_interval_invalid");
        }
    }

    /**
     * 校验 LLM 配置。
     */
    private void validateLlm(CompressionConfigValidationResult result) {
        // 缺失 LLM 配置时直接报错，避免 llm/hybrid 模式运行时失败。
        if (properties.getLlm() == null) {
            result.addError("llm_config_missing");
            return;
        }
        // 校验场景名，保证调用路由到预期模型场景。
        if (!StringUtils.hasText(properties.getLlm().getScene())) {
            result.addError("llm_scene_missing");
        }
        // 校验超时时间，避免非正值导致调用策略失效。
        if (properties.getLlm().getTimeoutMs() <= 0) {
            result.addError("llm_timeout_invalid");
        }
        // 校验重试次数，避免负值破坏重试控制。
        if (properties.getLlm().getRetry() < 0) {
            result.addError("llm_retry_invalid");
        }
        // 校验降级配置是否填写。
        if (!StringUtils.hasText(properties.getLlm().getFallback())) {
            result.addError("llm_fallback_missing");
            return;
        }
        // 提取并归一化降级配置值。
        String fallback = properties.getLlm().getFallback().trim().toLowerCase();
        // 校验降级策略值域，当前只允许 rule 或 none。
        if (!MODE_RULE.equals(fallback) && !"none".equals(fallback)) {
            result.addError("llm_fallback_invalid");
        }
    }

    /**
     * 校验窗口配置。
     */
    private void validateWindow(CompressionConfigValidationResult result) {
        // 缺失窗口配置时给出告警并允许继续，运行期会使用默认值。
        if (properties.getWindow() == null) {
            result.addWarning("window_missing_use_default");
            return;
        }
        // 校验首段保留条数，避免负值导致窗口策略异常。
        if (properties.getWindow().getPrimersCount() < 0) {
            result.addError("window_primers_invalid");
        }
        // 校验尾段保留条数，避免负值导致窗口策略异常。
        if (properties.getWindow().getRecentsCount() < 0) {
            result.addError("window_recents_invalid");
        }
    }

    /**
     * 校验基线配置。
     */
    private void validateBaseline(CompressionConfigValidationResult result) {
        // 缺失基线配置时给出告警并允许继续，使用系统内建默认值。
        if (properties.getBaseline() == null) {
            result.addWarning("baseline_missing_use_default");
            return;
        }
        // 校验默认模式非空，避免后续策略读取空模式。
        if (!StringUtils.hasText(properties.getBaseline().getDefaultMode())) {
            result.addError("baseline_default_mode_missing");
        } else {
            // 归一化默认模式，用于统一值域判断。
            String mode = properties.getBaseline().getDefaultMode().trim().toLowerCase();
            // 校验默认模式可选值，避免灰度与回滚策略出现未知模式。
            if (!MODE_RULE.equals(mode) && !MODE_LLM.equals(mode) && !MODE_HYBRID.equals(mode)) {
                result.addError("baseline_default_mode_invalid");
            }
        }
        // 校验默认触发比例，保证阈值在合法区间。
        validateRatio(result, properties.getBaseline().getDefaultTriggerRatio(), "baseline_trigger_ratio_invalid");
        // 校验默认目标比例，保证阈值在合法区间。
        validateRatio(result, properties.getBaseline().getDefaultTargetRatio(), "baseline_target_ratio_invalid");
        // 校验默认比例关系，避免目标比例高于触发比例。
        if (properties.getBaseline().getDefaultTriggerRatio() != null
                && properties.getBaseline().getDefaultTargetRatio() != null
                && properties.getBaseline().getDefaultTargetRatio() > properties.getBaseline().getDefaultTriggerRatio()) {
            result.addError("baseline_ratio_relation_invalid");
        }
    }

    /**
     * 校验灰度配置。
     */
    private void validateRollout(CompressionConfigValidationResult result) {
        // 缺失灰度配置时给出告警并允许继续，按全量稳定模式运行。
        if (properties.getRollout() == null) {
            result.addWarning("rollout_missing_use_default");
            return;
        }
        // 校验灰度版本，确保观测事件可定位配置版本。
        if (!StringUtils.hasText(properties.getRollout().getVersion())) {
            result.addError("rollout_version_missing");
        }
        // 校验灰度比例必须在 [0,1]，避免出现非法分流。
        validateClosedRatio(result, properties.getRollout().getGlobalRatio(), "rollout_global_ratio_invalid");
    }

    /**
     * 校验质量门禁配置。
     */
    private void validateQualityGate(CompressionConfigValidationResult result) {
        // 缺失质量门禁配置时给出告警并允许继续，运行期按默认阈值执行。
        if (properties.getQualityGate() == null) {
            result.addWarning("quality_gate_missing_use_default");
            return;
        }
        // 校验质量门禁版本，确保指标与配置可追踪。
        if (!StringUtils.hasText(properties.getQualityGate().getVersion())) {
            result.addError("quality_gate_version_missing");
        }
        // 校验最低质量分区间，避免比较逻辑失真。
        validateClosedRatio(result, properties.getQualityGate().getMinScore(), "quality_gate_min_score_invalid");
        // 校验最大解析失败率区间，避免门禁阈值异常。
        validateClosedRatio(result,
                properties.getQualityGate().getMaxParseFailureRate(),
                "quality_gate_parse_failure_rate_invalid");
    }

    /**
     * 校验回滚配置。
     */
    private void validateRollback(CompressionConfigValidationResult result) {
        // 缺失回滚配置时给出告警并允许继续，按无自动回滚策略运行。
        if (properties.getRollback() == null) {
            result.addWarning("rollback_missing_use_default");
            return;
        }
        // 校验回滚版本，确保回滚策略可观测。
        if (!StringUtils.hasText(properties.getRollback().getVersion())) {
            result.addError("rollback_version_missing");
        }
        // 校验窗口分钟数，避免零或负值导致窗口逻辑失效。
        if (properties.getRollback().getTriggerWindowMinutes() <= 0) {
            result.addError("rollback_trigger_window_minutes_invalid");
        }
        // 校验最小样本数，避免样本阈值非法导致抖动。
        if (properties.getRollback().getTriggerWindowMinSamples() <= 0) {
            result.addError("rollback_trigger_window_min_samples_invalid");
        }
        // 校验恢复等待时间，避免负值导致回滚恢复时间异常。
        if (properties.getRollback().getRecoveryWaitMinutes() < 0) {
            result.addError("rollback_recovery_wait_minutes_invalid");
        }
        // 校验最大失败率阈值区间。
        validateClosedRatio(result, properties.getRollback().getMaxFailureRate(), "rollback_max_failure_rate_invalid");
        // 校验最大低分率阈值区间。
        validateClosedRatio(result, properties.getRollback().getMaxLowScoreRate(), "rollback_max_low_score_rate_invalid");
        // 校验最大超时率阈值区间。
        validateClosedRatio(result, properties.getRollback().getMaxTimeoutRate(), "rollback_max_timeout_rate_invalid");
    }

    /**
     * 校验紧急控制配置。
     */
    private void validateEmergency(CompressionConfigValidationResult result) {
        // 缺失紧急配置时直接返回，默认视为未开启紧急开关。
        if (properties.getEmergency() == null) {
            result.addWarning("emergency_missing_use_default");
            return;
        }
        // 冲突提示：禁用压缩且触发开关仍开启时给出告警，提示运行期将被紧急开关覆盖。
        if (properties.getEmergency().isDisableCompression()
                && properties.getTrigger() != null
                && properties.getTrigger().isEnabled()) {
            result.addWarning("emergency_disable_overrides_trigger");
        }
        // 冲突提示：禁用压缩时强制 rule 开关无实际效果，给出告警便于治理。
        if (properties.getEmergency().isDisableCompression() && properties.getEmergency().isForceRuleMode()) {
            result.addWarning("emergency_force_rule_ignored_when_disabled");
        }
    }

    /**
     * 校验比例值。
     */
    private void validateRatio(CompressionConfigValidationResult result, Double ratio, String errorCode) {
        // 开区间校验：用于触发阈值，要求严格大于 0 且不超过 1。
        if (ratio == null || ratio <= 0D || ratio > 1D) {
            result.addError(errorCode);
        }
    }

    /**
     * 校验闭区间比例值。
     */
    private void validateClosedRatio(CompressionConfigValidationResult result, Double ratio, String errorCode) {
        // 闭区间校验：用于质量/灰度/回滚阈值，允许 0 和 1 作为边界值。
        if (ratio == null || ratio < 0D || ratio > 1D) {
            result.addError(errorCode);
        }
    }
}
