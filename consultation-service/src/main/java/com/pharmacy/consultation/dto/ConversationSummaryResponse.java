package com.pharmacy.consultation.dto;

import java.time.Instant;

/**
 * Tóm tắt phiên hội thoại trong danh sách lịch sử của người dùng.
 */
public class ConversationSummaryResponse {

    private String conversationId;
    private String title;
    private int messageCount;
    private Instant createdAt;
    private Instant updatedAt;

    public ConversationSummaryResponse() {
    }

    public ConversationSummaryResponse(String conversationId, String title, int messageCount,
                                       Instant createdAt, Instant updatedAt) {
        this.conversationId = conversationId;
        this.title = title;
        this.messageCount = messageCount;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public int getMessageCount() {
        return messageCount;
    }

    public void setMessageCount(int messageCount) {
        this.messageCount = messageCount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}

