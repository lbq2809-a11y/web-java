package com.pharmacy.consultation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Yêu cầu đặt câu hỏi tư vấn thuốc.
 */
public class AskRequest {

    private String conversationId;

    @NotBlank(message = "Câu hỏi không được để trống")
    @Size(max = 500, message = "Câu hỏi không được vượt quá 500 ký tự")
    private String question;

    public AskRequest() {
    }

    public AskRequest(String conversationId, String question) {
        this.conversationId = conversationId;
        this.question = question;
    }

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }
}

