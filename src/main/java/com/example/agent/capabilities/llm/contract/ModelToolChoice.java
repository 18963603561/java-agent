package com.example.agent.capabilities.llm.contract;

import java.util.Map;
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

    /**
     * 空构造方法，便于序列化。
     */
    public ModelToolChoice() {
    }

    /**
     * 构造工具选择策略。
     *
     * @param mode 选择模式
     * @param toolName 指定工具名称
     */
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

    /**
     * 从任意对象解析工具选择策略。
     *
     * <p>用途：统一承接字符串/对象映射等输入形态，避免消费方散落对 {@code Map} 的硬编码键访问。</p>
     *
     * @param raw 输入对象
     * @return 工具选择策略
     */
    public static ModelToolChoice fromRaw(Object raw) {
        if (raw instanceof ModelToolChoice choice) {
            return choice;
        }
        if (raw instanceof String value) {
            return fromString(value);
        }
        if (!(raw instanceof Map<?, ?> map) || map.isEmpty()) {
            return null;
        }
        String mode = readString(map, "mode");
        if (!hasText(mode)) {
            mode = readString(map, "type");
        }
        String name = readString(map, "toolName");
        if (!hasText(name)) {
            name = readString(map, "name");
        }
        if (hasText(mode) && "specified".equalsIgnoreCase(mode)) {
            return specified(name);
        }
        ModelToolChoice parsed = fromString(mode);
        if (parsed != null && parsed.getMode() == Mode.SPECIFIED && hasText(name)) {
            parsed.setToolName(name);
        }
        return parsed;
    }

    private static String readString(Map<?, ?> map, String key) {
        if (map == null || key == null) {
            return null;
        }
        Object value = map.get(key);
        if (value == null) {
            return null;
        }
        String text = value.toString();
        return text == null ? null : text.trim();
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
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
