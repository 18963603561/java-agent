package com.example.agent.planning.parser;

/**
 * 规划解析错误类型常量。
 */
public final class PlanParseErrorTypes {

    public static final String EMPTY_OUTPUT = "empty_output";
    public static final String JSON_PARSE_ERROR = "json_parse_error";
    public static final String MISSING_STEPS = "missing_steps";
    public static final String INVALID_TOOL_ARGUMENTS = "invalid_tool_arguments";
    public static final String NO_VALID_STEPS = "no_valid_steps";

    private PlanParseErrorTypes() {
    }
}

