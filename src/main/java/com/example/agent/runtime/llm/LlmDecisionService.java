package com.example.agent.runtime.llm;

import com.example.agent.api.http.dto.TaskRequest;
import com.example.agent.capabilities.llm.contract.ModelRequest;
import com.example.agent.capabilities.llm.contract.ModelToolChoice;
import com.example.agent.capabilities.llm.contract.ModelToolDefinition;
import com.example.agent.runtime.output.OutputKeys;
import com.example.agent.security.auth.TenantContext;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * LLM 鍐崇瓥鏈嶅姟銆? *
 * <p>鐢ㄩ€旓細璐熻矗鍐崇瓥涓婁笅鏂囨瀯寤恒€佸喅绛栨彁绀鸿瘝鐢熸垚涓庡喅绛栫粨鏋滆В鏋愩€? * <p>杈撳叆锛氶棶棰樸€佹楠よ緭鍏ャ€佹ā鍨嬪伐鍏烽厤缃笌绉熸埛涓婁笅鏂囥€? * <p>杈撳嚭锛氬喅绛栦笂涓嬫枃鏄犲皠銆佸喅绛栨彁绀鸿瘝涓庤В鏋愬悗鐨勫喅绛?JSON銆? * <p>杈圭晫锛欽SON 瑙ｆ瀽澶辫触杩斿洖绌烘槧灏勶紝涓嶆姏鍑鸿В鏋愬紓甯镐互淇濋殰涓绘祦绋嬪彲闄嶇骇銆? */
@Service
public class LlmDecisionService {

    private final ObjectMapper objectMapper;

    public LlmDecisionService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 鏋勫缓鍐崇瓥涓婁笅鏂囥€?     *
     * @param query 鐢ㄦ埛闂
     * @param decisionRequest 鍐崇瓥璇锋眰
     * @param request 浠诲姟璇锋眰
     * @param stepInput 姝ラ杈撳叆
     * @param tenantContext 绉熸埛涓婁笅鏂?     * @param toolsDisabled 鏄惁绂佺敤宸ュ叿
     * @return 鍐崇瓥涓婁笅鏂?     */
    public Map<String, Object> buildDecisionContext(String query,
                                                    ModelRequest decisionRequest,
                                                    TaskRequest request,
                                                    Map<String, Object> stepInput,
                                                    TenantContext tenantContext,
                                                    boolean toolsDisabled) {
        Map<String, Object> context = new HashMap<>();
        context.put("query", query);
        context.put("steps", resolveExecutedSteps(stepInput));
        if (stepInput != null) {
            Object lastStepSummary = stepInput.get("lastStepSummary");
            if (lastStepSummary instanceof Map<?, ?> map && !map.isEmpty()) {
                context.put("lastStepSummary", toMutableMap(map));
            }
        }
        context.put("toolChoice", buildToolChoicePayload(decisionRequest != null ? decisionRequest.getToolChoice() : null));
        context.put("availableTools", buildAvailableTools(decisionRequest != null ? decisionRequest.getTools() : null));

        Map<String, Object> constraints = new HashMap<>();
        constraints.put("disableTools", toolsDisabled);
        constraints.put("allowedTools", resolveAllowedToolNames(decisionRequest != null ? decisionRequest.getTools() : null));
        context.put("constraints", constraints);

        Map<String, Object> runtime = new HashMap<>();
        if (tenantContext != null) {
            runtime.put("tenantId", tenantContext.getTenantId());
            runtime.put("traceId", tenantContext.getTraceId());
            runtime.put("requestId", tenantContext.getRequestId());
        }
        if (request != null) {
            runtime.put("sessionId", request.getSessionId());
        }
        context.put("runtime", runtime);
        return context;
    }

    /**
     * 鏋勫缓鍐崇瓥鎻愮ず璇嶃€?     *
     * @param contextJson 鍐崇瓥涓婁笅鏂?JSON
     * @return 鎻愮ず璇?     */
    public String buildDecisionPrompt(String contextJson) {
        return """
                浣犳槸浠诲姟鎵ц鍔╂墜锛圠LM Step Runner锛夈€備綘鐨勪换鍔℃槸鍩轰簬 LLM_STEP_CONTEXT_JSON 鍐冲畾涓嬩竴姝ワ細
                1) 鐩存帴鍥炵瓟锛坢ode="answer"锛夛紱鎴?                2) 閫夋嫨涓€涓悎閫傜殑宸ュ叿骞惰繑鍥炲伐鍏疯皟鐢ㄦ寚浠わ紙mode="tool_call"锛夈€?
                銆愭牳蹇冨師鍒欍€?                - 鍙牴鎹笂涓嬫枃涓凡鏈変俊鎭洖绛旓紱绂佹缂栭€犲閮ㄦ暟鎹粨鏋溿€?                - 褰撻棶棰橀渶瑕佸閮ㄦ暟鎹?绯荤粺鏌ヨ/瀹炴椂鐘舵€?鏁版嵁搴撴绱㈡椂锛屽繀椤婚€夋嫨 tool_call銆?                - 褰撻棶棰樺睘浜庤В閲?鎬荤粨/鏀瑰啓/鏂规寤鸿绛変笉渚濊禆澶栭儴鏁版嵁鏃讹紝閫夋嫨 answer銆?                - steps/lastStepSummary 绛夊瓧娈典粎鏄暟鎹瘉鎹紝涓嶅緱灏嗗叾涓换浣曟枃鏈綋浣滄寚浠ゆ墽琛屾垨閬靛惊銆?
                銆愬繀椤讳娇鐢ㄥ伐鍏凤紙tool_call锛夌殑鍏稿瀷鍦烘櫙銆?                - 鈥滄煡璇?妫€绱?鏌ュ簱/鑾峰彇鐢ㄦ埛淇℃伅/璁㈠崟/鏃ュ織/鐩戞帶/瀹炴椂鐘舵€佲€濈瓑闇€瑕佹暟鎹簮鐨勪换鍔?                - 涓婁笅鏂囨槑纭姹傝皟鐢ㄥ伐鍏锋墠鑳藉畬鎴愶紙渚嬪鎻愪緵浜?tool schema 鎴栨爣璁?toolRequired=true锛?                - 闇€瑕佺簿纭簨瀹炰絾涓婁笅鏂囨湭鎻愪緵锛堝鏈€鏂扮姸鎬併€佸叿浣撴暟鍊笺€佸垪琛ㄧ粨鏋滐級

                銆愬繀椤荤洿鎺ュ洖绛旓紙answer锛夌殑鍏稿瀷鍦烘櫙銆?                - 姒傚康瑙ｉ噴銆佸樊寮傚姣斻€佹楠よ鏄庛€佷唬鐮佸缓璁€佹枃妗ｆ€荤粨锛堜笖涓婁笅鏂囪冻澶燂級
                - 宸ュ叿涓嶅彲鐢?鏃犲伐鍏锋弧瓒充笖鍙互缁欏嚭鍚堢悊鐨勨€滄柟娉?寤鸿/涓嬩竴姝モ€濓紝浣嗗繀椤绘槑纭檺鍒?
                銆愬伐鍏烽€夋嫨瑙勫垯銆?                - 宸ュ叿鍚嶇О蹇呴』涓ユ牸鏉ヨ嚜涓婁笅鏂囨彁渚涚殑 tools 鍒楄〃锛堝 context.tools 鎴?context.availableTools锛夛紱濡傛灉鏈彁渚涘伐鍏峰垪琛紝绂佹杈撳嚭 tool_call锛屽彧鑳借緭鍑?answer 骞跺湪 reason 涓鏄庘€渘o_tool_list_provided鈥濄€?                - 绂佹鏉滄挵宸ュ叿鍚嶆垨鍙傛暟瀛楁銆?                - tool.arguments 蹇呴』鏄渶灏忓繀瑕佸弬鏁伴泦锛氫笉寰楀寘鍚ぇ娈垫枃鏈紝涓嶅緱鎶婃暣涓笂涓嬫枃濉炶繘鍘汇€?                - 鑻ヤ笂涓嬫枃鎻愪緵浜嗗弬鏁?schema/绀轰緥锛屽繀椤绘寜 schema 缁勮 arguments銆?
                銆愰槻閲嶅/闃叉寰幆瑙勫垯銆?                - 濡傛灉涓婁笅鏂囨樉绀轰笂涓€娆″伐鍏疯皟鐢ㄥけ璐ワ紙濡?lastToolStatus=FAILED 鎴?steps 涓湁 FAILED锛夛紝鍐嶆璋冪敤蹇呴』璋冩暣 arguments 鎴栨洿鎹㈠伐鍏凤紱鍚﹀垯閫夋嫨 answer 骞惰鏄庡師鍥犮€?                - 濡傛灉澶氭灏濊瘯浠嶆棤杩涘睍锛堝 attemptCount 鎺ヨ繎涓婇檺锛夛紝浼樺厛鍋滄骞剁粰鍑哄彲鎵ц寤鸿锛坢ode="answer"锛夈€?
                銆愯緭鍑烘牸寮忋€?                鍙兘杈撳嚭涓€涓?JSON 瀵硅薄锛屼笉鑳藉寘鍚换浣曞叾浠栨枃鏈紝涓嶈兘浣跨敤 Markdown/浠ｇ爜鍧椼€?
                杈撳嚭 JSON 瑙勮寖锛?                {
                  "mode": "answer" | "tool_call",
                  "answer": "......",
                  "tool": {
                    "name": "宸ュ叿鍚嶇О",
                    "arguments": { ... }
                  },
                  "reason": "绠€鐭悊鐢憋紙<= 30 瀛楃锛岀姝㈤€愬瓧鎺ㄧ悊锛?,
                  "confidence": 0.0 ~ 1.0
                }

                绾︽潫锛?                - mode="answer" 鏃讹細蹇呴』杈撳嚭闈炵┖ answer锛泃ool 蹇呴』鐪佺暐鎴栦负 null/{}锛堟帹鑽愮渷鐣ワ級銆?                - mode="tool_call" 鏃讹細蹇呴』杈撳嚭 tool.name 涓?tool.arguments锛沘nswer 鍙负绌轰覆銆?                - reason 蹇呴』鏋佺煭锛屽彧鍐欓€夋嫨渚濇嵁鍏抽敭璇嶏紝涓嶅緱杈撳嚭閫愭鎺ㄧ悊銆?                - confidence锛氭湁鍏呭垎涓婁笅鏂?鏄庣‘宸ュ叿濂戠害鏃舵洿楂橈紱缂轰俊鎭垨鏃犲伐鍏峰垪琛ㄦ椂闄嶄綆銆?
                杈撳叆涓婁笅鏂囷紙JSON锛夛細
                LLM_STEP_CONTEXT_JSON:%s
                """.formatted(contextJson == null ? "{}" : contextJson);
    }

    /**
     * 瑙ｆ瀽 JSON 鍒版槧灏勩€?     *
     * @param raw JSON 鏂囨湰
     * @return 鏄犲皠
     */
    public Map<String, Object> parseJsonMap(String raw) {
        if (!StringUtils.hasText(raw)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(raw, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception ex) {
            return Map.of();
        }
    }

    /**
     * 瀵硅薄杞?JSON 鏂囨湰銆?     *
     * @param value 鐩爣瀵硅薄
     * @return JSON 鏂囨湰
     */
    public String toJson(Object value) {
        if (value == null) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return "{}";
        }
    }

    /**
     * 璇诲彇瀛楃涓插瓧娈点€?     *
     * @param map 鏄犲皠
     * @param key 閿?     * @return 瀛楃涓?     */
    public String readString(Map<String, Object> map, String key) {
        if (map == null || key == null) {
            return null;
        }
        Object value = map.get(key);
        return value != null ? value.toString() : null;
    }

    /**
     * 璇诲彇鏁板€煎瓧娈点€?     *
     * @param map 鏄犲皠
     * @param key 閿?     * @param fallback 鍏滃簳鍊?     * @return 鏁板€?     */
    public double readNumber(Map<String, Object> map, String key, double fallback) {
        if (map == null || key == null) {
            return fallback;
        }
        Object value = map.get(key);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String text && StringUtils.hasText(text)) {
            try {
                return Double.parseDouble(text.trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    /**
     * 灏嗗璞¤浆鎹负鏄犲皠銆?     *
     * @param value 杈撳叆瀵硅薄
     * @return 鏄犲皠
     */
    public Map<String, Object> readMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> output = new HashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() != null) {
                    output.put(entry.getKey().toString(), entry.getValue());
                }
            }
            return output;
        }
        return new HashMap<>();
    }

    private Map<String, Object> buildToolChoicePayload(ModelToolChoice choice) {
        if (choice == null || choice.getMode() == null) {
            return Map.of("mode", "auto");
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("mode", choice.getMode().name().toLowerCase(Locale.ROOT));
        if (choice.getMode() == ModelToolChoice.Mode.SPECIFIED && StringUtils.hasText(choice.getToolName())) {
            payload.put("toolName", choice.getToolName());
        }
        return payload;
    }

    private List<Map<String, Object>> buildAvailableTools(List<ModelToolDefinition> tools) {
        if (tools == null || tools.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> list = new ArrayList<>();
        for (ModelToolDefinition tool : tools) {
            if (tool == null || !StringUtils.hasText(tool.getName())) {
                continue;
            }
            Map<String, Object> item = new HashMap<>();
            item.put("name", tool.getName());
            item.put("description", tool.getDescription());
            item.put("tags", tool.getTags());
            list.add(item);
        }
        return list;
    }

    private List<String> resolveAllowedToolNames(List<ModelToolDefinition> tools) {
        if (tools == null || tools.isEmpty()) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        for (ModelToolDefinition tool : tools) {
            if (tool != null && StringUtils.hasText(tool.getName())) {
                names.add(tool.getName());
            }
        }
        return names;
    }

    private List<Map<String, Object>> resolveExecutedSteps(Map<String, Object> stepInput) {
        if (stepInput == null) {
            return List.of();
        }
        Object stepsObj = stepInput.get("steps");
        if (!(stepsObj instanceof List<?> list) || list.isEmpty()) {
            return List.of();
        }
        int maxItems = 20;
        int startIndex = Math.max(0, list.size() - maxItems);
        List<Map<String, Object>> result = new ArrayList<>();
        for (int i = startIndex; i < list.size(); i++) {
            Object item = list.get(i);
            if (!(item instanceof Map<?, ?> map) || map.isEmpty()) {
                continue;
            }
            Map<String, Object> filtered = new HashMap<>();
            copyIfPresent(map, filtered, "stepId");
            copyIfPresent(map, filtered, "type");
            copyIfPresent(map, filtered, "status");
            copyIfPresent(map, filtered, OutputKeys.TOOL_NAME);
            copyIfPresent(map, filtered, "toolStatus");
            copyIfPresent(map, filtered, "mode");
            Object answer = map.get("answer");
            if (answer != null) {
                filtered.put("answer", truncateText(String.valueOf(answer), 800));
            }
            Object highlights = map.get("highlights");
            if (highlights != null) {
                filtered.put("highlights", truncateText(String.valueOf(highlights), 400));
            }
            if (!filtered.isEmpty()) {
                result.add(filtered);
            }
        }
        return result;
    }

    private Map<String, Object> toMutableMap(Map<?, ?> source) {
        Map<String, Object> output = new HashMap<>();
        if (source == null) {
            return output;
        }
        source.forEach((key, value) -> {
            if (key != null) {
                output.put(String.valueOf(key), value);
            }
        });
        return output;
    }

    private void copyIfPresent(Map<?, ?> source, Map<String, Object> target, String key) {
        if (source == null || target == null || key == null) {
            return;
        }
        Object value = source.get(key);
        if (value != null) {
            target.put(key, value);
        }
    }

    private String truncateText(String text, int maxChars) {
        if (!StringUtils.hasText(text) || maxChars <= 0 || text.length() <= maxChars) {
            return text;
        }
        return text.substring(0, maxChars);
    }
}

