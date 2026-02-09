package com.example.agent.governance.common.state;

/**
 * 状态记录匹配器。
 *
 * @param <T> 记录类型
 */
@FunctionalInterface
public interface StoreEntryMatcher<T> {

    /**
     * 判断记录是否匹配。
     *
     * @param value 记录
     * @return 是否匹配
     */
    boolean matches(T value);
}

