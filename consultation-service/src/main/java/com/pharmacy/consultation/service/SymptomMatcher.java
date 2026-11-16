package com.pharmacy.consultation.service;

import com.pharmacy.consultation.entity.KnowledgeChunkDocument;
import com.pharmacy.consultation.repository.KnowledgeChunkRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Bộ truy xuất thuốc Tầng 1 dựa theo triệu chứng & công dụng (Symptom-based Retrieval).
 * Dùng khi câu hỏi mô tả triệu chứng (không nêu rõ tên thuốc) và Tầng 2 LLM/Embedding chưa cấu hình hoặc chưa tìm được vector.
 */
@ApplicationScoped
public class SymptomMatcher {

    @Inject
    MedicineKnowledgeCache medicineCache;

    @Inject
    KnowledgeChunkRepository chunkRepository;

    @Inject
    TextNormalizer textNormalizer;

    public record SymptomMatchResult(
            List<CachedMedicine> medicines,
            boolean otcRequested,
            boolean etcRequested,
            List<String> matchedTerms
    ) {}

    /**
     * Bản đồ cụm từ triệu chứng / nhóm công dụng phổ biến (không dấu) -> danh sách từ khóa mục tiêu trong indications & name.
     */
    private static final List<SymptomRule> SYMPTOM_RULES = List.of(
            new SymptomRule(List.of("sot nhe", "sot cao", "ha sot", "bi sot", "con sot", "sot"), List.of("ha sot", "sot")),
            new SymptomRule(List.of("dau dau", "nhuc dau", "dau nua dau"), List.of("dau dau", "nua dau", "giam dau")),
            new SymptomRule(List.of("giam dau", "dau rang", "dau co", "dau nhuc", "dau khop", "viem khop", "thoai hoa khop"),
                    List.of("giam dau", "dau rang", "dau co", "viem khop", "thoai hoa khop", "khang viem")),
            new SymptomRule(List.of("cam cum", "cam lanh", "so mui", "hat hoi", "nghet mui", "viem mui", "ngat mui"),
                    List.of("so mui", "hat hoi", "viem mui", "ha sot", "giam dau")),
            new SymptomRule(List.of("ho khan", "ho co dom", "long dom", "tieu dom", "khac dom", "bi ho", "tri ho", "siro ho", "ho"),
                    List.of("ho", "dom", "tieu nhay", "phe quan")),
            new SymptomRule(List.of("viem hong", "dau hong", "rat hong", "viem phe quan", "duong ho hap", "ho hap"),
                    List.of("ho hap", "phe quan", "tai mui hong", "viem hong", "nhiem khuan")),
            new SymptomRule(List.of("dau da day", "da day", "bao tu", "o chua", "o nong", "trao nguoc", "loet da day", "thuong vi", "du acid", "kho tieu"),
                    List.of("da day", "trao nguoc", "o chua", "o nong", "thuong vi", "acid", "dich vi")),
            new SymptomRule(List.of("tieu chay", "di ngoai", "roi loan tieu hoa", "bu nuoc", "dien giai", "duong ruot", "tieu hoa"),
                    List.of("tieu chay", "bu nuoc", "dien giai", "tieu hoa", "duong ruot")),
            new SymptomRule(List.of("di ung", "me day", "man ngua", "ngua mui", "ngua mat", "ngua"),
                    List.of("di ung", "me day", "ngua", "khang histamin")),
            new SymptomRule(List.of("khang sinh", "nhiem khuan", "nhiem trung", "vi khuan", "tiet nieu"),
                    List.of("khang sinh", "nhiem khuan", "nhiem trung", "tiet nieu")),
            new SymptomRule(List.of("huyet ap", "tang huyet ap", "tim mach", "dau that nguc", "suy tim", "mo mau", "tieu duong", "dai thao duong"),
                    List.of("huyet ap", "tim", "thao duong", "lipid", "mach vanh")),
            new SymptomRule(List.of("vitamin", "de khang", "suc de khang", "met moi", "suy nhuoc", "canxi", "chuot rut", "moi co"),
                    List.of("vitamin", "met moi", "suy nhuoc", "canxi", "chuot rut", "moi co", "magnesi"))
    );

    private record SymptomRule(List<String> triggers, List<String> targets) {}

    public SymptomMatchResult findBySymptoms(String question) {
        if (question == null || question.isBlank()) {
            return new SymptomMatchResult(List.of(), false, false, List.of());
        }

        String noDiaQ = textNormalizer.normalizeWithoutDiacritics(question);

        boolean otcRequested = containsPhrase(noDiaQ, "khong ke don")
                || containsPhrase(noDiaQ, "otc")
                || containsPhrase(noDiaQ, "mua tu do")
                || containsPhrase(noDiaQ, "khong can don");

        boolean etcRequested = !otcRequested && (
                containsPhrase(noDiaQ, "ke don")
                        || containsPhrase(noDiaQ, "etc")
                        || containsPhrase(noDiaQ, "theo don")
        );

        // 1. Trích xuất các từ khóa mục tiêu từ câu hỏi triệu chứng
        Set<String> targetTerms = new LinkedHashSet<>();
        Set<String> matchedSymptoms = new LinkedHashSet<>();

        for (SymptomRule rule : SYMPTOM_RULES) {
            for (String trigger : rule.triggers()) {
                if (containsPhrase(noDiaQ, trigger)) {
                    matchedSymptoms.add(trigger);
                    targetTerms.addAll(rule.targets());
                }
            }
        }

        if (targetTerms.isEmpty()) {
            return new SymptomMatchResult(List.of(), otcRequested, etcRequested, List.of());
        }

        // 2. Tập hợp ứng viên từ RAM Cache + MongoDB knowledge_chunks
        Map<String, CachedMedicine> candidates = new LinkedHashMap<>();
        for (CachedMedicine med : medicineCache.getAll()) {
            if (med.getMedicineId() != null) {
                candidates.put(med.getMedicineId(), med);
            }
        }

        if (candidates.isEmpty()) {
            List<KnowledgeChunkDocument> chunks = chunkRepository.searchByKeywords(new ArrayList<>(targetTerms));
            for (KnowledgeChunkDocument chunk : chunks) {
                if (chunk.medicineId == null) continue;
                CachedMedicine cm = candidates.computeIfAbsent(chunk.medicineId, id -> {
                    CachedMedicine item = new CachedMedicine();
                    item.setMedicineId(id);
                    item.setName(chunk.medicineName);
                    item.setNormalizedName(textNormalizer.normalize(chunk.medicineName));
                    item.setNoDiacriticsName(textNormalizer.normalizeWithoutDiacritics(chunk.medicineName));
                    return item;
                });
                if ("indications".equals(chunk.section)) {
                    cm.setIndications(chunk.text);
                } else if ("composition".equals(chunk.section)) {
                    cm.setActiveIngredient(chunk.text);
                } else if ("general".equals(chunk.section) && chunk.text != null) {
                    cm.setPrescriptionType(chunk.text.contains("Nhóm: ETC") ? "ETC" : "OTC");
                }
            }
        }

        // 3. Chấm điểm mức độ khớp trên indications, name và activeIngredient
        List<ScoredMedicine> scored = new ArrayList<>();
        for (CachedMedicine med : candidates.values()) {
            if (otcRequested && "ETC".equalsIgnoreCase(med.getPrescriptionType())) {
                continue;
            }
            if (etcRequested && "OTC".equalsIgnoreCase(med.getPrescriptionType())) {
                continue;
            }

            String indNoDia = textNormalizer.normalizeWithoutDiacritics(med.getIndications());
            String nameNoDia = med.getNoDiacriticsName() != null
                    ? med.getNoDiacriticsName()
                    : textNormalizer.normalizeWithoutDiacritics(med.getName());
            String ingNoDia = med.getNoDiacriticsActiveIngredient() != null
                    ? med.getNoDiacriticsActiveIngredient()
                    : textNormalizer.normalizeWithoutDiacritics(med.getActiveIngredient());

            int score = 0;
            for (String term : targetTerms) {
                if (containsPhrase(indNoDia, term)) {
                    score += 3;
                }
                if (containsPhrase(nameNoDia, term)) {
                    score += 2;
                }
                if (containsPhrase(ingNoDia, term)) {
                    score += 2;
                }
            }

            if (score > 0) {
                // Ưu tiên thuốc OTC khi không yêu cầu ETC cụ thể
                int otcBonus = "OTC".equalsIgnoreCase(med.getPrescriptionType()) ? 1 : 0;
                scored.add(new ScoredMedicine(med, score, otcBonus));
            }
        }

        scored.sort(Comparator
                .comparingInt(ScoredMedicine::score).reversed()
                .thenComparing(Comparator.comparingInt(ScoredMedicine::otcBonus).reversed())
                .thenComparing(sm -> sm.medicine().getName() != null ? sm.medicine().getName() : ""));

        List<CachedMedicine> topMedicines = scored.stream()
                .limit(4)
                .map(ScoredMedicine::medicine)
                .toList();

        return new SymptomMatchResult(topMedicines, otcRequested, etcRequested, new ArrayList<>(matchedSymptoms));
    }

    private boolean containsPhrase(String text, String phrase) {
        if (text == null || text.isBlank() || phrase == null || phrase.isBlank()) {
            return false;
        }
        String pattern = "(^|\\b)" + Pattern.quote(phrase) + "(\\b|$)";
        return Pattern.compile(pattern).matcher(text).find();
    }

    private record ScoredMedicine(CachedMedicine medicine, int score, int otcBonus) {}
}
