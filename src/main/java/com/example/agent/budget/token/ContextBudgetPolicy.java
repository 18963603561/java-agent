package com.example.agent.budget.token;

import java.util.EnumMap;
import java.util.Map;
import com.example.agent.budget.trim.ContextSection;

/**
 * 上下文预算策略，用于描述分段比例与版本信息。
 */
public class ContextBudgetPolicy {

    /**
     * 策略版本号，默认 v1。
     */
    private String version = "v1";

    /**
     * 分段比例映射，key 为分段类型，value 为比例，范围 0-1。
     */
    private Map<ContextSection, Double> sectionRatios = new EnumMap<>(ContextSection.class);

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public Map<ContextSection, Double> getSectionRatios() {
        return sectionRatios;
    }

    public void setSectionRatios(Map<ContextSection, Double> sectionRatios) {
        this.sectionRatios = sectionRatios;
    }
}
