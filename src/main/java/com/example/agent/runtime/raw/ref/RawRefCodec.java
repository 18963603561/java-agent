package com.example.agent.runtime.raw.ref;

import java.util.Objects;

import com.example.agent.runtime.raw.RawStoreType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 原始引用编码与解码器。
 *
 * <p>用途：统一构建与解析 rawref 协议标识，减少魔法字符串分散。
 */
@Component
public class RawRefCodec {

    /**
     * 协议前缀。
     */
    public static final String PREFIX = "rawref";

    /**
     * 协议版本。
     */
    public static final String VERSION = "v1";

    /**
     * 编码统一引用标识。
     *
     * @param storeType 存储类型
     * @param id 存储标识
     * @return 引用标识
     */
    public String encode(RawStoreType storeType, String id) {
        RawStoreType safeType = Objects.requireNonNull(storeType, "storeType 不能为空");
        if (!StringUtils.hasText(id)) {
            throw new IllegalArgumentException("id 不能为空");
        }
        return String.join(":", PREFIX, VERSION, safeType.getCode(), id.trim());
    }

    /**
     * 解析统一引用标识。
     *
     * @param refId 引用标识
     * @return 解析结果
     */
    public ParsedRawRef parse(String refId) {
        if (!StringUtils.hasText(refId)) {
            throw new IllegalArgumentException("refId 不能为空");
        }
        String trimmed = refId.trim();
        String[] parts = trimmed.split(":", 4);
        if (parts.length < 4) {
            throw new IllegalArgumentException("非法 rawref 格式: " + refId);
        }
        if (!PREFIX.equals(parts[0])) {
            throw new IllegalArgumentException("非法 rawref 前缀: " + refId);
        }
        if (!VERSION.equals(parts[1])) {
            throw new IllegalArgumentException("不支持的 rawref 版本: " + refId);
        }
        RawStoreType storeType = RawStoreType.fromCode(parts[2]);
        if (!StringUtils.hasText(parts[3])) {
            throw new IllegalArgumentException("rawref id 不能为空: " + refId);
        }
        return new ParsedRawRef(VERSION, storeType, parts[3]);
    }

    /**
     * 判断是否为统一引用标识。
     *
     * @param refId 引用标识
     * @return true 表示匹配 rawref 协议
     */
    public boolean isRawRef(String refId) {
        if (!StringUtils.hasText(refId)) {
            return false;
        }
        return refId.trim().startsWith(PREFIX + ":");
    }
}

