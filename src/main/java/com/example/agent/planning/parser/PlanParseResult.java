package com.example.agent.planning.parser;

import com.example.agent.runtime.model.StepSpec;
import java.util.List;

/**
 * 规划解析结果。
 *
 * <p>用途：承载解析后的摘要与步骤列表。
 */
public class PlanParseResult {

    private final String summary;
    private final List<StepSpec> steps;

    /**
     * 构造解析结果。
     *
     * @param summary 摘要
     * @param steps 步骤列表
     */
    public PlanParseResult(String summary, List<StepSpec> steps) {
        this.summary = summary;
        this.steps = steps;
    }

    public String getSummary() {
        return summary;
    }

    public List<StepSpec> getSteps() {
        return steps;
    }
}

