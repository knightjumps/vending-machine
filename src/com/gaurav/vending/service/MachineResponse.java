package com.gaurav.vending.service;

import com.gaurav.vending.domain.Product;
import com.gaurav.vending.payment.Denomination;

import java.util.*;

/**
 * Immutable UI/API boundary result.
 */
public record MachineResponse(String message, Optional<Product> product, List<Denomination> returnedCash) {
    public MachineResponse {
        product = product == null ? Optional.empty() : product;
        returnedCash = List.copyOf(returnedCash == null ? List.of() : returnedCash);
    }

    public static MachineResponse message(String m) {
        return new MachineResponse(m, Optional.empty(), List.of());
    }
}
