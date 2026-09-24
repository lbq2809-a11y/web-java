package com.pharmacy.auth.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
public class User extends PanacheEntity {

    @Column(unique = true, nullable = false)
    public String email;

    @Column(nullable = false)
    public String password;

    public String fullName;

    public String phone;

    @Enumerated(EnumType.STRING)
    public Role role = Role.CUSTOMER;

    public boolean active = true;

    public String faceEmbedding;

    public LocalDateTime createdAt = LocalDateTime.now();
}