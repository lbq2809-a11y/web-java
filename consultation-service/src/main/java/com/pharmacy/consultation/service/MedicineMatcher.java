package com.pharmacy.consultation.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Nhận diện thuốc từ câu hỏi của người dùng (khớp chính xác tên thuốc/hoạt chất có dấu và không dấu,
 * và khớp mờ Levenshtein chịu lỗi chính tả nhẹ).
 */
@ApplicationScoped
public class MedicineMatcher {

    @Inject
    MedicineKnowledgeCache medicineCache;

    @Inject
    TextNormalizer textNormalizer;

    @ConfigProperty(name = "consultation.matcher.similarity-threshold", defaultValue = "0.8")
    double similarityThreshold;

    private static final Set<String> STOP_WORDS = Set.of(
            "thuoc", "vien", "goi", "dung", "dich", "tiem", "uong", "cho", "nguoi",
            "1g", "2g", "500mg", "250mg", "100mg", "10mg", "20mg", "50mg"
    );

    public static class MatchResult {
        private final List<CachedMedicine> matches;
        private final boolean isSingleMatch;

        public MatchResult(List<CachedMedicine> matches) {
            this.matches = matches != null ? matches : new ArrayList<>();
            this.isSingleMatch = this.matches.size() == 1;
        }

        public List<CachedMedicine> getMatches() {
            return matches;
        }

        public boolean isSingleMatch() {
            return isSingleMatch;
        }

        public boolean isEmpty() {
            return matches.isEmpty();
        }

        public CachedMedicine getFirst() {
            return matches.isEmpty() ? null : matches.get(0);
        }
    }

    /**
     * Tìm danh sách các thuốc khớp với câu hỏi.
     */
    public MatchResult findMatchingMedicines(String question) {
        if (question == null || question.isBlank()) {
            return new MatchResult(List.of());
        }

        List<CachedMedicine> allMedicines = medicineCache.getAll();
        if (allMedicines.isEmpty()) {
            return new MatchResult(List.of());
        }

        String normQ = textNormalizer.normalize(question);
        String noDiaQ = textNormalizer.normalizeWithoutDiacritics(question);

        Map<String, CachedMedicine> matchedMap = new LinkedHashMap<>();

        // 1. Khớp chính xác tên thuốc đầy đủ hoặc nhãn hiệu thuốc (brand name)
        for (CachedMedicine med : allMedicines) {
            if (isExactMedicineNameMatch(normQ, noDiaQ, med)) {
                matchedMap.put(med.getMedicineId(), med);
            }
        }

        // 2. Nếu chưa khớp tên, kiểm tra khớp chính xác hoạt chất
        if (matchedMap.isEmpty()) {
            for (CachedMedicine med : allMedicines) {
                if (isExactIngredientMatch(normQ, noDiaQ, med)) {
                    matchedMap.put(med.getMedicineId(), med);
                }
            }
        }

        // 3. Nếu vẫn chưa khớp, thử so khớp mờ (Fuzzy Levenshtein) cho tên thuốc và hoạt chất
        if (matchedMap.isEmpty()) {
            for (CachedMedicine med : allMedicines) {
                if (isFuzzyMatch(normQ, noDiaQ, med)) {
                    matchedMap.put(med.getMedicineId(), med);
                }
            }
        }

        return new MatchResult(new ArrayList<>(matchedMap.values()));
    }

    private boolean isExactMedicineNameMatch(String normQ, String noDiaQ, CachedMedicine med) {
        String medNorm = med.getNormalizedName();
        String medNoDia = med.getNoDiacriticsName();

        if (medNorm != null && !medNorm.isBlank() && containsWordOrPhrase(normQ, medNorm)) {
            return true;
        }
        if (medNoDia != null && !medNoDia.isBlank() && containsWordOrPhrase(noDiaQ, medNoDia)) {
            return true;
        }

        // Kiểm tra từng từ khóa nhãn hiệu riêng lẻ (ví dụ "Panadol" trong "Panadol Extra", "Augmentin" trong "Augmentin 1g")
        if (medNoDia != null && !medNoDia.isBlank()) {
            String[] tokens = medNoDia.split("\\s+");
            for (String token : tokens) {
                if (token.length() >= 4 && !STOP_WORDS.contains(token)) {
                    if (containsWordOrPhrase(noDiaQ, token)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean isExactIngredientMatch(String normQ, String noDiaQ, CachedMedicine med) {
        String ingNorm = med.getNormalizedActiveIngredient();
        String ingNoDia = med.getNoDiacriticsActiveIngredient();

        if (ingNorm != null && !ingNorm.isBlank()) {
            String[] parts = ingNorm.split("\\s*\\+\\s*|\\s*,\\s*|\\s*;\\s*");
            for (String part : parts) {
                String trimmed = part.trim();
                if (trimmed.length() >= 3 && containsWordOrPhrase(normQ, trimmed)) {
                    return true;
                }
            }
        }

        if (ingNoDia != null && !ingNoDia.isBlank()) {
            String[] parts = ingNoDia.split("\\s*\\+\\s*|\\s*,\\s*|\\s*;\\s*");
            for (String part : parts) {
                String trimmed = part.trim();
                if (trimmed.length() >= 3 && containsWordOrPhrase(noDiaQ, trimmed)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isFuzzyMatch(String normQ, String noDiaQ, CachedMedicine med) {
        List<String> questionWords = Arrays.asList(noDiaQ.split("\\s+"));
        if (questionWords.isEmpty()) return false;

        // So khớp mờ với cả tên đầy đủ
        if (checkFuzzyTokens(questionWords, med.getNoDiacriticsName())) {
            return true;
        }

        // So khớp mờ với từng token trong tên thuốc (ví dụ "panadoll" vs "panadol")
        String medNoDia = med.getNoDiacriticsName();
        if (medNoDia != null) {
            String[] nameTokens = medNoDia.split("\\s+");
            for (String nt : nameTokens) {
                if (nt.length() >= 4 && !STOP_WORDS.contains(nt)) {
                    if (checkFuzzyTokens(questionWords, nt)) {
                        return true;
                    }
                }
            }
        }

        // So khớp mờ với từng hoạt chất (ví dụ "paracetamon" vs "paracetamol")
        String ingNoDia = med.getNoDiacriticsActiveIngredient();
        if (ingNoDia != null && !ingNoDia.isBlank()) {
            String[] parts = ingNoDia.split("\\s*\\+\\s*|\\s*,\\s*|\\s*;\\s*");
            for (String part : parts) {
                String trimmed = part.trim();
                if (trimmed.length() >= 4) {
                    if (checkFuzzyTokens(questionWords, trimmed)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private boolean checkFuzzyTokens(List<String> words, String target) {
        if (target == null || target.isBlank()) return false;
        String[] targetTokens = target.split("\\s+");
        int targetWordCount = targetTokens.length;

        // Trượt cửa sổ độ dài targetWordCount (hoặc +-1) trên câu hỏi
        for (int windowSize = Math.max(1, targetWordCount - 1); windowSize <= targetWordCount + 1; windowSize++) {
            for (int i = 0; i <= words.size() - windowSize; i++) {
                StringBuilder sb = new StringBuilder();
                for (int j = 0; j < windowSize; j++) {
                    if (j > 0) sb.append(" ");
                    sb.append(words.get(i + j));
                }
                String candidate = sb.toString();
                if (candidate.length() >= 4 && !STOP_WORDS.contains(candidate)) {
                    int dist = textNormalizer.levenshteinDistance(candidate, target);
                    double sim = textNormalizer.similarity(candidate, target);
                    if (dist <= 2 && sim >= similarityThreshold) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean containsWordOrPhrase(String text, String phrase) {
        if (text == null || phrase == null || phrase.isBlank()) return false;
        // Kiểm tra chuỗi con có ranh giới từ hoặc nằm trong câu
        return text.contains(phrase);
    }
}
