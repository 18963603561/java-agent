package com.example.agent.reflection.parser;

/**
 * 反思解析结果封装。
 *
 * <p>用途：统一承载解析成功/失败状态与失败类型，替代异常与 null 混合语义。</p>
 */
public class ReflectionParseOutcome {

    /**
     * 是否解析成功。
     */
    private final boolean success;

    /**
     * 解析成功时的结果对象。
     */
    private final ReflectionParsingResult parsingResult;

    /**
     * 解析失败类型。
     */
    private final String errorType;

    private ReflectionParseOutcome(boolean success,
                                   ReflectionParsingResult parsingResult,
                                   String errorType) {
        this.success = success;
        this.parsingResult = parsingResult;
        this.errorType = errorType;
    }

    /**
     * 创建成功结果。
     *
     * @param parsingResult 解析结果
     * @return 成功封装对象
     */
    public static ReflectionParseOutcome success(ReflectionParsingResult parsingResult) {
        return new ReflectionParseOutcome(true, parsingResult, null);
    }

    /**
     * 创建失败结果。
     *
     * @param errorType 失败类型
     * @return 失败封装对象
     */
    public static ReflectionParseOutcome failure(String errorType) {
        return new ReflectionParseOutcome(false, null, errorType);
    }

    public boolean isSuccess() {
        return success;
    }

    public ReflectionParsingResult getParsingResult() {
        return parsingResult;
    }

    public String getErrorType() {
        return errorType;
    }
}

