package com.example.agent.governance.policy.provider.opa;

import com.example.agent.governance.policy.config.PolicyProviderProperties;
import com.example.agent.governance.policy.domain.PolicyEvaluationCommand;
import com.example.agent.security.auth.TenantContext;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * OPA 客户端。
 */
@Component
public class OpaClient {

    private static final Logger log = LoggerFactory.getLogger(OpaClient.class);

    private final RestTemplate restTemplate;
    private final PolicyProviderProperties properties;

    public OpaClient(ObjectProvider<RestTemplateBuilder> builderProvider,
                     PolicyProviderProperties properties) {
        this.properties = properties;
        int timeout = properties != null ? properties.getOpa().getTimeoutMs() : 3000;
        RestTemplateBuilder builder = builderProvider != null
                ? builderProvider.getIfAvailable(RestTemplateBuilder::new)
                : new RestTemplateBuilder();
        this.restTemplate = builder
                .setConnectTimeout(Duration.ofMillis(timeout))
                .setReadTimeout(Duration.ofMillis(timeout))
                .build();
    }

    /**
     * 调用 OPA 评估。
     *
     * @param command 策略命令
     * @param tenantContext 租户上下文
     * @return OPA 结果映射
     */
    public Map<String, Object> evaluate(PolicyEvaluationCommand command, TenantContext tenantContext) {
        String endpoint = resolveEndpoint();
        Map<String, Object> payload = buildPayload(command, tenantContext);
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(endpoint, request, Map.class);
            return response == null ? Map.of() : response;
        } catch (RestClientException ex) {
            log.error("OPA调用失败, endpoint={}, tenantId={}, action={}, resource={}",
                    endpoint,
                    tenantContext != null ? tenantContext.getTenantId() : null,
                    command != null ? command.getAction() : null,
                    command != null ? command.getResource() : null,
                    ex);
            throw ex;
        }
    }

    private String resolveEndpoint() {
        if (properties == null || properties.getOpa() == null) {
            return "http://localhost:8181/v1/data/agent/policy/evaluate";
        }
        String url = properties.getOpa().getUrl();
        String path = properties.getOpa().getPath();
        String base = url == null ? "http://localhost:8181" : url.trim();
        String suffix = path == null ? "/v1/data/agent/policy/evaluate" : path.trim();
        if (base.endsWith("/") && suffix.startsWith("/")) {
            return base.substring(0, base.length() - 1) + suffix;
        }
        if (!base.endsWith("/") && !suffix.startsWith("/")) {
            return base + "/" + suffix;
        }
        return base + suffix;
    }

    private Map<String, Object> buildPayload(PolicyEvaluationCommand command, TenantContext tenantContext) {
        Map<String, Object> input = new HashMap<>();
        input.put("tenantId", tenantContext != null ? tenantContext.getTenantId() : null);
        input.put("userId", tenantContext != null ? tenantContext.getUserId() : null);
        input.put("action", command != null ? command.getAction() : null);
        input.put("resource", command != null ? command.getResource() : null);
        input.put("policyId", command != null ? command.getPolicyId() : null);
        input.put("context", command != null ? command.getInput() : null);

        Map<String, Object> payload = new HashMap<>();
        payload.put("input", input);
        return payload;
    }

    /**
     * 解析 OPA 结果。
     *
     * @param response OPA 响应
     * @return 归一化结果
     */
    @SuppressWarnings("unchecked")
    public OpaDecision parseDecision(Map<String, Object> response) {
        if (response == null || response.isEmpty()) {
            return new OpaDecision(false, "opa_empty_response", List.of("opa_empty"));
        }
        Object resultObj = response.get("result");
        if (!(resultObj instanceof Map<?, ?> resultMapRaw)) {
            return new OpaDecision(false, "opa_missing_result", List.of("opa_missing_result"));
        }
        Map<String, Object> resultMap = new HashMap<>();
        resultMapRaw.forEach((k, v) -> resultMap.put(String.valueOf(k), v));

        boolean allow = toBoolean(resultMap.get("allow"));
        String reason = toText(resultMap.get("reason"));
        Object matchedObj = resultMap.get("matchedRules");
        List<String> matchedRules;
        if (matchedObj instanceof List<?> list) {
            matchedRules = list.stream().map(String::valueOf).toList();
        } else {
            matchedRules = List.of();
        }
        return new OpaDecision(allow, reason, matchedRules);
    }

    private boolean toBoolean(Object value) {
        if (value instanceof Boolean boolValue) {
            return boolValue;
        }
        if (value instanceof String textValue) {
            return "true".equalsIgnoreCase(textValue.trim());
        }
        return false;
    }

    private String toText(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    /**
     * OPA 决策结果。
     */
    public record OpaDecision(boolean allow, String reason, List<String> matchedRules) {
    }
}
