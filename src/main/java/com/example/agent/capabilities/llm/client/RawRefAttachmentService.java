package com.example.agent.capabilities.llm.client;

import com.example.agent.capabilities.llm.contract.ModelResponse;
import com.example.agent.capabilities.llm.contract.ModelScene;
import com.example.agent.runtime.raw.ref.RawRef;
import com.example.agent.runtime.raw.store.RawResultStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * 原始输出引用挂载服务。
 *
 * <p>用途：统一管理模型输出 rawRef 挂载策略，保证挂载失败可观测且行为一致。
 * <p>输入：模型响应、调用场景与阶段。
 * <p>输出：在响应对象上回写 rawRef。
 */
@Component
public class RawRefAttachmentService {

    private static final Logger log = LoggerFactory.getLogger(RawRefAttachmentService.class);

    /**
     * rawRef 挂载的最大内容长度，超过阈值后仅保留日志，不再执行存储。
     */
    private static final int RAW_REF_MAX_CONTENT_CHARS = 1_000_000;

    private final RawResultStore rawResultStore;

    public RawRefAttachmentService(ObjectProvider<RawResultStore> rawResultStoreProvider) {
        this.rawResultStore = rawResultStoreProvider != null ? rawResultStoreProvider.getIfAvailable() : null;
        if (this.rawResultStore == null) {
            log.warn("未检测到 RawResultStore 实现，模型输出将跳过 rawRef 挂载");
        }
    }

    /**
     * 为模型输出挂载原始结果引用。
     *
     * @param response 模型响应
     * @param scene 调用场景
     * @param phase 阶段标识
     */
    public void attach(ModelResponse response, ModelScene scene, String phase) {
        if (response == null || rawResultStore == null) {
            return;
        }
        String content = response.getContent();
        if (content == null || content.isBlank()) {
            log.debug("模型输出 rawRef 跳过挂载, reason=empty_content, scene={}, phase={}, modelId={}",
                    scene,
                    phase,
                    response.getModelId());
            return;
        }
        if (content.length() > RAW_REF_MAX_CONTENT_CHARS) {
            log.warn("模型输出 rawRef 跳过挂载, reason=content_too_large, scene={}, phase={}, modelId={}, contentChars={}, maxChars={}",
                    scene,
                    phase,
                    response.getModelId(),
                    content.length(),
                    RAW_REF_MAX_CONTENT_CHARS);
            return;
        }
        String source = buildSource(scene, phase);
        RawRef rawRef;
        try {
            rawRef = rawResultStore.store(source, content, "text/plain");
        } catch (Exception ex) {
            log.error("模型输出 rawRef 挂载异常, reason=store_exception, scene={}, phase={}, modelId={}, source={}",
                    scene,
                    phase,
                    response.getModelId(),
                    source,
                    ex);
            return;
        }
        if (rawRef != null && rawRef.getRefId() != null && !rawRef.getRefId().isBlank()) {
            response.setRawRef(rawRef.getRefId());
            return;
        }
        if (rawRef != null && rawRef.getKey() != null && !rawRef.getKey().isBlank()) {
            response.setRawRef(rawRef.getKey());
        }
        if (response.getRawRef() == null || response.getRawRef().isBlank()) {
            log.warn("模型输出 rawRef 挂载失败, reason=empty_ref, scene={}, phase={}, modelId={}",
                    scene,
                    phase,
                    response.getModelId());
        }
    }

    private String buildSource(ModelScene scene, String phase) {
        String source = "model";
        if (scene != null) {
            source = source + ":" + scene.name().toLowerCase();
        }
        if (phase != null && !phase.isBlank()) {
            source = source + ":" + phase;
        }
        return source;
    }
}

