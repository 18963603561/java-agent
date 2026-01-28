package com.example.agent.security;

import com.example.agent.observability.MetricsPublisher;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 脱敏服务，执行最小规则集检测与替换。
 */
@Service
public class RedactionService {

    private static final Pattern SECRET_PATTERN = Pattern.compile(
            "(?i)(token|secret|password|api[_-]?key|access[_-]?key|ak|sk)\\s*[:=]\\s*\\S{6,}");
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern PHONE_PATTERN = Pattern.compile("(?<!\\d)1\\d{10}(?!\\d)");
    private static final Pattern ID_PATTERN = Pattern.compile("(?<!\\d)\\d{17}[0-9Xx](?!\\d)");

    private static final String SECRET_MASK = "【已脱敏密钥】";
    private static final String EMAIL_MASK = "【已脱敏邮箱】";
    private static final String PHONE_MASK = "【已脱敏手机号】";
    private static final String ID_MASK = "【已脱敏身份证】";

    private final RedactionProperties properties;
    private final MetricsPublisher metricsPublisher;

    public RedactionService(RedactionProperties properties, MetricsPublisher metricsPublisher) {
        this.properties = properties;
        this.metricsPublisher = metricsPublisher;
    }

    /**
     * 当前脱敏是否启用。
     *
     * @return 是否启用
     */
    public boolean isEnabled() {
        return properties == null || properties.isEnabled();
    }

    /**
     * 是否启用密钥拒写。
     *
     * @return 是否拒写
     */
    public boolean isRejectOnSecrets() {
        return properties != null && properties.isRejectOnSecrets();
    }

    /**
     * 是否启用个人信息脱敏。
     *
     * @return 是否脱敏
     */
    public boolean isRedactOnPii() {
        return properties == null || properties.isRedactOnPii();
    }

    /**
     * 执行脱敏与拒写判断。
     *
     * @param text 原始文本
     * @param stage 处理阶段
     * @param fieldKey 字段标识
     * @return 处理结果
     */
    public RedactionResult apply(String text, RedactionStage stage, String fieldKey) {
        RedactionResult result = new RedactionResult();
        if (!StringUtils.hasText(text)) {
            result.setRedactedText(text);
            return result;
        }
        if (properties != null && !properties.isEnabled()) {
            result.setRedactedText(text);
            return result;
        }
        if (isIgnored(fieldKey)) {
            result.setRedactedText(text);
            return result;
        }

        String scanText = limitScan(text);
        String tail = scanText.length() < text.length() ? text.substring(scanText.length()) : "";
        String processed = scanText;

        int secretHits = countMatches(SECRET_PATTERN, scanText);
        if (secretHits > 0) {
            result.addRuleHit("secret", secretHits);
            incrementMetric("redaction_rules_hit_total", secretHits, "ruleType", "secret");
            if (stage == RedactionStage.WRITE && properties != null && properties.isRejectOnSecrets()) {
                result.setRejected(true);
                result.setRedactedText(text);
                incrementMetric("redaction_rejected_total", 1, "stage", "write");
                return result;
            }
            processed = SECRET_PATTERN.matcher(processed).replaceAll(SECRET_MASK);
            result.setRedactedCount(result.getRedactedCount() + secretHits);
        }

        if (properties == null || properties.isRedactOnPii()) {
            int emailHits = countMatches(EMAIL_PATTERN, processed);
            if (emailHits > 0) {
                result.addRuleHit("email", emailHits);
                incrementMetric("redaction_rules_hit_total", emailHits, "ruleType", "email");
                processed = EMAIL_PATTERN.matcher(processed).replaceAll(EMAIL_MASK);
                result.setRedactedCount(result.getRedactedCount() + emailHits);
            }
            int phoneHits = countMatches(PHONE_PATTERN, processed);
            if (phoneHits > 0) {
                result.addRuleHit("phone", phoneHits);
                incrementMetric("redaction_rules_hit_total", phoneHits, "ruleType", "phone");
                processed = PHONE_PATTERN.matcher(processed).replaceAll(PHONE_MASK);
                result.setRedactedCount(result.getRedactedCount() + phoneHits);
            }
            int idHits = countMatches(ID_PATTERN, processed);
            if (idHits > 0) {
                result.addRuleHit("id", idHits);
                incrementMetric("redaction_rules_hit_total", idHits, "ruleType", "id");
                processed = ID_PATTERN.matcher(processed).replaceAll(ID_MASK);
                result.setRedactedCount(result.getRedactedCount() + idHits);
            }
        }

        if (result.getRedactedCount() > 0) {
            String stageTag = stage != null ? stage.name().toLowerCase(Locale.ROOT) : "unknown";
            incrementMetric("redaction_applied_total", result.getRedactedCount(), "stage", stageTag);
        }

        result.setRedactedText(processed + tail);
        return result;
    }

    private void incrementMetric(String name, int count, String tagKey, String tagValue) {
        if (metricsPublisher == null) {
            return;
        }
        metricsPublisher.incrementWithTags(name, count, tagKey, tagValue);
    }

    private boolean isIgnored(String fieldKey) {
        if (!StringUtils.hasText(fieldKey) || properties == null || properties.getIgnoreKeys() == null) {
            return false;
        }
        for (String key : properties.getIgnoreKeys()) {
            if (key != null && key.equalsIgnoreCase(fieldKey)) {
                return true;
            }
        }
        return false;
    }

    private String limitScan(String text) {
        int max = properties != null ? properties.getMaxScanChars() : 0;
        if (max <= 0 || text.length() <= max) {
            return text;
        }
        return text.substring(0, max);
    }

    private int countMatches(Pattern pattern, String text) {
        if (pattern == null || text == null) {
            return 0;
        }
        int count = 0;
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            count++;
        }
        return count;
    }
}
