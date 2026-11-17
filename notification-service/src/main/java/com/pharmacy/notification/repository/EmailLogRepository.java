package com.pharmacy.notification.repository;

import com.pharmacy.notification.document.EmailLogDocument;
import io.quarkus.mongodb.panache.PanacheMongoRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class EmailLogRepository
        implements PanacheMongoRepository<EmailLogDocument> {

    public List<EmailLogDocument> findByRecipient(String recipient) {
        return find("recipient", recipient).list();
    }

    public List<EmailLogDocument> findByStatus(String status) {
        return find("status", status).list();
    }

    public List<EmailLogDocument> findPendingEmails() {
        return find("status", "PENDING").list();
    }

    public Optional<EmailLogDocument> findByEventId(String eventId) {
        if (eventId == null || eventId.isBlank()) {
            return Optional.empty();
        }

        return find("eventId", eventId).firstResultOptional();
    }
}
