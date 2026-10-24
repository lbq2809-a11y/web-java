package com.pharmacy.consultation.client;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.annotation.Priority;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Bản giả lập (Mock) EmbeddingClient trong src/test sử dụng vector mẫu xác định (Deterministic Vectors)
 * dựa trên từ khóa y dược / nhóm bệnh, đảm bảo các phép tính Cosine Similarity luôn ra kết quả nhất quán 100%.
 */
@Alternative
@Priority(1)
@ApplicationScoped
public class MockEmbeddingClient implements EmbeddingClient {

    private static final Pattern DIACRITICS = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");

    @Override
    public List<Double> embedText(String text) {
        if (text == null || text.isBlank()) {
            return normalize(List.of(0.01, 0.01, 0.01, 0.01, 0.01, 0.01, 0.01, 0.01));
        }

        String norm = removeDiacritics(text.toLowerCase());

        // 8 Chiều đặc trưng chủ đề:
        // 0: Đau đầu / Giảm đau
        // 1: Hạ sốt
        // 2: Kháng sinh / Nhiễm khuẩn / Nhiễm trùng hô hấp
        // 3: Tai mũi họng / Viêm phế quản
        // 4: Dạng sủi / Hòa tan
        // 5: Thuốc kê đơn / Bác sĩ
        // 6: Chức năng gan / Thận
        // 7: Dược thư chung
        double[] dims = new double[8];
        dims[7] = 0.05; // Base noise

        if (containsAny(norm, "dau", "nhuc dau", "dau dau", "giam dau", "panadol", "paracetamol", "dau rang", "nua dau")) {
            dims[0] += 0.95;
        }
        if (containsAny(norm, "sot", "ha sot", "paracetamol", "panadol", "efferalgan")) {
            dims[1] += 0.90;
        }
        if (containsAny(norm, "nhiem khuan", "nhiem trung", "khang sinh", "augmentin", "amoxicillin", "ho hap", "duong ho hap", "khuan", "vi khuan", "tiet nieu", "viem")) {
            dims[2] += 0.95;
        }
        if (containsAny(norm, "viem hong", "tai mui hong", "viem tai", "phe quan", "tai", "hong", "augmentin", "ho hap", "duong ho hap")) {
            dims[3] += 0.90;
        }
        if (containsAny(norm, "sui", "hoa tan", "efferalgan", "vien sui", "pha")) {
            dims[4] += 0.95;
        }
        if (containsAny(norm, "ke don", "bac si", "etc", "toa thuoc", "chi dinh", "augmentin")) {
            dims[5] += 0.85;
        }
        if (containsAny(norm, "gan", "suy gan", "chong chi dinh", "man cam", "di ung", "penicillin", "beta-lactam")) {
            dims[6] += 0.85;
        }

        List<Double> vector = new ArrayList<>();
        for (double d : dims) {
            vector.add(d);
        }
        return normalize(vector);
    }

    @Override
    public List<List<Double>> embedBatch(List<String> texts) {
        if (texts == null) return List.of();
        List<List<Double>> result = new ArrayList<>();
        for (String t : texts) {
            result.add(embedText(t));
        }
        return result;
    }

    private boolean containsAny(String text, String... keywords) {
        for (String kw : keywords) {
            if (text.contains(kw)) {
                return true;
            }
        }
        return false;
    }

    private String removeDiacritics(String text) {
        String nfd = Normalizer.normalize(text, Normalizer.Form.NFD);
        return DIACRITICS.matcher(nfd).replaceAll("").replace('đ', 'd').replace('Đ', 'd');
    }

    private List<Double> normalize(List<Double> v) {
        double sum = 0.0;
        for (double d : v) {
            sum += d * d;
        }
        double norm = Math.sqrt(sum);
        if (norm <= 0.0) {
            return v;
        }
        List<Double> normalized = new ArrayList<>();
        for (double d : v) {
            normalized.add(d / norm);
        }
        return normalized;
    }
}
