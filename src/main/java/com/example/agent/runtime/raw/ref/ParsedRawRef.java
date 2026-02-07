package com.example.agent.runtime.raw.ref;

import com.example.agent.runtime.raw.RawStoreType;

/**
 * 统一引用解析结果。
 *
 * @param version 协议版本
 * @param storeType 存储类型
 * @param id 存储标识
 */
public record ParsedRawRef(String version, RawStoreType storeType, String id) {
}

