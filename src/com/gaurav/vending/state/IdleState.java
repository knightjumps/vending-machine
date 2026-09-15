package com.gaurav.vending.state;

import com.gaurav.vending.payment.Denomination;
import com.gaurav.vending.service.*;

import java.util.List;

public final class IdleState implements MachineState {
    public MachineResponse insertCash(VendingMachine m, List<Denomination> c) {
        return m.startPayment(c);
    }

    public MachineResponse selectProduct(VendingMachine m, String s) {
        return MachineResponse.message("Insert cash before selecting a product.");
    }

    public MachineResponse cancel(VendingMachine m) {
        return MachineResponse.message("No active payment to refund.");
    }
}
