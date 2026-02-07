package com.example.agent.runtime.structured.extractor;

import com.example.agent.runtime.structured.structured.StructuredData;
import com.example.agent.runtime.structured.result.StructuredResult;

import java.util.Map;

/**
 * 结构化提取器接口。
 *
 * <p>用途：将工具或模型原始输出映射为统一的结构化语义结果。
 */
public interface StructuredExtractor {

    /**
     * 判断提取器是否支持当前输入。
     *
     * @param stepType 步骤类型
     * @param toolName 工具名称
     * @param rawResult 原始结果
     * @return true 表示支持
     */
    boolean supports(String stepType, String toolName, Map<String, Object> rawResult);

    /**
     * 执行结构化提取。
     *
     * @param stepType 步骤类型
     * @param toolName 工具名称
     * @param rawResult 原始结果
     * @param rawRef 原始结果引用
     * @return 结构化结果
     */
    StructuredResult<? extends StructuredData> extract(String stepType,
                                                       String toolName,
                                                       Map<String, Object> rawResult,
                                                       String rawRef);
}

