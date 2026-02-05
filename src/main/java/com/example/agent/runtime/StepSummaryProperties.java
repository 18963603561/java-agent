package com.example.agent.runtime;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 步骤输出摘要配置，用于控制摘要生成的开关与截断策略。
 */
@Component
@ConfigurationProperties(prefix = "agent.summary")
public class StepSummaryProperties {

    /**
     * 是否启用步骤输出摘要，默认关闭以便灰度与回滚。
     */
    private boolean enable = true;

    /**
     * 摘要整体序列化最大字符数，超过后标记为截断。
     */
    private int maxChars = 4000;

    /**
     * 列表类字段最多保留的元素数量。
     */
    private int maxListItems = 20;

    /**
     * 单个字段字符串的最大字符数。
     */
    private int maxFieldChars = 1000;
    /**
     * 是否启用原始结果受控输出。
     */
    private boolean rawEnable = true;

    /**
     * 原始结果快照最大字符数。
     */
    private int rawMaxChars = 4000;

    /**
     * 原始结果单字段最大字符数。
     */
    private int rawMaxFieldChars = 1000;

    /**
     * 原始结果列表最大条目数。
     */
    private int rawMaxListItems = 20;

    /**
     * 获取是否启用步骤输出摘要。
     *
     * @return 是否启用步骤输出摘要
     */
    public boolean isEnable() {
        return enable;
    }

    public void setEnable(boolean enable) {
        this.enable = enable;
    }

    public int getMaxChars() {
        return maxChars;
    }

    public void setMaxChars(int maxChars) {
        this.maxChars = maxChars;
    }

    public int getMaxListItems() {
        return maxListItems;
    }

    public void setMaxListItems(int maxListItems) {
        this.maxListItems = maxListItems;
    }

    public int getMaxFieldChars() {
        return maxFieldChars;
    }

    public void setMaxFieldChars(int maxFieldChars) {
        this.maxFieldChars = maxFieldChars;
    }

    public boolean isRawEnable() {
        return rawEnable;
    }

    public void setRawEnable(boolean rawEnable) {
        this.rawEnable = rawEnable;
    }

    public int getRawMaxChars() {
        return rawMaxChars;
    }

    public void setRawMaxChars(int rawMaxChars) {
        this.rawMaxChars = rawMaxChars;
    }

    public int getRawMaxFieldChars() {
        return rawMaxFieldChars;
    }

    public void setRawMaxFieldChars(int rawMaxFieldChars) {
        this.rawMaxFieldChars = rawMaxFieldChars;
    }

    public int getRawMaxListItems() {
        return rawMaxListItems;
    }

    public void setRawMaxListItems(int rawMaxListItems) {
        this.rawMaxListItems = rawMaxListItems;
    }
}
