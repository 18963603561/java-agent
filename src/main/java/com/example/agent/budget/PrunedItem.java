package com.example.agent.budget;

/**
 * 被裁剪的内容项。
 */
public class PrunedItem {

    /**
     * 项类型。
     */
    private String itemType;

    /**
     * 项标识。
     */
    private String itemId;

    /**
     * 裁剪原因。
     */
    private String reason;

    public String getItemType() {
        return itemType;
    }

    public void setItemType(String itemType) {
        this.itemType = itemType;
    }

    public String getItemId() {
        return itemId;
    }

    public void setItemId(String itemId) {
        this.itemId = itemId;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}