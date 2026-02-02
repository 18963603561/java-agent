package com.example.agent.runtime;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 受控原始输出配置，用于在禁用摘要时保留关键原始结果。
 */
@Component
@ConfigurationProperties(prefix = "agent.summary.raw-output")
public class RawOutputProperties {

    /**
     * 是否启用受控原始输出。
     */
    private boolean enable = false;

    /**
     * 原始输出最大字符数，超过会截断。
     */
    private int maxChars = 2000;

    /**
     * 列表或映射允许采样的最大条目数。
     */
    private int maxListItems = 20;

    /**
     * 单字段最大字符数。
     */
    private int maxFieldChars = 200;

    /**
     * 允许输出的字段白名单（大小写不敏感）。
     */
    private List<String> allowKeys = new ArrayList<>(List.of(
            "answer",
            "result",
            "data",
            "error",
            "message",
            "code",
            "status",
            "tool",
            "toolName",
            "highlights"
    ));

    /**
     * 需要脱敏的字段列表（大小写不敏感）。
     */
    private List<String> maskKeys = new ArrayList<>(List.of(
            "password",
            "token",
            "secret",
            "authorization",
            "apiKey",
            "accessKey",
            "refreshToken",
            "credential"
    ));

    public boolean isEnable() {
        return enable;
    }

    public void setEnable(boolean enable) {
        this.enable = enable;
    }

    public int getMaxChars() {
        return maxChars;
    }

    public void setMaxChars(int maxChars) {
        this.maxChars = maxChars;
    }

    public int getMaxListItems() {
        return maxListItems;
    }

    public void setMaxListItems(int maxListItems) {
        this.maxListItems = maxListItems;
    }

    public int getMaxFieldChars() {
        return maxFieldChars;
    }

    public void setMaxFieldChars(int maxFieldChars) {
        this.maxFieldChars = maxFieldChars;
    }

    public List<String> getAllowKeys() {
        return allowKeys;
    }

    public void setAllowKeys(List<String> allowKeys) {
        this.allowKeys = allowKeys;
    }

    public List<String> getMaskKeys() {
        return maskKeys;
    }

    public void setMaskKeys(List<String> maskKeys) {
        this.maskKeys = maskKeys;
    }
}
