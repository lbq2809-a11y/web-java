package com.pharmacy.medicine.repository;

import com.pharmacy.medicine.document.CategoryDocument;
import io.quarkus.mongodb.panache.PanacheMongoRepository;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import org.bson.types.ObjectId;

import java.util.List;
import java.util.Optional;

/**
 * Repository thao tác với collection "categories" trong MongoDB.
 */
@ApplicationScoped
public class CategoryRepository implements PanacheMongoRepository<CategoryDocument> {

    public Optional<CategoryDocument> findByCode(String code) {
        return find("code", code).firstResultOptional();
    }

    public Optional<CategoryDocument> findByObjectId(ObjectId id) {
        return findByIdOptional(id);
    }

    public List<CategoryDocument> listCategories(boolean includeInactive) {
        if (includeInactive) {
            return listAll(Sort.by("name").ascending());
        }
        return list("active", Sort.by("name").ascending(), true);
    }
}

