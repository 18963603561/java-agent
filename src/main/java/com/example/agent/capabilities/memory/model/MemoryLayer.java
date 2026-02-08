package com.example.agent.capabilities.memory.model;

import java.util.Locale;
import org.springframework.util.StringUtils;

/**
 * 记忆分层常量定义，统一管理 layer 字段取值与判定逻辑。
 */
public enum MemoryLayer {

    RECENT,
    COMPRESSED;

    /**
     * 返回标准层级字符串。
     *
     * @return 标准层级值
     */
    public String value() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * 判断输入层级是否为压缩层。
     *
     * @param layer 输入层级
     * @return 是否为压缩层
     */
    public static boolean isCompressed(String layer) {
        return matches(layer, COMPRESSED);
    }

    /**
     * 判断输入层级是否为最近层。
     *
     * @param layer 输入层级
     * @return 是否为最近层
     */
    public static boolean isRecent(String layer) {
        return matches(layer, RECENT);
    }

    /**
     * 判断输入层级是否匹配目标层级。
     *
     * @param layer 输入层级
     * @param target 目标层级
     * @return 是否匹配
     */
    public static boolean matches(String layer, MemoryLayer target) {
        return StringUtils.hasText(layer) && target != null && target.value().equalsIgnoreCase(layer);
    }
}
