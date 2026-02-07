package com.example.agent.runtime.model.input;

import java.util.Locale;
import java.util.Map;

/**
 * 审批输入对象。
 *
 * <p>用途：统一承载审批相关高频字段，避免核心链路直接读写动态键。
 * <p>输入：来自步骤策略、步骤上下文或运行时上下文的审批字段。
 * <p>输出：供审批门禁与上下文更新逻辑读取的强类型对象。
 */
public class ApprovalInput {

    /**
     * 是否需要审批。
     */
    private final Boolean requiresApproval;

    /**
     * 审批来源。
     */
    private final String approvalSource;

    private ApprovalInput(Boolean requiresApproval, String approvalSource) {
        this.requiresApproval = requiresApproval;
        this.approvalSource = normalizeSource(approvalSource);
    }

    /**
     * 创建空审批对象。
     *
     * @return 空审批对象
     */
    public static ApprovalInput empty() {
        return new ApprovalInput(null, null);
    }

    /**
     * 创建审批对象。
     *
     * @param requiresApproval 审批标记
     * @param approvalSource 审批来源
     * @return 审批对象
     */
    public static ApprovalInput of(Boolean requiresApproval, String approvalSource) {
        return new ApprovalInput(requiresApproval, approvalSource);
    }

    /**
     * 从动态映射中解析审批对象。
     *
     * @param map 动态映射
     * @return 审批对象
     */
    public static ApprovalInput fromMap(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return empty();
        }
        return fromValues(map.get("requiresApproval"), map.get("approvalSource"));
    }

    /**
     * 从任意值解析审批对象。
     *
     * @param requiresApprovalValue 审批标记值
     * @param approvalSourceValue 审批来源值
     * @return 审批对象
     */
    public static ApprovalInput fromValues(Object requiresApprovalValue, Object approvalSourceValue) {
        Boolean requiresApproval = toBoolean(requiresApprovalValue);
        String approvalSource = approvalSourceValue == null ? null : String.valueOf(approvalSourceValue);
        return new ApprovalInput(requiresApproval, approvalSource);
    }

    /**
     * 判断是否显式声明审批信息。
     *
     * @return true 表示包含审批字段
     */
    public boolean isExplicit() {
        return requiresApproval != null || (approvalSource != null && !approvalSource.isBlank());
    }

    /**
     * 判断是否要求审批。
     *
     * @return true 表示要求审批
     */
    public boolean isRequired() {
        return Boolean.TRUE.equals(requiresApproval);
    }

    public Boolean getRequiresApproval() {
        return requiresApproval;
    }

    public String getApprovalSource() {
        return approvalSource;
    }

    private static Boolean toBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String text) {
            if (text.isBlank()) {
                return null;
            }
            return "true".equalsIgnoreCase(text.trim());
        }
        return null;
    }

    private static String normalizeSource(String source) {
        if (source == null || source.isBlank()) {
            return null;
        }
        return source.trim().toLowerCase(Locale.ROOT);
    }
}

