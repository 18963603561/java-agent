package com.example.agent.runtime.raw.store;

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
}
