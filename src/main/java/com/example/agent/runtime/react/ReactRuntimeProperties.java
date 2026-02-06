package com.example.agent.runtime.react;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * ReAct 循环运行参数，控制最小/最大轮次与观察窗口大小。
 */
@Component
@ConfigurationProperties(prefix = "agent.runtime")
public class ReactRuntimeProperties {

    /**
     * ReAct 最大迭代次数，达到后必须终止。
     */
    private int maxIterations = 10;

    /**
     * ReAct 最小迭代次数，未达到不得提前完成。
     */
    private int minIterations = 1;

    /**
     * 观察窗口大小（按条数截断）。
     */
    private int observationWindow = 3;

    public int getMaxIterations() {
        return maxIterations;
    }

    public void setMaxIterations(int maxIterations) {
        this.maxIterations = maxIterations;
    }

    public int getMinIterations() {
        return minIterations;
    }

    public void setMinIterations(int minIterations) {
        this.minIterations = minIterations;
    }

    public int getObservationWindow() {
        return observationWindow;
    }

    public void setObservationWindow(int observationWindow) {
        this.observationWindow = observationWindow;
    }
}
