package com.pharmacy.consultation.service;

import com.pharmacy.consultation.entity.KnowledgeSource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Chế độ trích đoạn dự phòng (Extractive Fallback Mode):
 * Tự động kích hoạt khi dịch vụ LLM bên ngoài bị gián đoạn, hết hạn mức, timeout hoặc chưa cấu hình API key.
 * Trả về thông tin trích xuất trực tiếp từ các đoạn Top-K trong kho tri thức, kèm nguồn và cảnh báo,
 * TUYỆT ĐỐI KHÔNG để xảy ra lỗi HTTP 500.
 */
@ApplicationScoped
public class ExtractiveFallbackService {

    @Inject
    TemplateAnswerGenerator templateGenerator;

    public record FallbackResult(String answer, List<KnowledgeSource> sources, String disclaimer) {}

    public FallbackResult generateFallback(List<VectorSearchService.ScoredChunk> scoredChunks) {
        if (scoredChunks == null || scoredChunks.isEmpty()) {
            return new FallbackResult(
                    templateGenerator.generateUnknownAnswer().answer(),
                    List.of(),
                    templateGenerator.getDisclaimer()
            );
        }

        StringBuilder sb = new StringBuilder();
        sb.append("1. Tóm tắt thông tin (Trích dẫn trực tiếp từ cơ sở dữ liệu nhà thuốc):\n");

        List<KnowledgeSource> sources = new ArrayList<>();
        Set<String> medicineNames = new LinkedHashSet<>();
        boolean hasEtc = false;

        for (VectorSearchService.ScoredChunk sc : scoredChunks) {
            VectorItem item = sc.item();
            sb.append(String.format("- [%s - %s]: %s\n",
                    item.medicineName(), translateSection(item.section()), item.text()));

            sources.add(new KnowledgeSource(item.medicineId(), item.medicineName(), item.section()));
            medicineNames.add(item.medicineName());

            if (item.text() != null && item.text().toUpperCase().contains("ETC")) {
                hasEtc = true;
            }
        }

        sb.append("\n2. Lưu ý quan trọng:\n");
        if (hasEtc) {
            sb.append("⚠️ Đây là thông tin trích xuất tự động. Thuốc kê đơn (ETC) bắt buộc phải có chỉ định và đơn thuốc của bác sĩ.\n");
        } else {
            sb.append("⚠️ Đây là thông tin trích dẫn tự động từ dược thư. Vui lòng đọc kỹ hướng dẫn sử dụng trước khi dùng.\n");
        }

        sb.append("\n3. Nguồn tham khảo: ");
        sb.append(String.join(", ", medicineNames)).append(".");

        return new FallbackResult(sb.toString(), sources, templateGenerator.getDisclaimer());
    }

    private String translateSection(String section) {
        if (section == null) return "Thông tin";
        return switch (section.toLowerCase()) {
            case "indications" -> "Chỉ định";
            case "contraindications" -> "Chống chỉ định";
            case "dosage" -> "Cách dùng";
            case "composition" -> "Thành phần";
            case "general" -> "Thông tin chung";
            default -> section;
        };
    }
}

