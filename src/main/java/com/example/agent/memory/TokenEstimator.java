package com.example.agent.memory;

import org.springframework.stereotype.Component;

/**
 * Token 估算器，用于在缺少真实 token 统计时进行近似估算。
 */
@Component
public class TokenEstimator {

    /**
     * 估算文本 token 数量。
     *
     * @param text 文本内容
     * @return 估算 token 数量
     */
    public int estimateTokens(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        int length = text.length();
        int estimate = (int) Math.ceil(length / 4.0);
        return Math.max(1, estimate);
    }
}
