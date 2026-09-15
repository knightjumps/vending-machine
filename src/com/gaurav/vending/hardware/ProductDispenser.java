package com.gaurav.vending.hardware;

import com.gaurav.vending.domain.Slot;

/**
 * Production adapter drives a motor and checks the delivery sensor.
 */
public interface ProductDispenser {
    boolean dispense(Slot slot);
}
