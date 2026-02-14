package com.example.agent.api.http.response;

import java.util.Locale;
import org.springframework.util.StringUtils;

/**
 * HTTP 响应模式。
 * <p>用途：控制对外返回是否进行瘦身处理。
 */
public enum ResponseMode {

    /**
     * 紧凑模式：移除空值并去重重复字段。
     */
    COMPACT("compact"),

    /**
     * 完整模式：保留原始结构，不进行瘦身。
     */
    FULL("full");

    /**
     * 对外协议使用的小写值。
     */
    private final String value;

    ResponseMode(String value) {
        this.value = value;
    }

    /**
     * 获取协议值。
     *
     * @return 小写模式值
     */
    public String value() {
        return value;
    }

    /**
     * 解析外部传入的响应模式。
     *
     * @param rawMode 原始模式字符串
     * @return 解析后的枚举；非法值返回 {@code null}
     */
    public static ResponseMode from(String rawMode) {
        if (!StringUtils.hasText(rawMode)) {
            return null;
        }
        String normalized = rawMode.trim().toLowerCase(Locale.ROOT);
        for (ResponseMode mode : values()) {
            if (mode.value.equals(normalized)) {
                return mode;
            }
        }
        return null;
    }
}
