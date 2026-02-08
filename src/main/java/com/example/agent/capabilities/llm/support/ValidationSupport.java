package com.example.agent.capabilities.llm.support;

import com.example.agent.common.error.ErrorCodeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * LLM 模块参数校验工具。
 *
 * <p>用途：统一处理关键入参的非空校验与默认值修正，减少散落防御逻辑。
 * <p>输入：字符串与数字等基础参数。
 * <p>输出：校验通过后的规范化值，或抛出统一错误码异常。
 * <p>边界：仅对 LLM 相关校验负责，不承担复杂业务规则判定。
 */
@Component
public class ValidationSupport {

    private static final Logger log = LoggerFactory.getLogger(ValidationSupport.class);

    /**
     * 校验必填字符串并返回去空白后的值。
     *
     * @param value 输入值
     * @param fieldName 字段名称
     * @param errorCode 错误码
     * @param message 错误信息
     * @return 规范化后的字符串
     */
    public String requireText(String value, String fieldName, String errorCode, String message) {
        if (!StringUtils.hasText(value)) {
            throw invalid(fieldName, errorCode, message);
        }
        return value.trim();
    }

    /**
     * 校验可选字符串，返回去空白后的值或默认值。
     *
     * @param value 输入值
     * @param defaultValue 默认值
     * @return 规范化后的字符串
     */
    public String normalizeText(String value, String defaultValue) {
        if (!StringUtils.hasText(value)) {
            return defaultValue;
        }
        return value.trim();
    }

    /**
     * 修正最大重试次数。
     *
     * @param maxAttempts 输入重试次数
     * @param defaultValue 默认值
     * @param sceneId 场景标识
     * @return 修正后的重试次数
     */
    public int normalizeMaxAttempts(int maxAttempts, int defaultValue, String sceneId) {
        if (maxAttempts > 0) {
            return maxAttempts;
        }
        log.warn("maxAttempts 非法, sceneId={}, maxAttempts={}, fallback={}", sceneId, maxAttempts, defaultValue);
        return defaultValue;
    }

    /**
     * 创建统一无效参数异常。
     *
     * @param fieldName 字段名称
     * @param errorCode 错误码
     * @param message 错误信息
     * @return 参数异常
     */
    public ErrorCodeException invalid(String fieldName, String errorCode, String message) {
        log.warn("参数校验失败, field={}, code={}, message={}", fieldName, errorCode, message);
        return new ErrorCodeException(HttpStatus.BAD_REQUEST, errorCode, message);
    }
}
