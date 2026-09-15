package com.gaurav.vending.state;

import com.gaurav.vending.payment.Denomination;
import com.gaurav.vending.service.*;

import java.util.List;

/**
 * Same customer event, different legal response by phase.
 */
public interface MachineState {
    MachineResponse insertCash(VendingMachine m, List<Denomination> cash);

    MachineResponse selectProduct(VendingMachine m, String slot);

    MachineResponse cancel(VendingMachine m);
}
