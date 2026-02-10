package com.example.agent.reasoning;

import com.example.agent.reasoning.cot.CotAnswerSanitizer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CotAnswerSanitizerTest {

    @Test
    void sanitizeShouldRemoveReasoningTrace() {
        CotAnswerSanitizer sanitizer = new CotAnswerSanitizer();
        String raw = "analysis: step by step\n最终答案：北京";

        String sanitized = sanitizer.sanitize(raw, 100);
        assertEquals("北京", sanitized);
    }

    @Test
    void sanitizeShouldTruncateByMaxChars() {
        CotAnswerSanitizer sanitizer = new CotAnswerSanitizer();
        String raw = "final answer: " + "a".repeat(50);

        String sanitized = sanitizer.sanitize(raw, 20);
        assertEquals(20, sanitized.length());
        assertTrue(sanitized.startsWith("a"));
    }
}

