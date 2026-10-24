package com.pharmacy.consultation.dto;

import com.pharmacy.consultation.entity.KnowledgeSource;

import java.util.ArrayList;
import java.util.List;

/**
 * Phản hồi câu trả lời tư vấn thuốc từ trợ lý AI.
 */
public class AskResponse {

    private String conversationId;
    private String answer;
    private int tier;
    private String intent;
    private List<KnowledgeSource> sources = new ArrayList<>();
    private List<SuggestedMedicineDto> suggestedMedicines = new ArrayList<>();
    private String disclaimer;

    public AskResponse() {
    }

    public AskResponse(String conversationId, String answer, int tier, String intent,
                       List<KnowledgeSource> sources, List<SuggestedMedicineDto> suggestedMedicines,
                       String disclaimer) {
        this.conversationId = conversationId;
        this.answer = answer;
        this.tier = tier;
        this.intent = intent;
        this.sources = sources != null ? sources : new ArrayList<>();
        this.suggestedMedicines = suggestedMedicines != null ? suggestedMedicines : new ArrayList<>();
        this.disclaimer = disclaimer;
    }

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }

    public int getTier() {
        return tier;
    }

    public void setTier(int tier) {
        this.tier = tier;
    }

    public String getIntent() {
        return intent;
    }

    public void setIntent(String intent) {
        this.intent = intent;
    }

    public List<KnowledgeSource> getSources() {
        return sources;
    }

    public void setSources(List<KnowledgeSource> sources) {
        this.sources = sources;
    }

    public List<SuggestedMedicineDto> getSuggestedMedicines() {
        return suggestedMedicines;
    }

    public void setSuggestedMedicines(List<SuggestedMedicineDto> suggestedMedicines) {
        this.suggestedMedicines = suggestedMedicines;
    }

    public String getDisclaimer() {
        return disclaimer;
    }

    public void setDisclaimer(String disclaimer) {
        this.disclaimer = disclaimer;
    }
}

