package com.pharmacy.medicine.dto;

import java.time.Instant;

/**
 * DTO trả thông tin đánh giá thuốc về cho client.
 */
public class ReviewResponse {
    public String id;
    public String medicineId;
    public String userId;
    public String userFullName;
    public int rating;
    public String comment;
    public Instant createdAt;
    public Instant updatedAt;
}

