package com.example.agent.research;

import java.util.List;

/**
 * 研究流程执行结果。
 */
public class ResearchRunResult {

    /**
     * 引用列表。
     */
    private List<ResearchCitation> citations;

    /**
     * 模型原始输出引用键。
     */
    private String rawRef;

    public ResearchRunResult() {
    }

    public ResearchRunResult(List<ResearchCitation> citations, String rawRef) {
        this.citations = citations;
        this.rawRef = rawRef;
    }

    public List<ResearchCitation> getCitations() {
        return citations;
    }

    public void setCitations(List<ResearchCitation> citations) {
        this.citations = citations;
    }

    public String getRawRef() {
        return rawRef;
    }

    public void setRawRef(String rawRef) {
        this.rawRef = rawRef;
    }
}
