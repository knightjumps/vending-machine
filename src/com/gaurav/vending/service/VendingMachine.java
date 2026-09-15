package com.gaurav.vending.service;

import com.gaurav.vending.domain.*;
import com.gaurav.vending.hardware.*;
import com.gaurav.vending.inventory.Inventory;
import com.gaurav.vending.payment.*;
import com.gaurav.vending.state.*;

import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * Coordinates one physical machine without implementing inventory, cash, or hardware itself.
 */
public final class VendingMachine {
    private final String machineId;
    private final Inventory inventory = new Inventory();
    private final CashDrawer drawer = new CashDrawer();
    private final ProductDispenser dispenser;
    private final ReentrantLock lock = new ReentrantLock(true);
    private final MachineState idle = new IdleState(), collecting = new CollectingPaymentState(), dispensing = new DispensingState();
    private MachineState state = idle;
    private Transaction active;

    public VendingMachine(String machineId) {
        this(machineId, new InMemoryProductDispenser());
    }

    public VendingMachine(String machineId, ProductDispenser dispenser) {
        if (machineId == null || machineId.isBlank()) throw new IllegalArgumentException("Machine ID required");
        this.machineId = machineId;
        this.dispenser = Objects.requireNonNull(dispenser);
    }

    public String machineId() {
        return machineId;
    }

    public MachineResponse insertCash(List<Denomination> cash) {
        return guarded(() -> state.insertCash(this, cash));
    }

    public MachineResponse selectProduct(String slot) {
        return guarded(() -> state.selectProduct(this, slot));
    }

    public MachineResponse cancel() {
        return guarded(() -> state.cancel(this));
    }

    /**
     * Operator operations: the same lock prevents a restock/collection race with purchase.
     */
    public void addSlot(Slot slot) {
        guarded(() -> {
            inventory.addSlot(slot);
            return null;
        });
    }

    public void restock(String slot, Product product, int units) {
        guarded(() -> {
            inventory.find(slot).orElseThrow(() -> new IllegalArgumentException("Unknown slot: " + slot)).restock(product, units);
            return null;
        });
    }

    /**
     * Operator loads a starting float so the machine can make change.
     */
    public void loadChangeFloat(List<Denomination> cash) {
        guarded(() -> {
            validate(cash);
            drawer.deposit(cash);
            return null;
        });
    }

    public List<Denomination> collectCash() {
        return guarded(drawer::collectAll);
    }

    public List<Slot> inventorySnapshot() {
        return guarded(() -> List.copyOf(inventory.all()));
    }

    // Called by state implementations; public because state is in a sibling package.
    public MachineResponse startPayment(List<Denomination> cash) {
        validate(cash);
        active = new Transaction();
        accept(cash);
        state = collecting;
        return balanceResponse();
    }

    public MachineResponse addPayment(List<Denomination> cash) {
        validate(cash);
        accept(cash);
        return balanceResponse();
    }

    public MachineResponse purchase(String slotCode) {
        Slot slot = inventory.find(slotCode).orElse(null);
        if (slot == null || slot.isEmpty()) return MachineResponse.message("Selected slot is unavailable or empty.");
        Product product = slot.product().orElseThrow();
        long paid = active.amount();
        if (paid < product.priceInPaise()) return refund("Insufficient payment; payment refunded.");
        Optional<List<Denomination>> change = drawer.reserveChange(paid - product.priceInPaise());
        if (change.isEmpty()) return refund("Exact change is unavailable; payment refunded.");
        active.transitionTo(TransactionStatus.DISPENSING);
        state = dispensing;
        if (!dispenser.dispense(slot)) {
            drawer.deposit(change.get());
            return refund("Product dispensing failed; payment refunded.");
        }
        Product delivered = slot.removeOne();
        active.transitionTo(TransactionStatus.COMPLETED);
        reset();
        return new MachineResponse("Purchase complete.", Optional.of(delivered), change.get());
    }

    public MachineResponse refund(String reason) {
        List<Denomination> refund = active.insertedCash();
        if (!drawer.removeExactNotes(refund))
            throw new IllegalStateException("Accepted notes cannot be returned; operator intervention required");
        active.transitionTo(TransactionStatus.REFUNDED);
        reset();
        return new MachineResponse(reason, Optional.empty(), refund);
    }

    private void accept(List<Denomination> cash) {
        drawer.deposit(cash);
        active.addCash(cash);
    }

    private void validate(List<Denomination> cash) {
        if (cash == null || cash.isEmpty() || cash.stream().anyMatch(Objects::isNull))
            throw new IllegalArgumentException("Insert at least one valid denomination");
    }

    private MachineResponse balanceResponse() {
        return MachineResponse.message("Cash accepted. Balance: " + active.amount() + " paise.");
    }

    private void reset() {
        active = null;
        state = idle;
    }

    private <T> T guarded(Supplier<T> action) {
        lock.lock();
        try {
            return action.get();
        } finally {
            lock.unlock();
        }
    }
}
