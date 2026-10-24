package com.pharmacy.consultation.entity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Một tin nhắn trong phiên hội thoại (người dùng hoặc trợ lý AI).
 */
public class MessageItem {

    private String role; // "user" hoặc "assistant"
    private String content;
    private List<KnowledgeSource> sources = new ArrayList<>();
    private int tier; // 0 cho user, 1 cho tier 1 template
    private Instant createdAt;

    public MessageItem() {
    }

    public MessageItem(String role, String content, List<KnowledgeSource> sources, int tier, Instant createdAt) {
        this.role = role;
        this.content = content;
        this.sources = sources != null ? sources : new ArrayList<>();
        this.tier = tier;
        this.createdAt = createdAt;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public List<KnowledgeSource> getSources() {
        return sources;
    }

    public void setSources(List<KnowledgeSource> sources) {
        this.sources = sources;
    }

    public int getTier() {
        return tier;
    }

    public void setTier(int tier) {
        this.tier = tier;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}

