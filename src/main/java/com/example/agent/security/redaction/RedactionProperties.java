package com.example.agent.security.redaction;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 脱敏配置，用于控制规则与扫描范围。
 */
@Component
@ConfigurationProperties(prefix = "agent.security.redaction")
public class RedactionProperties {

    /**
     * 是否启用脱敏治理。
     */
    private boolean enabled = true;

    /**
     * 命中密钥类规则时是否拒写。
     */
    private boolean rejectOnSecrets = true;

    /**
     * 命中个人信息时是否脱敏替换。
     */
    private boolean redactOnPii = true;

    /**
     * 忽略字段列表。
     */
    private List<String> ignoreKeys = new ArrayList<>();

    /**
     * 单次扫描最大字符数。
     */
    private int maxScanChars = 8000;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isRejectOnSecrets() {
        return rejectOnSecrets;
    }

    public void setRejectOnSecrets(boolean rejectOnSecrets) {
        this.rejectOnSecrets = rejectOnSecrets;
    }

    public boolean isRedactOnPii() {
        return redactOnPii;
    }

    public void setRedactOnPii(boolean redactOnPii) {
        this.redactOnPii = redactOnPii;
    }

    public List<String> getIgnoreKeys() {
        return ignoreKeys;
    }

    public void setIgnoreKeys(List<String> ignoreKeys) {
        this.ignoreKeys = ignoreKeys;
    }

    public int getMaxScanChars() {
        return maxScanChars;
    }

    public void setMaxScanChars(int maxScanChars) {
        this.maxScanChars = maxScanChars;
    }
}
