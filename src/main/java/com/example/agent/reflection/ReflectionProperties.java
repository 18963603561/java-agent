package com.example.agent.reflection;

import java.util.ArrayList;
import java.util.List;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * 反思配置属性，用于控制阈值与重试策略。
 */
@Component
@Validated
@ConfigurationProperties(prefix = "agent.reflection")
public class ReflectionProperties {

    /**
     * 是否启用反思能力。
     */
    private boolean enabled = true;

    /**
     * 是否启用模型反思。
     */
    private boolean llmEnabled = true;

    /**
     * 模型反思失败时是否允许回退。
     */
    private boolean fallbackEnabled = true;

    /**
     * 最大反思重试次数，超过后不再请求重试。
     */
    @Min(1)
    private int maxRetries = 1;

    /**
     * 质量阈值，低于阈值将触发重试建议。
     */
    @DecimalMin("0.0")
    @DecimalMax("1.0")
    private double confidenceThreshold = 0.7;

    /**
     * 最小输出字符数，低于该值判定为质量不足。
     */
    @Min(1)
    private int minOutputChars = 40;

    /**
     * 必须包含的关键字段，用于识别结构化输出是否完整。
     */
    @NotNull
    @NotEmpty
    private List<String> requiredKeys = new ArrayList<>(List.of("result", "data"));

    /**
     * 失败关键词，出现时会降低评分。
     */
    @NotNull
    private List<String> failureKeywords = new ArrayList<>(List.of("error", "failed", "exception", "失败", "错误", "异常"));

    /**
     * 反思提示词模板版本。
     */
    @NotNull
    @NotEmpty
    private String promptVersion = "v1";

    /**
     * 反思提示词是否启用严格模式。
     */
    private boolean promptStrict = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isLlmEnabled() {
        return llmEnabled;
    }

    public void setLlmEnabled(boolean llmEnabled) {
        this.llmEnabled = llmEnabled;
    }

    public boolean isFallbackEnabled() {
        return fallbackEnabled;
    }

    public void setFallbackEnabled(boolean fallbackEnabled) {
        this.fallbackEnabled = fallbackEnabled;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    public double getConfidenceThreshold() {
        return confidenceThreshold;
    }

    public void setConfidenceThreshold(double confidenceThreshold) {
        this.confidenceThreshold = confidenceThreshold;
    }

    public int getMinOutputChars() {
        return minOutputChars;
    }

    public void setMinOutputChars(int minOutputChars) {
        this.minOutputChars = minOutputChars;
    }

    public List<String> getRequiredKeys() {
        return requiredKeys;
    }

    public void setRequiredKeys(List<String> requiredKeys) {
        this.requiredKeys = requiredKeys;
    }

    public List<String> getFailureKeywords() {
        return failureKeywords;
    }

    public void setFailureKeywords(List<String> failureKeywords) {
        this.failureKeywords = failureKeywords;
    }

    public String getPromptVersion() {
        return promptVersion;
    }

    public void setPromptVersion(String promptVersion) {
        this.promptVersion = promptVersion;
    }

    public boolean isPromptStrict() {
        return promptStrict;
    }

    public void setPromptStrict(boolean promptStrict) {
        this.promptStrict = promptStrict;
    }
}
