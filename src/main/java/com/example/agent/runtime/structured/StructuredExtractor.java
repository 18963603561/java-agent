package com.example.agent.runtime.structured;

import java.util.Map;

/**
 * 结构化提取器接口。
 */
public interface StructuredExtractor {

    /**
     * 是否支持当前输入。
     */
    boolean supports(String stepType, String toolName, Map<String, Object> rawResult);

    /**
     * 执行结构化提取。
     */
    StructuredResult extract(String stepType, String toolName, Map<String, Object> rawResult, String rawRef);
}
