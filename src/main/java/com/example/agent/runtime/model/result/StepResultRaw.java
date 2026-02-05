package com.example.agent.runtime.model.result;

import java.util.Map;

/**
 * 步骤原始结果快照。
 */
public class StepResultRaw {

    private Map<String, Object> data;
    private Boolean truncated;

    public Map<String, Object> getData() {
        return data;
    }

    public void setData(Map<String, Object> data) {
        this.data = data;
    }

    public Boolean getTruncated() {
        return truncated;
    }

    public void setTruncated(Boolean truncated) {
        this.truncated = truncated;
    }
}
