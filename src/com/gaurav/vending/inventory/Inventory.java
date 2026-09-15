package com.gaurav.vending.inventory;

import com.gaurav.vending.domain.Slot;

import java.util.*;

/**
 * Slot lookup/layout; transaction synchronization belongs to VendingMachine.
 */
public final class Inventory {
    private final Map<String, Slot> slots = new LinkedHashMap<>();

    public void addSlot(Slot s) {
        if (slots.putIfAbsent(s.code(), s) != null) throw new IllegalArgumentException("Slot exists: " + s.code());
    }

    public Optional<Slot> find(String code) {
        return Optional.ofNullable(slots.get(code));
    }

    public Collection<Slot> all() {
        return List.copyOf(slots.values());
    }
}
