package com.example.agent.memory;

/**
 * 记忆片段，用于长文本分块。
 */
public class MemoryChunk {

    private String chunkId;
    private String memoryId;
    private String content;
    private String embeddingRef;
    private int position;

    public MemoryChunk() {
    }

    public String getChunkId() {
        return chunkId;
    }

    public void setChunkId(String chunkId) {
        this.chunkId = chunkId;
    }

    public String getMemoryId() {
        return memoryId;
    }

    public void setMemoryId(String memoryId) {
        this.memoryId = memoryId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getEmbeddingRef() {
        return embeddingRef;
    }

    public void setEmbeddingRef(String embeddingRef) {
        this.embeddingRef = embeddingRef;
    }

    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
    }
}
