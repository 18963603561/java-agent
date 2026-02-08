package com.example.agent.capabilities.tools.validation;

import com.example.agent.common.error.ErrorCodeException;
import com.example.agent.security.auth.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;

/**
 * 工具域入口请求校验器。
 *
 * <p>用途：统一工具域关键入口的空值与非法参数校验，收敛错误码与错误消息风格。</p>
 * <p>输入：请求对象、租户上下文、字符串参数等。</p>
 * <p>输出：校验通过后的原值或规范化字符串。</p>
 * <p>边界：任何关键参数缺失时抛出 {@code INVALID_REQUEST}，并返回 {@code BAD_REQUEST}。</p>
 */
public final class ToolRequestValidator {

    private static final String INVALID_REQUEST = "INVALID_REQUEST";

    private ToolRequestValidator() {
    }

    /**
     * 校验对象参数非空。
     *
     * @param value 参数值
     * @param fieldName 参数名称
     * @param <T> 参数类型
     * @return 原参数值
     */
    public static <T> T requireNonNull(T value, String fieldName) {
        if (value == null) {
            throw invalid(fieldName, "不能为空");
        }
        return value;
    }

    /**
     * 校验字符串参数非空白，并返回去除首尾空格后的结果。
     *
     * @param value 参数值
     * @param fieldName 参数名称
     * @return 规范化后的字符串
     */
    public static String requireNonBlank(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            throw invalid(fieldName, "不能为空");
        }
        return value.trim();
    }

    /**
     * 校验租户上下文与租户标识。
     *
     * @param tenantContext 租户上下文
     * @return 原租户上下文
     */
    public static TenantContext requireTenantContext(TenantContext tenantContext) {
        requireNonNull(tenantContext, "tenantContext");
        requireNonBlank(tenantContext.getTenantId(), "tenantContext.tenantId");
        return tenantContext;
    }

    private static ErrorCodeException invalid(String fieldName, String reason) {
        return new ErrorCodeException(HttpStatus.BAD_REQUEST, INVALID_REQUEST,
                fieldName + " " + reason);
    }
}

