package com.gaurav.vending.state;

import com.gaurav.vending.payment.Denomination;
import com.gaurav.vending.service.*;

import java.util.List;

/**
 * Remains useful for real asynchronous hardware.
 */
public final class DispensingState implements MachineState {
    private static final String WAIT = "Dispensing is in progress; please wait.";

    public MachineResponse insertCash(VendingMachine m, List<Denomination> c) {
        return MachineResponse.message(WAIT);
    }

    public MachineResponse selectProduct(VendingMachine m, String s) {
        return MachineResponse.message(WAIT);
    }

    public MachineResponse cancel(VendingMachine m) {
        return MachineResponse.message(WAIT);
    }
}
