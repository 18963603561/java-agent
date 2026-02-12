package com.example.agent.capabilities.context.compression.parser;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 压缩响应解析器。
 */
@Component
public class CompressionResponseParser {

    private final ObjectMapper objectMapper;
    private final CompressionValidationPolicy validationPolicy;

    public CompressionResponseParser(ObjectMapper objectMapper,
                                     CompressionValidationPolicy validationPolicy) {
        this.objectMapper = objectMapper;
        this.validationPolicy = validationPolicy;
    }

    /**
     * 解析模型输出。
     *
     * @param content 模型输出
     * @return 解析结果
     */
    public CompressionParseResult parse(String content) {
        CompressionParseResult result = new CompressionParseResult();
        if (!StringUtils.hasText(content)) {
            result.setSuccess(false);
            result.setFailureReason("LLM_EMPTY");
            return result;
        }
        Map<String, Object> root;
        try {
            // JSON 解析：将模型输出解析为结构化对象。
            root = objectMapper.readValue(content, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception exception) {
            // 异常处理：解析失败返回标准失败码，交由上游执行降级。
            result.setSuccess(false);
            result.setFailureReason("LLM_PARSE_ERROR");
            result.addViolation("json_parse_error");
            return result;
        }
        // 字段提取：读取 summary 与 summaryVersion 字段并标准化。
        result.setSummary(extractText(root.get("summary")));
        result.setSummaryVersion(extractText(root.get("summaryVersion")));
        result.setSuccess(true);
        // 规则校验：应用响应校验策略，确保输出可安全进入主链路。
        validationPolicy.validate(result);
        return result;
    }

    /**
     * 提取文本字段。
     */
    private String extractText(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }
}

