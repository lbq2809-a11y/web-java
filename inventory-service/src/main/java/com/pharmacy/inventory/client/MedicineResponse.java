package com.pharmacy.inventory.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public class MedicineResponse {
    public String id;
    public String code;
    public String name;
    public String unit;
    public BigDecimal price;
    public boolean active;
}

