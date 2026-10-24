package com.pharmacy.consultation.client;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

import java.util.List;

/**
 * Bản giả lập (Mock) LlmClient phục vụ kiểm thử đơn vị và tích hợp trong src/test.
 * Có cờ simulateFailure để kiểm tra Chế độ trích đoạn dự phòng (Extractive Fallback).
 */
@Alternative
@Priority(1)
@ApplicationScoped
public class MockLlmClient implements LlmClient {

    private boolean simulateFailure = false;

    public void setSimulateFailure(boolean simulateFailure) {
        this.simulateFailure = simulateFailure;
    }

    @Override
    public String generateAnswer(String question, List<String> contextChunks, String conversationHistory) {
        if (simulateFailure) {
            throw new RuntimeException("Simulated LLM connection timeout / rate limit error");
        }

        StringBuilder sb = new StringBuilder();
        sb.append("1. Tóm tắt thông tin: Dựa trên cơ sở dữ liệu y dược, câu trả lời cho câu hỏi của bạn như sau: ");
        if (contextChunks != null && !contextChunks.isEmpty()) {
            sb.append("Hệ thống ghi nhận thông tin phù hợp từ các thuốc trong kho. ");
        }

        sb.append("\n2. Lưu ý quan trọng: Vui lòng đọc kỹ hướng dẫn sử dụng. Đối với thuốc kê đơn (ETC), bắt buộc cần có chỉ định của bác sĩ.");
        sb.append("\n3. Nguồn tham khảo: Dữ liệu thuốc nhà thuốc trực tuyến.");

        return sb.toString();
    }

    @Override
    public String streamAnswer(String question, List<String> contextChunks, String conversationHistory) {
        return generateAnswer(question, contextChunks, conversationHistory);
    }
}

