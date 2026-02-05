package com.example.agent.capabilities.context.research;

import java.time.Instant;

/**
 * 研究引用记录。
 */
public class ResearchCitation {

    private String source;
    private String snippet;
    private Instant fetchedAt;

    public ResearchCitation() {
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getSnippet() {
        return snippet;
    }

    public void setSnippet(String snippet) {
        this.snippet = snippet;
    }

    public Instant getFetchedAt() {
        return fetchedAt;
    }

    public void setFetchedAt(Instant fetchedAt) {
        this.fetchedAt = fetchedAt;
    }
}
