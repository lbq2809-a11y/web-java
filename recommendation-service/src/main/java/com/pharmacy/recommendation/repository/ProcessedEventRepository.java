package com.pharmacy.recommendation.repository;

import com.pharmacy.recommendation.document.ProcessedEventDocument;
import io.quarkus.mongodb.panache.PanacheMongoRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Instant;
import java.util.Optional;

@ApplicationScoped
public class ProcessedEventRepository implements PanacheMongoRepository<ProcessedEventDocument> {

    public Optional<ProcessedEventDocument> findByEventId(String eventId) {
        return find("eventId", eventId).firstResultOptional();
    }

    public boolean isEventProcessed(String eventId) {
        return count("eventId", eventId) > 0;
    }

    public void recordEvent(String eventId, String eventType) {
        ProcessedEventDocument doc = new ProcessedEventDocument();
        doc.eventId = eventId;
        doc.eventType = eventType;
        doc.processedAt = Instant.now();
        persist(doc);
    }
}

