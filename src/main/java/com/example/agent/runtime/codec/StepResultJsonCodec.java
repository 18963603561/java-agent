package com.example.agent.runtime.codec;

import com.example.agent.runtime.model.StepResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 步骤结果 JSON 编解码器。
 *
 * <p>用途：统一运行时步骤输入/输出在持久化边界的序列化规则，避免各仓储重复实现。
 */
@Component
public class StepResultJsonCodec {

    /**
     * 日志记录器。
     */
    private static final Logger log = LoggerFactory.getLogger(StepResultJsonCodec.class);

    private final ObjectMapper objectMapper;

    public StepResultJsonCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 将对象序列化为 JSON 文本。
     *
     * @param payload 待序列化对象
     * @return JSON 文本
     */
    public String write(Object payload) {
        if (payload == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            log.warn("步骤结果JSON序列化失败, payloadType={}", payload.getClass().getName(), ex);
            return null;
        }
    }

    /**
     * 反序列化步骤输入映射。
     *
     * @param json JSON 文本
     * @return 输入映射
     */
    public Map<String, Object> readMap(String json) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {
            });
        } catch (JsonProcessingException ex) {
            log.warn("步骤输入JSON反序列化失败, jsonLength={}", safeLength(json), ex);
            return null;
        }
    }

    /**
     * 反序列化步骤结果对象。
     *
     * @param json JSON 文本
     * @return 步骤结果
     */
    public StepResult readStepResult(String json) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, StepResult.class);
        } catch (JsonProcessingException ex) {
            log.warn("步骤结果JSON反序列化失败, targetType={}, jsonLength={}",
                    StepResult.class.getSimpleName(), safeLength(json), ex);
            return null;
        }
    }

    /**
     * 计算字符串长度。
     *
     * @param text 文本
     * @return 长度，空值返回 0
     */
    private int safeLength(String text) {
        return text == null ? 0 : text.length();
    }
}
