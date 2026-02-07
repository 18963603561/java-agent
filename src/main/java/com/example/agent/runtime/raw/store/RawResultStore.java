package com.example.agent.runtime.raw.store;

import com.example.agent.runtime.raw.RawStoreType;
import com.example.agent.runtime.raw.ref.RawRef;

/**
 * 原始结果存储接口。
 */
public interface RawResultStore {

    /**
     * 保存原始结果并返回引用。
     *
     * @param source 来源标识
     * @param payload 原始对象
     * @param mediaType 媒体类型
     * @return 原始结果引用
     */
    RawRef store(String source, Object payload, String mediaType);

    /**
     * 获取存储类型。
     *
     * @return 存储类型
     */
    RawStoreType storeType();

    /**
     * 判断是否支持指定存储类型。
     *
     * @param storeType 存储类型
     * @return true 表示支持
     */
    default boolean supports(RawStoreType storeType) {
        if (storeType == null) {
            return false;
        }
        if (storeType() == storeType) {
            return true;
        }
        return storeType() == RawStoreType.FILE && storeType == RawStoreType.TXT;
    }

    /**
     * 按统一引用读取文本。
     *
     * @param refId 统一引用标识
     * @return 原始文本，不存在时返回 null
     */
    default String loadByRefId(String refId) {
        throw new UnsupportedOperationException("当前存储实现未支持按 refId 读取");
    }

    /**
     * 按存储标识读取文本。
     *
     * @param storeId 存储标识
     * @return 原始文本，不存在时返回 null
     */
    String loadByStoreId(String storeId);
}
