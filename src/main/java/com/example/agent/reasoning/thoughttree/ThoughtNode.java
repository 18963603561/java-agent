package com.example.agent.reasoning.thoughttree;

import java.util.List;

/**
 * 鎬濈淮鑺傜偣锛岃〃绀烘帹鐞嗘爲鑺傜偣銆?
 */
public class ThoughtNode {

    /**
     * 鑺傜偣鏍囥€?
     */
    private String nodeId;

    /**
     * 鑺傜偣鍐呭锛屾彁渚涚粰鍚庣画鎵╁睍涓庤评鍒嗐€?
     */
    private String content;

    /**
     * 鑺傜偣璇勫垎锛屽弽鏄犺鎬濊€冭矾寰勭殑鍙鎬с€?
     */
    private double score;

    /**
     * 鐖惰妭鐐规爣璇嗭紝鐢ㄤ簬鏋勫缓鏍戠姸鍏崇郴銆?
     */
    private String parentId;

    /**
     * 褰撳墠鑺傜偣鐨勬繁搴︺€?
     */
    private int depth;

    /**
     * 鏄惁涓虹粓姝㈣妭鐐癸紝鍙敤浜庨€夋嫨鏈€浼樿矾寰勩€?
     */
    private boolean terminal;

    /**
     * 鎬濊€冨彉鍖栬鏄庯紝鐢ㄤ簬璁板綍璺敱鍘熷洜銆?
     */
    private String explanation;

    /**
     * 鎯宠薄 token 浣跨敤閲忥紝鐢ㄤ簬瀹忚棰勭畻璇勪及銆?
     */
    private int tokensUsed;

    /**
     * 瀛愯妭鐐瑰垪琛紝鐢ㄤ簬鏋勫缓鎬濈淮鏍戠粨鏋勩€?
     */
    private List<ThoughtNode> children;

    public ThoughtNode() {
    }

    public String getNodeId() {
        return nodeId;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public double getScore() {
        return score;
    }

    public void setScore(double score) {
        this.score = score;
    }

    public String getParentId() {
        return parentId;
    }

    public void setParentId(String parentId) {
        this.parentId = parentId;
    }

    public int getDepth() {
        return depth;
    }

    public void setDepth(int depth) {
        this.depth = depth;
    }

    public boolean isTerminal() {
        return terminal;
    }

    public void setTerminal(boolean terminal) {
        this.terminal = terminal;
    }

    public String getExplanation() {
        return explanation;
    }

    public void setExplanation(String explanation) {
        this.explanation = explanation;
    }

    public int getTokensUsed() {
        return tokensUsed;
    }

    public void setTokensUsed(int tokensUsed) {
        this.tokensUsed = tokensUsed;
    }

    public List<ThoughtNode> getChildren() {
        return children;
    }

    public void setChildren(List<ThoughtNode> children) {
        this.children = children;
    }
}
