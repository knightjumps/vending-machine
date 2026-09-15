package com.gaurav.vending.domain;

import com.gaurav.vending.payment.Denomination;

import java.util.*;

/**
 * One customer session; enables exact refund and future auditing.
 */
public final class Transaction {
    private final UUID id = UUID.randomUUID();
    private final List<Denomination> cash = new ArrayList<>();
    private TransactionStatus status = TransactionStatus.COLLECTING_PAYMENT;

    public UUID id() {
        return id;
    }

    public TransactionStatus status() {
        return status;
    }

    public List<Denomination> insertedCash() {
        return List.copyOf(cash);
    }

    public long amount() {
        return cash.stream().mapToLong(Denomination::valueInPaise).sum();
    }

    public void addCash(List<Denomination> notes) {
        cash.addAll(notes);
    }

    public void transitionTo(TransactionStatus s) {
        status = s;
    }
}
