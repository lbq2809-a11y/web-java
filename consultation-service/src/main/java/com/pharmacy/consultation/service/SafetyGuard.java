package com.pharmacy.consultation.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;

/**
 * Bộ lọc an toàn (Safety Guard) phát hiện câu hỏi khẩn cấp, nguy kịch, tự hại
 * hoặc nhóm đối tượng nhạy cảm (bà bầu, trẻ sơ sinh).
 */
@ApplicationScoped
public class SafetyGuard {

    public enum SafetyStatus {
        SAFE,
        EMERGENCY,
        VULNERABLE
    }

    private static final List<String> EMERGENCY_KEYWORDS = List.of(
            "cap cuu", "cấp cứu", "115",
            "tu tu", "tự tử", "tu hai", "tự hại", "chet", "chết",
            "ngung tho", "ngừng thở", "kho tho", "khó thở",
            "co giat", "co giật", "hon me", "hôn mê",
            "dot quy", "đột quỵ", "tai bien", "tai biến",
            "ngo doc", "ngộ độc", "uong thuoc qua lieu", "uống thuốc quá liều"
    );

    private static final List<String> VULNERABLE_KEYWORDS = List.of(
            "mang thai", "co thai", "có thai", "ba bau", "bà bầu",
            "cho con bu", "cho con bú",
            "tre so sinh", "trẻ sơ sinh", "tre nho", "trẻ nhỏ",
            "duoi 1 tuoi", "dưới 1 tuổi", "tre em", "trẻ em"
    );

    @Inject
    TextNormalizer textNormalizer;

    /**
     * Kiểm tra mức độ an toàn của câu hỏi.
     */
    public SafetyStatus check(String rawQuestion) {
        if (rawQuestion == null || rawQuestion.isBlank()) {
            return SafetyStatus.SAFE;
        }

        String normalized = textNormalizer.normalize(rawQuestion);
        String noDiacritics = textNormalizer.normalizeWithoutDiacritics(rawQuestion);

        // 1. Kiểm tra cấp cứu / nguy kịch / tự hại trước
        for (String keyword : EMERGENCY_KEYWORDS) {
            String normKey = textNormalizer.normalize(keyword);
            String noDiaKey = textNormalizer.normalizeWithoutDiacritics(keyword);
            if (normalized.contains(normKey) || noDiacritics.contains(noDiaKey)) {
                return SafetyStatus.EMERGENCY;
            }
        }

        // 2. Kiểm tra phụ nữ mang thai / trẻ sơ sinh
        for (String keyword : VULNERABLE_KEYWORDS) {
            String normKey = textNormalizer.normalize(keyword);
            String noDiaKey = textNormalizer.normalizeWithoutDiacritics(keyword);
            if (normalized.contains(normKey) || noDiacritics.contains(noDiaKey)) {
                return SafetyStatus.VULNERABLE;
            }
        }

        return SafetyStatus.SAFE;
    }
}

