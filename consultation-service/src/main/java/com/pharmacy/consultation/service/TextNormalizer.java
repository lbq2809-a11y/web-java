package com.pharmacy.consultation.service;

import jakarta.enterprise.context.ApplicationScoped;

import java.text.Normalizer;
import java.util.regex.Pattern;

/**
 * Tiện ích chuẩn hóa chuỗi tiếng Việt và tính toán độ tương đồng xâu ký tự.
 */
@ApplicationScoped
public class TextNormalizer {

    private static final Pattern DIACRITICS_PATTERN = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
    private static final Pattern PUNCTUATION_PATTERN = Pattern.compile("[.,?!:;'\"]|[\\[\\](){}]");
    private static final Pattern MULTI_SPACE_PATTERN = Pattern.compile("\\s+");

    /**
     * Chuẩn hóa cơ bản: chuyển chữ thường, loại bỏ dấu câu, thu gọn khoảng trắng thừa.
     */
    public String normalize(String text) {
        if (text == null) {
            return "";
        }
        String cleaned = PUNCTUATION_PATTERN.matcher(text).replaceAll(" ");
        cleaned = MULTI_SPACE_PATTERN.matcher(cleaned).replaceAll(" ");
        return cleaned.trim().toLowerCase();
    }

    /**
     * Loại bỏ dấu tiếng Việt (Normalizer NFD) và thay thế ký tự đ/Đ thành d.
     */
    public String removeDiacritics(String text) {
        if (text == null) {
            return "";
        }
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFD);
        String withoutMarks = DIACRITICS_PATTERN.matcher(normalized).replaceAll("");
        return withoutMarks.replace('đ', 'd').replace('Đ', 'd');
    }

    /**
     * Chuẩn hóa kép: vừa chuẩn hóa vừa bỏ dấu tiếng Việt.
     */
    public String normalizeWithoutDiacritics(String text) {
        return removeDiacritics(normalize(text));
    }

    /**
     * Tính khoảng cách Levenshtein giữa hai chuỗi ký tự.
     */
    public int levenshteinDistance(String s1, String s2) {
        if (s1 == null) s1 = "";
        if (s2 == null) s2 = "";

        int[] costs = new int[s2.length() + 1];
        for (int j = 0; j <= s2.length(); j++) {
            costs[j] = j;
        }

        for (int i = 1; i <= s1.length(); i++) {
            costs[0] = i;
            int nw = i - 1;
            for (int j = 1; j <= s2.length(); j++) {
                int cj = Math.min(
                        1 + Math.min(costs[j], costs[j - 1]),
                        s1.charAt(i - 1) == s2.charAt(j - 1) ? nw : nw + 1
                );
                nw = costs[j];
                costs[j] = cj;
            }
        }
        return costs[s2.length()];
    }

    /**
     * Tính hệ số tương đồng (0.0 đến 1.0) dựa trên khoảng cách Levenshtein.
     */
    public double similarity(String s1, String s2) {
        if (s1 == null || s2 == null) return 0.0;
        if (s1.equalsIgnoreCase(s2)) return 1.0;

        int maxLen = Math.max(s1.length(), s2.length());
        if (maxLen == 0) return 1.0;

        int distance = levenshteinDistance(s1, s2);
        return 1.0 - ((double) distance / maxLen);
    }
}

