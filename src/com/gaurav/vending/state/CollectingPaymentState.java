package com.gaurav.vending.state;

import com.gaurav.vending.payment.Denomination;
import com.gaurav.vending.service.*;

import java.util.List;

public final class CollectingPaymentState implements MachineState {
    public MachineResponse insertCash(VendingMachine m, List<Denomination> c) {
        return m.addPayment(c);
    }

    public MachineResponse selectProduct(VendingMachine m, String s) {
        return m.purchase(s);
    }

    public MachineResponse cancel(VendingMachine m) {
        return m.refund("Transaction cancelled.");
    }
}
