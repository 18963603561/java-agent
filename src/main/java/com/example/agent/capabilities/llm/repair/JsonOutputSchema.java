package com.example.agent.capabilities.llm.repair;

/**
 * JSON 修复期望结构枚举。
 */
public enum JsonOutputSchema {
    PLANNER(
            "summary: string, steps: array, steps[*].type: string, steps[*].input: object, "
                    + "steps[*].tool: string(可空), steps[*].dependsOn: array(可空)"),
    REFLECTION(
            "score: number, retry: boolean, notes: string"),
    FINAL(
            "answer: string, highlights: string, confidence: number"),
    REACT(
            "action: string(tool/stop/none), tool: string, arguments: object, shouldStop: boolean, "
                    + "stopReason: string, finalAnswer: string"),
    COT(
            "stepSummary: string, shouldContinue: boolean, finalAnswer: string, confidence: number, stopReason: string"),
    RESEARCH(
            "citations: array, citations[*].source: string, citations[*].snippet: string"),
    DEBATE(
            "conclusion: string"),
    MULTIAGENT(
            "team: array, team[*].roleId: string, team[*].name: string, team[*].modelId: string(可空), "
                    + "team[*].description: string");

    private final String constraintText;

    JsonOutputSchema(String constraintText) {
        this.constraintText = constraintText;
    }

    public String constraintText() {
        return constraintText;
    }
}
