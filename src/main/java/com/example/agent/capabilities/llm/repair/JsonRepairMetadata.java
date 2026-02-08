package com.example.agent.capabilities.llm.repair;

import com.example.agent.capabilities.llm.client.events.LlmInvocationMetadata;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * JSON 修复调用元数据。
 *
 * <p>用途：统一承载 JSON 修复链路的观测元数据。
 * <p>输入：场景标识与时间戳。
 * <p>输出：供模型调用服务使用的元数据 Map。
 * <p>边界：字段为空时自动补默认值，避免下游空值分支。
 */
public class JsonRepairMetadata implements LlmInvocationMetadata {

    private final String scene;
    private final Instant timestamp;

    public JsonRepairMetadata(String scene, Instant timestamp) {
        this.scene = scene == null || scene.isBlank() ? "unknown" : scene.trim();
        this.timestamp = timestamp == null ? Instant.now() : timestamp;
    }

    /**
     * 创建修复元数据。
     *
     * @param scene 场景标识
     * @return 修复元数据
     */
    public static JsonRepairMetadata of(String scene) {
        return new JsonRepairMetadata(scene, Instant.now());
    }

    @Override
    public Map<String, Object> toMetadataMap() {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("scene", scene);
        metadata.put("ts", timestamp.toString());
        metadata.put("promptScene", scene);
        return metadata;
    }

    public String getScene() {
        return scene;
    }

    public Instant getTimestamp() {
        return timestamp;
    }
}
