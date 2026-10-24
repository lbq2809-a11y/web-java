package com.pharmacy.consultation.dto;

import com.pharmacy.consultation.entity.MessageItem;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Chi tiết toàn bộ tin nhắn trong một phiên hội thoại.
 */
public class ConversationResponse {

    private String conversationId;
    private String title;
    private List<MessageItem> messages = new ArrayList<>();
    private Instant createdAt;
    private Instant updatedAt;

    public ConversationResponse() {
    }

    public ConversationResponse(String conversationId, String title, List<MessageItem> messages,
                                Instant createdAt, Instant updatedAt) {
        this.conversationId = conversationId;
        this.title = title;
        this.messages = messages != null ? messages : new ArrayList<>();
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

    public List<MessageItem> getMessages() {
        return messages;
    }

    public void setMessages(List<MessageItem> messages) {
        this.messages = messages;
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

