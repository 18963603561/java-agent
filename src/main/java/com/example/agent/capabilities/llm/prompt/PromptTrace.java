package com.example.agent.capabilities.llm.prompt;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;

/**
 * 提示词调用追踪信息。
 */
public class PromptTrace {

    private String promptScene;
    private String promptId;
    private int promptChars;
    private int promptTokensEstimate;
    private Boolean parseSuccess;
    private String parseErrorType;
    private Boolean repairAttempted;
    private Boolean repairSuccess;

    public String getPromptScene() {
        return promptScene;
    }

    public void setPromptScene(String promptScene) {
        this.promptScene = promptScene;
    }

    public String getPromptId() {
        return promptId;
    }

    public void setPromptId(String promptId) {
        this.promptId = promptId;
    }

    public int getPromptChars() {
        return promptChars;
    }

    public void setPromptChars(int promptChars) {
        this.promptChars = promptChars;
    }

    public int getPromptTokensEstimate() {
        return promptTokensEstimate;
    }

    public void setPromptTokensEstimate(int promptTokensEstimate) {
        this.promptTokensEstimate = promptTokensEstimate;
    }

    public Boolean getParseSuccess() {
        return parseSuccess;
    }

    public void setParseSuccess(Boolean parseSuccess) {
        this.parseSuccess = parseSuccess;
    }

    public String getParseErrorType() {
        return parseErrorType;
    }

    public void setParseErrorType(String parseErrorType) {
        this.parseErrorType = parseErrorType;
    }

    public Boolean getRepairAttempted() {
        return repairAttempted;
    }

    public void setRepairAttempted(Boolean repairAttempted) {
        this.repairAttempted = repairAttempted;
    }

    public Boolean getRepairSuccess() {
        return repairSuccess;
    }

    public void setRepairSuccess(Boolean repairSuccess) {
        this.repairSuccess = repairSuccess;
    }

    public Map<String, Object> toPayload() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("promptScene", promptScene);
        payload.put("promptId", promptId);
        payload.put("promptChars", promptChars);
        payload.put("promptTokensEstimate", promptTokensEstimate);
        if (parseSuccess != null) {
            payload.put("parseSuccess", parseSuccess);
        }
        if (parseErrorType != null) {
            payload.put("parseErrorType", parseErrorType);
        }
        if (repairAttempted != null) {
            payload.put("repairAttempted", repairAttempted);
        }
        if (repairSuccess != null) {
            payload.put("repairSuccess", repairSuccess);
        }
        return payload;
    }

    public static PromptTrace fromPrompt(String promptScene, String prompt) {
        PromptTrace trace = new PromptTrace();
        trace.setPromptScene(promptScene);
        String safePrompt = prompt == null ? "" : prompt;
        trace.setPromptChars(safePrompt.length());
        trace.setPromptTokensEstimate(estimateTokens(safePrompt));
        trace.setPromptId(hashPrompt(safePrompt));
        return trace;
    }

    public static PromptTrace fromMetadata(Map<String, Object> metadata) {
        if (metadata == null) {
            return null;
        }
        Object value = metadata.get("promptTrace");
        if (value instanceof PromptTrace trace) {
            return trace;
        }
        return null;
    }

    public static String hashPrompt(String prompt) {
        if (prompt == null) {
            return "";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(prompt.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte b : bytes) {
                String hex = Integer.toHexString(b & 0xff);
                if (hex.length() == 1) {
                    builder.append('0');
                }
                builder.append(hex);
            }
            String full = builder.toString();
            return full.length() > 8 ? full.substring(0, 8) : full;
        } catch (Exception ex) {
            return "";
        }
    }

    private static int estimateTokens(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return 0;
        }
        int chars = prompt.length();
        return Math.max(1, (int) Math.ceil(chars / 4.0));
    }
}