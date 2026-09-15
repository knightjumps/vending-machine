import com.gaurav.vending.domain.Product;
import com.gaurav.vending.domain.ProductCategory;
import com.gaurav.vending.domain.Slot;
import com.gaurav.vending.payment.Denomination;
import com.gaurav.vending.service.MachineResponse;
import com.gaurav.vending.service.VendingMachine;

import java.util.List;

public class Main {
    public static void main(String[] args) {
        VendingMachine machine = configuredMachine("VM-MUM-001");
        scenario("1. Product selected before payment", () -> print(machine.selectProduct("A1")));
        scenario("2. Invalid and empty slots", () -> {
            print(machine.insertCash(List.of(Denomination.ONE_RUPEE)));
            print(machine.selectProduct("Z9"));
            print(machine.selectProduct("C1"));
            print(machine.cancel());
        });
        scenario("3. Underpayment refunds the original cash", () -> {
            print(machine.insertCash(List.of(Denomination.ONE_RUPEE)));
            print(machine.selectProduct("A1"));
        });
        scenario("4. Exact payment", () -> {
            print(machine.insertCash(List.of(Denomination.ONE_RUPEE, Denomination.FIFTY_PAISE)));
            print(machine.selectProduct("A1"));
        });
        scenario("5. Multiple cash insertions", () -> {
            print(machine.insertCash(List.of(Denomination.ONE_RUPEE)));
            print(machine.insertCash(List.of(Denomination.ONE_RUPEE)));
            print(machine.selectProduct("A1"));
        });
        scenario("6. Customer cancellation", () -> {
            print(machine.insertCash(List.of(Denomination.TWO_RUPEES)));
            print(machine.cancel());
        });
        scenario("7. Overpayment and exact change", () -> {
            print(machine.insertCash(List.of(Denomination.TWO_RUPEES)));
            print(machine.selectProduct("A1"));
        });
        scenario("8. No physical change available", () -> {
            VendingMachine noChange = new VendingMachine("VM-NO-CHANGE");
            noChange.addSlot(new Slot("A1", 2));
            noChange.restock("A1", new Product("cola", "Cola", 150, ProductCategory.BEVERAGE), 1);
            print(noChange.insertCash(List.of(Denomination.TWO_RUPEES)));
            print(noChange.selectProduct("A1"));
        });
        scenario("9. Hardware failure compensates with refund", () -> {
            VendingMachine broken = new VendingMachine("VM-BROKEN", slot -> false);
            broken.addSlot(new Slot("A1", 2));
            broken.restock("A1", new Product("cola", "Cola", 150, ProductCategory.BEVERAGE), 1);
            broken.loadChangeFloat(List.of(Denomination.FIFTY_PAISE));
            print(broken.insertCash(List.of(Denomination.TWO_RUPEES)));
            print(broken.selectProduct("A1"));
            System.out.println("  Remaining stock: " + broken.inventorySnapshot().getFirst().quantity());
        });
        scenario("10. Operator cash collection", () -> System.out.println("  Collected: " + machine.collectCash()));
        scenario("11. Inventory validation", () -> {
            try {
                machine.restock("A1", new Product("water", "Water", 100, ProductCategory.BEVERAGE), 1);
            } catch (IllegalStateException e) {
                System.out.println("  Rejected mixed-product restock: " + e.getMessage());
            }
            try {
                machine.restock("A1", new Product("cola", "Cola", 150, ProductCategory.BEVERAGE), 99);
            } catch (IllegalArgumentException e) {
                System.out.println("  Rejected over-capacity restock: " + e.getMessage());
            }
        });
    }

    private static VendingMachine configuredMachine(String id) {
        VendingMachine machine = new VendingMachine(id);
        machine.addSlot(new Slot("A1", 10));
        machine.addSlot(new Slot("C1", 10));
        machine.restock("A1", new Product("cola", "Cola", 150, ProductCategory.BEVERAGE), 5);
        machine.loadChangeFloat(List.of(Denomination.FIFTY_PAISE, Denomination.FIFTY_PAISE));
        return machine;
    }

    private static void scenario(String name, Runnable action) {
        System.out.println("\n=== " + name + " ===");
        action.run();
    }

    private static void print(MachineResponse response) {
        System.out.println("  " + response.message());
        response.product().ifPresent(product -> System.out.println("  Product: " + product.name()));
        if (!response.returnedCash().isEmpty()) System.out.println("  Cash returned: " + response.returnedCash());
    }
}
