package com.pharmacy.consultation.client;

import java.util.List;

/**
 * Interface giao tiếp với Large Language Model (LLM) - chừa sẵn cho Tầng 2.
 */
public interface LlmClient {

    String generateAnswer(String question, List<String> contextChunks, String conversationHistory);

    String streamAnswer(String question, List<String> contextChunks, String conversationHistory);
}

