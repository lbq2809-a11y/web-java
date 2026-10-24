package com.pharmacy.consultation.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Phân loại ý định người dùng dựa trên từ điển đồng nghĩa tải từ synonyms.json.
 */
@ApplicationScoped
public class IntentClassifier {

    private static final Logger LOG = Logger.getLogger(IntentClassifier.class);

    private final Map<String, List<String>> synonymsMap = new HashMap<>();

    @Inject
    ObjectMapper objectMapper;

    @Inject
    TextNormalizer textNormalizer;

    void onStart(@Observes StartupEvent ev) {
        loadSynonyms();
    }

    public synchronized void loadSynonyms() {
        try (InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream("synonyms.json")) {
            if (is == null) {
                LOG.error("Không tìm thấy file tài nguyên synonyms.json");
                return;
            }
            Map<String, List<String>> loaded = objectMapper.readValue(is, new TypeReference<>() {});
            synonymsMap.clear();
            synonymsMap.putAll(loaded);
            LOG.infof("Đã nạp thành công %d nhóm ý định từ synonyms.json", synonymsMap.size());
        } catch (Exception e) {
            LOG.error("Lỗi khi nạp synonyms.json", e);
        }
    }

    /**
     * Phân loại ý định từ câu hỏi của người dùng.
     * @return Một trong các ý định: THANH_PHAN, CHI_DINH, CHONG_CHI_DINH, CACH_DUNG, LOAI_THUOC, GIA hoặc KHAC.
     */
    public String classify(String question) {
        if (question == null || question.isBlank()) {
            return "KHAC";
        }

        String normalized = textNormalizer.normalize(question);
        String noDiacritics = textNormalizer.normalizeWithoutDiacritics(question);

        // Thứ tự ưu tiên kiểm tra
        List<String> intentPriority = List.of(
                "THANH_PHAN",
                "CHI_DINH",
                "CHONG_CHI_DINH",
                "CACH_DUNG",
                "LOAI_THUOC",
                "GIA"
        );

        for (String intent : intentPriority) {
            List<String> keywords = synonymsMap.getOrDefault(intent, Collections.emptyList());
            for (String kw : keywords) {
                String normKw = textNormalizer.normalize(kw);
                String noDiaKw = textNormalizer.normalizeWithoutDiacritics(kw);
                if (normalized.contains(normKw) || noDiacritics.contains(noDiaKw)) {
                    return intent;
                }
            }
        }

        return "KHAC";
    }
}

