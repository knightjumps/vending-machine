# Vending Machine LLD

An original Java design for a single physical, cash-based vending machine.

```text
UI / REST controller
        |
        v
VendingMachine (one fair lock per physical machine)
  |          |                 |                 |
  v          v                 v                 v
State      Inventory        CashDrawer      ProductDispenser
objects      -> Slot          -> exact          -> hardware adapter
             -> Product        change
```

| Package | Purpose | Why it is separate |
|---|---|---|
| `domain` | Product, Slot, Transaction | Product catalogue data differs from physical stock and a purchase session. |
| `inventory` | Slot layout/lookup | Keeps inventory navigation out of payment workflow. |
| `payment` | Denominations and cash reserve | Exact physical change is a distinct concern. |
| `state` | Idle, collecting payment, dispensing behavior | Removes a fragile state `if/else` chain. |
| `hardware` | Product-dispensing adapter | Makes domain code testable without real motors/sensors. |
| `service` | Transaction coordinator and UI result | Coordinates dependencies without owning their detailed logic. |

## Key design choices

- All money is `long` paise, never `double`; this prevents floating-point currency bugs.
- A `Slot` has capacity and may stock only one product type. Quantity is decremented only after the dispenser reports success.
- `CashDrawer` finds a bounded exact combination from the denominations actually available. Numeric overpayment alone does not guarantee physical change can be returned.
- Operators load a starting cash float with `loadChangeFloat` before customer transactions begin.
- `Transaction` remembers exact inserted notes, so a refund can return the customer’s actual cash.
- The machine reserves change before dispensing, and releases it before refunding if delivery fails.
- A fair `ReentrantLock` serializes one physical machine. A fleet should create/load a machine by `machineId`; a global Java Singleton is not correct for multiple machines or multiple JVMs.

## State machine

```text
Idle --insertCash--> CollectingPayment --select/sufficient--> Dispensing
  ^                       |                                     |
  +-- completed/refund ---+-- cancel, underpay, no change -------+
```

For a synchronous local adapter, `DispensingState` is brief. With real hardware it stays active until a product-drop sensor acknowledgement arrives, rejecting competing inputs in the meantime.

## Run

```bash
javac -d out $(find src -name '*.java')
java -cp out Main
```

## Extensions

Add a `PaymentMethod` strategy for card/UPI, persist transaction events for power-loss recovery, and add maintenance/out-of-service states.
