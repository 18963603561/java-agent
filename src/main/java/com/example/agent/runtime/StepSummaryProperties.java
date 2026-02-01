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
    private boolean enable = false;

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
    private int maxFieldChars = 200;

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
}
