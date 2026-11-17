
package com.pharmacy.notification.repository;

import com.pharmacy.notification.document.TemplateDocument;
import io.quarkus.mongodb.panache.PanacheMongoRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Optional;

@ApplicationScoped
public class TemplateRepository
        implements PanacheMongoRepository<TemplateDocument> {

    /**
     * Tìm mẫu email theo mã template.
     */
    public Optional<TemplateDocument> findByTemplateKey(String key) {
        return find("templateKey", key)
                .firstResultOptional();
    }

    /**
     * Tìm mẫu email đang được kích hoạt.
     */
    public Optional<TemplateDocument> findActiveTemplate(String key) {
        return find("templateKey = ?1 and active = true", key)
                .firstResultOptional();
    }
}
