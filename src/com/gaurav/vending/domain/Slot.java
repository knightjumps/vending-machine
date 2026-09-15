package com.gaurav.vending.domain;

import java.util.*;

/**
 * A physical column; it holds one product type at a time.
 */
public final class Slot {
    private final String code;
    private final int capacity;
    private Product product;
    private int quantity;

    public Slot(String code, int capacity) {
        if (code == null || code.isBlank() || capacity <= 0)
            throw new IllegalArgumentException("Slot code and positive capacity required");
        this.code = code;
        this.capacity = capacity;
    }

    public String code() {
        return code;
    }

    public boolean isEmpty() {
        return quantity == 0;
    }

    public int quantity() {
        return quantity;
    }

    public Optional<Product> product() {
        return Optional.ofNullable(product);
    }

    public void restock(Product p, int units) {
        Objects.requireNonNull(p);
        if (units <= 0 || quantity + units > capacity) throw new IllegalArgumentException("Quantity exceeds capacity");
        if (product != null && !product.id().equals(p.id()) && quantity > 0)
            throw new IllegalStateException("Cannot mix products in a slot");
        product = p;
        quantity += units;
    }

    /**
     * Commit stock decrement only after hardware confirms delivery.
     */
    public Product removeOne() {
        if (isEmpty()) throw new IllegalStateException("Slot empty");
        quantity--;
        return product;
    }
}
