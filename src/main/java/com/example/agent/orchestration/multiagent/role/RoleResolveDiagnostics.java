package com.example.agent.orchestration.multiagent.role;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 角色解析诊断组件。
 * <p>用途：统一产出解析错误分类，避免上层直接拼接错误类型字符串。</p>
 */
@Component
public class RoleResolveDiagnostics {

    /**
     * 根据解析阶段结果确定错误类型。
     *
     * @param rawContent 原始输出
     * @param parseErrorType 解析阶段错误类型
     * @return 规范化错误类型
     */
    public String resolveParseErrorType(String rawContent, String parseErrorType) {
        // 关键逻辑：空输出优先判定，便于运营区分模型未返回与结构错误。
        if (!StringUtils.hasText(rawContent)) {
            return "empty_output";
        }
        if (!StringUtils.hasText(parseErrorType)) {
            return "json_parse_error";
        }
        return parseErrorType;
    }
}

