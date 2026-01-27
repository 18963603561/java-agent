package com.example.agent.model;

import java.util.Locale;

/**
 * 模型工具选择策略，支持指定工具名称。
 */
public class ModelToolChoice {

    /**
     * 工具选择模式。
     */
    public enum Mode {
        AUTO,
        NONE,
        REQUIRED,
        SPECIFIED
    }

    /**
     * 选择模式。
     */
    private Mode mode;
    /**
     * 指定工具名称，仅在 SPECIFIED 模式下生效。
     */
    private String toolName;

    public ModelToolChoice() {
    }

    public ModelToolChoice(Mode mode, String toolName) {
        this.mode = mode;
        this.toolName = toolName;
    }

    public static ModelToolChoice auto() {
        return new ModelToolChoice(Mode.AUTO, null);
    }

    public static ModelToolChoice none() {
        return new ModelToolChoice(Mode.NONE, null);
    }

    public static ModelToolChoice required() {
        return new ModelToolChoice(Mode.REQUIRED, null);
    }

    public static ModelToolChoice specified(String toolName) {
        return new ModelToolChoice(Mode.SPECIFIED, toolName);
    }

    /**
     * 从字符串解析工具选择策略，支持 specified:toolName 形式。
     *
     * @param value 输入字符串
     * @return 工具选择策略
     */
    public static ModelToolChoice fromString(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (lower.startsWith("specified:")) {
            String tool = trimmed.substring("specified:".length()).trim();
            return specified(tool);
        }
        if ("auto".equals(lower)) {
            return auto();
        }
        if ("none".equals(lower)) {
            return none();
        }
        if ("required".equals(lower)) {
            return required();
        }
        return null;
    }

    public Mode getMode() {
        return mode;
    }

    public void setMode(Mode mode) {
        this.mode = mode;
    }

    public String getToolName() {
        return toolName;
    }

    public void setToolName(String toolName) {
        this.toolName = toolName;
    }
}
