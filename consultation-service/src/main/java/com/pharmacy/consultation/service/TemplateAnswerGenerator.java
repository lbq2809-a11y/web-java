package com.pharmacy.consultation.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pharmacy.consultation.entity.KnowledgeSource;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.io.InputStream;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Sinh câu trả lời theo khuôn mẫu (templates.json) tại Tầng 1, đính kèm cảnh báo ETC và disclaimer cố định.
 */
@ApplicationScoped
public class TemplateAnswerGenerator {

    private static final Logger LOG = Logger.getLogger(TemplateAnswerGenerator.class);

    private final Map<String, String> templatesMap = new HashMap<>();

    @Inject
    ObjectMapper objectMapper;

    void onStart(@Observes StartupEvent ev) {
        loadTemplates();
    }

    public synchronized void loadTemplates() {
        try (InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream("templates.json")) {
            if (is == null) {
                LOG.error("Không tìm thấy file tài nguyên templates.json");
                return;
            }
            Map<String, String> loaded = objectMapper.readValue(is, new TypeReference<>() {});
            templatesMap.clear();
            templatesMap.putAll(loaded);
            LOG.infof("Đã nạp thành công %d mẫu câu trả lời từ templates.json", templatesMap.size());
        } catch (Exception e) {
            LOG.error("Lỗi khi nạp templates.json", e);
        }
    }

    public record GeneratedAnswer(String answer, List<KnowledgeSource> sources, String disclaimer) {}

    public GeneratedAnswer generateForMedicine(CachedMedicine medicine, String intent) {
        String template = templatesMap.getOrDefault(intent, templatesMap.get("UNKNOWN"));
        if (template == null) {
            template = "Thông tin về thuốc {name}: đang được cập nhật.";
        }

        String section = "general";
        String name = medicine.getName() != null ? medicine.getName() : "sản phẩm";
        String answer = template.replace("{name}", name);

        switch (intent) {
            case "THANH_PHAN" -> {
                section = "composition";
                String ing = medicine.getActiveIngredient() != null ? medicine.getActiveIngredient() : "Chưa có thông tin";
                answer = answer.replace("{activeIngredient}", ing);
            }
            case "CHI_DINH" -> {
                section = "indications";
                String ind = medicine.getIndications() != null ? medicine.getIndications() : "Chưa có thông tin";
                answer = answer.replace("{indications}", ind);
            }
            case "CHONG_CHI_DINH" -> {
                section = "contraindications";
                String contra = medicine.getContraindications() != null ? medicine.getContraindications() : "Chưa có thông tin";
                answer = answer.replace("{contraindications}", contra);
            }
            case "CACH_DUNG" -> {
                section = "dosage";
                String dos = medicine.getDosage() != null ? medicine.getDosage() : "Chưa có thông tin";
                answer = answer.replace("{dosage}", dos);
            }
            case "LOAI_THUOC" -> {
                section = "prescription_type";
                String desc = "ETC".equalsIgnoreCase(medicine.getPrescriptionType())
                        ? "thuốc kê đơn (ETC)"
                        : "thuốc không kê đơn (OTC)";
                answer = answer.replace("{prescriptionTypeDescription}", desc);
            }
            case "GIA" -> {
                section = "price";
                DecimalFormat df = new DecimalFormat("#,###", DecimalFormatSymbols.getInstance(Locale.US));
                String priceStr = medicine.getPrice() != null ? df.format(medicine.getPrice()) : "Liên hệ";
                String unit = medicine.getUnit() != null ? medicine.getUnit() : "đơn vị";
                answer = answer.replace("{price}", priceStr).replace("{unit}", unit);
            }
            default -> {
                // Giữ nguyên answer
            }
        }

        // Bổ sung cảnh báo nếu là thuốc kê đơn (ETC)
        if ("ETC".equalsIgnoreCase(medicine.getPrescriptionType())) {
            String etcWarning = templatesMap.getOrDefault("ETC_WARNING", "\n\n⚠️ Lưu ý quan trọng: Đây là thuốc kê đơn (ETC), bắt buộc phải có chỉ định và đơn thuốc của bác sĩ.");
            answer = answer + etcWarning;
        }

        List<KnowledgeSource> sources = List.of(
                new KnowledgeSource(medicine.getMedicineId(), medicine.getName(), section)
        );

        String disclaimer = templatesMap.getOrDefault("DISCLAIMER",
                "Thông tin tư vấn từ trợ lý AI chỉ mang tính chất tham khảo, không thay thế cho chẩn đoán, chỉ định hay tư vấn của bác sĩ và chuyên gia y tế.");

        return new GeneratedAnswer(answer, sources, disclaimer);
    }

    public GeneratedAnswer generateEmergencyAnswer() {
        String answer = templatesMap.getOrDefault("EMERGENCY",
                "Trường hợp khẩn cấp hoặc có dấu hiệu nguy kịch, vui lòng gọi cấp cứu 115 hoặc đến ngay cơ sở y tế gần nhất để được hỗ trợ y tế kịp thời!");
        String disclaimer = getDisclaimer();
        return new GeneratedAnswer(answer, List.of(), disclaimer);
    }

    public GeneratedAnswer generateVulnerableAnswer() {
        String answer = templatesMap.getOrDefault("VULNERABLE",
                "Đối tượng phụ nữ có thai, đang cho con bú hoặc trẻ nhỏ/trẻ sơ sinh rất nhạy cảm với dược chất. Vui lòng tham khảo ý kiến trực tiếp của bác sĩ chuyên khoa trước khi sử dụng.");
        String disclaimer = getDisclaimer();
        return new GeneratedAnswer(answer, List.of(), disclaimer);
    }

    public GeneratedAnswer generateUnknownAnswer() {
        String answer = templatesMap.getOrDefault("UNKNOWN",
                "Hiện tại trợ lý AI chưa có đủ thông tin chi tiết về thuốc hoặc câu hỏi này. Bạn vui lòng liên hệ trực tiếp với dược sĩ chuyên môn để được tư vấn cụ thể và an toàn nhất.");
        String disclaimer = getDisclaimer();
        return new GeneratedAnswer(answer, List.of(), disclaimer);
    }

    public GeneratedAnswer generateMultipleMatchAnswer(List<CachedMedicine> matches) {
        StringBuilder sb = new StringBuilder("Hệ thống tìm thấy nhiều loại thuốc liên quan đến câu hỏi của bạn. Vui lòng chọn hoặc hỏi cụ thể về một trong các thuốc sau:\n");
        for (CachedMedicine m : matches) {
            sb.append("- ").append(m.getName());
            if (m.getActiveIngredient() != null && !m.getActiveIngredient().isBlank()) {
                sb.append(" (Hoạt chất: ").append(m.getActiveIngredient()).append(")");
            }
            sb.append("\n");
        }
        return new GeneratedAnswer(sb.toString().trim(), List.of(), getDisclaimer());
    }

    public String getDisclaimer() {
        return templatesMap.getOrDefault("DISCLAIMER",
                "Thông tin tư vấn từ trợ lý AI chỉ mang tính chất tham khảo, không thay thế cho chẩn đoán, chỉ định hay tư vấn của bác sĩ và chuyên gia y tế.");
    }
}
