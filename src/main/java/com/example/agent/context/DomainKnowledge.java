package com.example.agent.context;

import java.util.List;

/**
 * 领域知识引用。
 */
public class DomainKnowledge {

    /**
     * 引用列表。
     */
    private List<Citation> citations;

    public List<Citation> getCitations() {
        return citations;
    }

    public void setCitations(List<Citation> citations) {
        this.citations = citations;
    }
}