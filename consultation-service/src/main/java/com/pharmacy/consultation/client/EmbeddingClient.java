package com.pharmacy.consultation.client;

import java.util.List;

/**
 * Interface giao tiếp với dịch vụ sinh vector nhúng (Embedding) - chừa sẵn cho Tầng 2.
 */
public interface EmbeddingClient {

    List<Double> embedText(String text);

    List<List<Double>> embedBatch(List<String> texts);
}

