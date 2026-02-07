package com.example.agent.runtime.structured.structured;

import java.util.Map;

/**
 * 结构化数据抽象接口。
 *
 * <p>用途：定义结构化数据对象与通用键值视图之间的转换契约，
 * 便于旧链路继续以 Map 方式消费，也便于新链路以强类型消费。
 */
public interface StructuredData {

    /**
     * 导出为通用键值视图。
     *
     * @return 当前数据对象的 Map 表示，禁止返回 null
     */
    Map<String, Object> toMap();
}

