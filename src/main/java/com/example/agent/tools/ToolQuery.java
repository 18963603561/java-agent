package com.example.agent.tools;

import java.util.List;

/**
 * 工具查询条件。
 */
public class ToolQuery {

    /**
     * 必须包含的标签。
     */
    private List<String> requiredTags;

    /**
     * 授权范围。
     */
    private List<String> allowedScopes;

    /**
     * 语言偏好。
     */
    private String locale;

    public List<String> getRequiredTags() {
        return requiredTags;
    }

    public void setRequiredTags(List<String> requiredTags) {
        this.requiredTags = requiredTags;
    }

    public List<String> getAllowedScopes() {
        return allowedScopes;
    }

    public void setAllowedScopes(List<String> allowedScopes) {
        this.allowedScopes = allowedScopes;
    }

    public String getLocale() {
        return locale;
    }

    public void setLocale(String locale) {
        this.locale = locale;
    }
}