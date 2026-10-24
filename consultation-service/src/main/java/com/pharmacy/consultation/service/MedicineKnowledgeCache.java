package com.pharmacy.consultation.service;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Cache lưu trữ thông tin các thuốc trong RAM để phục vụ tra cứu nhanh và nhận diện thuốc.
 */
@ApplicationScoped
public class MedicineKnowledgeCache {

    private final List<CachedMedicine> cachedMedicines = new CopyOnWriteArrayList<>();
    private final Map<String, CachedMedicine> idMap = new ConcurrentHashMap<>();

    public synchronized void update(List<CachedMedicine> medicines) {
        cachedMedicines.clear();
        idMap.clear();
        if (medicines != null) {
            cachedMedicines.addAll(medicines);
            for (CachedMedicine m : medicines) {
                if (m.getMedicineId() != null) {
                    idMap.put(m.getMedicineId(), m);
                }
            }
        }
    }

    public List<CachedMedicine> getAll() {
        return new ArrayList<>(cachedMedicines);
    }

    public Optional<CachedMedicine> getById(String id) {
        if (id == null) return Optional.empty();
        return Optional.ofNullable(idMap.get(id));
    }

    public int size() {
        return cachedMedicines.size();
    }
}

