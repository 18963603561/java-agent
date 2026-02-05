package com.example.agent.security;

import com.example.agent.streaming.observability.MetricsPublisher;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import com.example.agent.security.redaction.RedactionProperties;
import com.example.agent.security.redaction.RedactionResult;
import com.example.agent.security.redaction.RedactionService;
import com.example.agent.security.redaction.RedactionStage;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RedactionServiceTest {

    @Test
    void rejectSecretWhenEnabled() {
        RedactionProperties properties = new RedactionProperties();
        properties.setEnabled(true);
        properties.setRejectOnSecrets(true);
        RedactionService service = new RedactionService(properties, new MetricsPublisher(new SimpleMeterRegistry()));

        RedactionResult result = service.apply("token=abcd123456", RedactionStage.WRITE, "payload");

        assertTrue(result.isRejected());
    }

    @Test
    void redactPiiWhenEnabled() {
        RedactionProperties properties = new RedactionProperties();
        properties.setEnabled(true);
        properties.setRedactOnPii(true);
        RedactionService service = new RedactionService(properties, new MetricsPublisher(new SimpleMeterRegistry()));

        RedactionResult result = service.apply("联系 test@example.com 电话 13800138000",
                RedactionStage.WRITE, "payload");

        assertFalse(result.isRejected());
        assertTrue(result.getRedactedCount() > 0);
        assertTrue(result.getRedactedText().contains("【已脱敏邮箱】"));
        assertTrue(result.getRedactedText().contains("【已脱敏手机号】"));
    }

    @Test
    void noProcessingWhenDisabled() {
        RedactionProperties properties = new RedactionProperties();
        properties.setEnabled(false);
        RedactionService service = new RedactionService(properties, new MetricsPublisher(new SimpleMeterRegistry()));

        RedactionResult result = service.apply("token=abcd123456",
                RedactionStage.WRITE, "payload");

        assertFalse(result.isRejected());
        assertTrue(result.getRedactedCount() == 0);
    }
}
