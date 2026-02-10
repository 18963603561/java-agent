package com.example.agent.orchestration.task;

import org.springframework.util.StringUtils;

/**
 * 任务分页查询参数。
 * <p>用途：统一描述仓储层分页查询输入，避免编排层传入散落参数。
 * <p>输入：租户、状态过滤、游标、分页大小。
 * <p>边界：当分页大小非法时自动回退默认值。
 */
public class TaskPageQuery {

    /**
     * 默认分页大小。
     */
    public static final int DEFAULT_PAGE_SIZE = 20;

    /**
     * 租户标识。
     */
    private final String tenantId;

    /**
     * 状态过滤。
     */
    private final String status;

    /**
     * 翻页游标。
     */
    private final String cursor;

    /**
     * 分页大小。
     */
    private final int size;

    public TaskPageQuery(String tenantId, String status, String cursor, Integer size) {
        if (!StringUtils.hasText(tenantId)) {
            throw new IllegalArgumentException("tenant_id_required");
        }
        this.tenantId = tenantId;
        this.status = normalize(status);
        this.cursor = normalize(cursor);
        this.size = size != null && size > 0 ? size : DEFAULT_PAGE_SIZE;
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getStatus() {
        return status;
    }

    public String getCursor() {
        return cursor;
    }

    public int getSize() {
        return size;
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}

