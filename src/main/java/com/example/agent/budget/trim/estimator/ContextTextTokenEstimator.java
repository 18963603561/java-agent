package com.example.agent.budget.trim.estimator;

import com.example.agent.capabilities.memory.policy.TokenEstimator;
import java.util.List;

/**
 * 文本令牌估算器，负责基础字符串与字符估算能力。
 */
class ContextTextTokenEstimator {

    private final TokenEstimator tokenEstimator;

    ContextTextTokenEstimator(TokenEstimator tokenEstimator) {
        this.tokenEstimator = tokenEstimator;
    }

    /**
     * 估算文本令牌。
     */
    int estimateTokens(String text) {
        return tokenEstimator != null ? tokenEstimator.estimateTokens(text) : 0;
    }

    /**
     * 估算文本列表令牌。
     */
    int estimateTokens(List<String> values) {
        if (values == null) {
            return 0;
        }
        int total = 0;
        for (String value : values) {
            total += estimateTokens(value);
        }
        return total;
    }

    /**
     * 按字符数估算令牌。
     */
    int estimateTokensByChars(int chars) {
        if (chars <= 0) {
            return 0;
        }
        int estimate = (int) Math.ceil(chars / 4.0);
        return Math.max(1, estimate);
    }

    /**
     * 安全长度计算。
     */
    int safeLength(String value) {
        return value == null ? 0 : value.length();
    }
}
