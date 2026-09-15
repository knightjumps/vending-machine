package com.gaurav.vending.hardware;

import com.gaurav.vending.domain.Slot;

/**
 * Deterministic test/demo adapter.
 */
public final class InMemoryProductDispenser implements ProductDispenser {
    public boolean dispense(Slot slot) {
        return !slot.isEmpty();
    }
}
