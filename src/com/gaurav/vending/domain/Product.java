package com.gaurav.vending.domain;

import java.util.Objects;

/**
 * Immutable catalogue data. Physical stock belongs to Slot.
 */
public record Product(String id, String name, long priceInPaise, ProductCategory category) {
    public Product {
        if (id == null || id.isBlank() || name == null || name.isBlank() || priceInPaise <= 0)
            throw new IllegalArgumentException("Product needs id, name, and positive price");
        Objects.requireNonNull(category);
    }
}
