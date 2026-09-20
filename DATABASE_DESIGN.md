# Vending Machine Database Design

This document extends the in-memory Low-Level Design into a durable database design. The correct architecture depends on whether the system controls one standalone machine or manages a fleet of machines.

## Questions to Clarify in an Interview

Before drawing tables, clarify the operating model:

1. Is this a single standalone machine or a centrally managed fleet?
2. Must the machine continue selling products while offline?
3. Are payments cash-only, or must card and UPI payments be recorded?
4. Is exact physical change required?
5. Do operators need inventory, cash-reconciliation, and maintenance reports?
6. Must incomplete transactions recover after a process or power failure?

The answers determine whether an embedded database is sufficient or whether local storage and a central relational database are both required.

---

# Part I: Single Vending Machine

## Architecture

A standalone machine can use an embedded relational database such as SQLite. The database runs on the device, requires no network connection, and persists inventory, cash, and active transactions across application restarts.

```text
Touchscreen / Buttons
        |
        v
VendingMachine application
   |         |          |
   v         v          v
Inventory  Payment    Hardware adapters
        \     |       /
         Local SQLite
```

The hardware workflow should not depend on a continuously available remote server. A network outage must not leave a customer unable to finish or recover a local cash transaction.

## Essential Local Tables

### `machine`

Stores the identity and durable administrative condition of the physical machine.

| Column | Suggested type | Description |
|---|---|---|
| `machine_id` | TEXT / UUID | Primary key |
| `serial_number` | TEXT | Manufacturer-provided unique identifier |
| `status` | TEXT | `ACTIVE`, `MAINTENANCE`, or `OUT_OF_SERVICE` |
| `software_version` | TEXT | Currently installed application version |
| `version` | INTEGER | Optimistic-locking version |
| `created_at` | TIMESTAMP | Creation time |
| `updated_at` | TIMESTAMP | Last modification time |

Short-lived object states such as `CollectingPaymentState` normally belong to the active transaction. Long-lived administrative state belongs to the machine record.

### `product`

Stores reusable product catalogue information.

| Column | Suggested type | Description |
|---|---|---|
| `product_id` | TEXT / UUID | Primary key |
| `sku` | TEXT | Business product identifier |
| `name` | TEXT | Display name |
| `category` | TEXT | Beverage, snack, chocolate, or another category |
| `price_minor` | INTEGER / BIGINT | Price in the smallest currency unit |
| `currency` | CHAR(3) | ISO code such as `INR` |
| `active` | BOOLEAN | Whether the product may still be sold |
| `created_at` | TIMESTAMP | Creation time |
| `updated_at` | TIMESTAMP | Last modification time |

Money should never be stored as `FLOAT` or `DOUBLE`. For example, `price_minor = 150` with `currency = INR` means ₹1.50.

### `machine_slot`

Represents a physical rack or slot. Product catalogue information and physical stock are separate because the same product can appear in several slots.

| Column | Suggested type | Description |
|---|---|---|
| `slot_id` | TEXT / UUID | Primary key |
| `machine_id` | TEXT / UUID | Owning machine |
| `slot_code` | TEXT | Customer-facing code such as `A1` |
| `product_id` | TEXT / UUID, nullable | Product currently loaded |
| `capacity` | INTEGER | Maximum supported units |
| `quantity` | INTEGER | Current physical units |
| `reserved_quantity` | INTEGER | Units committed to unfinished transactions |
| `version` | INTEGER | Optimistic-locking version |
| `updated_at` | TIMESTAMP | Last stock update |

Recommended constraints:

```sql
UNIQUE (machine_id, slot_code)

CHECK (capacity > 0)

CHECK (
    quantity >= 0
    AND reserved_quantity >= 0
    AND reserved_quantity <= quantity
    AND quantity <= capacity
)
```

### `purchase_transaction`

Stores one purchase attempt, including unfinished and failed purchases.

| Column | Suggested type | Description |
|---|---|---|
| `transaction_id` | TEXT / UUID | Primary key |
| `machine_id` | TEXT / UUID | Machine processing the purchase |
| `slot_id` | TEXT / UUID, nullable | Selected physical slot |
| `product_id` | TEXT / UUID, nullable | Product selected at purchase time |
| `status` | TEXT | Durable transaction state |
| `inserted_amount_minor` | INTEGER / BIGINT | Total customer payment |
| `price_minor` | INTEGER / BIGINT | Price charged at purchase time |
| `change_amount_minor` | INTEGER / BIGINT | Change returned |
| `currency` | CHAR(3) | Transaction currency |
| `failure_code` | TEXT, nullable | Machine-readable failure category |
| `failure_message` | TEXT, nullable | Human-readable failure explanation |
| `started_at` | TIMESTAMP | Transaction start |
| `completed_at` | TIMESTAMP, nullable | Terminal time |
| `version` | INTEGER | Optimistic-locking version |

Useful statuses include:

```text
COLLECTING_PAYMENT
PRODUCT_SELECTED
CHANGE_RESERVED
DISPENSING
COMPLETED
REFUND_PENDING
REFUNDED
FAILED
```

The transaction stores a price snapshot even though `product` has a price. If the catalogue price changes tomorrow, yesterday's receipt must still show what the customer actually paid.

### `transaction_cash`

Records exact denominations inserted, refunded, or returned as change.

| Column | Suggested type | Description |
|---|---|---|
| `transaction_cash_id` | TEXT / UUID | Primary key |
| `transaction_id` | TEXT / UUID | Related purchase |
| `direction` | TEXT | `INSERTED`, `CHANGE`, or `REFUND` |
| `denomination_minor` | INTEGER / BIGINT | Denomination value |
| `quantity` | INTEGER | Number of coins or notes |

The numeric total alone cannot describe a physical refund. ₹2 may have been inserted as one ₹2 coin, two ₹1 coins, or four ₹0.50 coins.

### `machine_cash_inventory`

Stores the physical cash available for refunds and change.

| Column | Suggested type | Description |
|---|---|---|
| `machine_id` | TEXT / UUID | Machine identifier |
| `denomination_minor` | INTEGER / BIGINT | Coin or note denomination |
| `available_count` | INTEGER | Units physically available |
| `reserved_count` | INTEGER | Units reserved for unfinished purchases |
| `version` | INTEGER | Optimistic-locking version |
| `updated_at` | TIMESTAMP | Last update |

Recommended primary key and constraints:

```sql
PRIMARY KEY (machine_id, denomination_minor)

CHECK (available_count >= 0)
CHECK (reserved_count >= 0)
CHECK (reserved_count <= available_count)
```

### `inventory_movement`

Provides an append-only audit history for restocking, dispensing, removal, and manual corrections.

| Column | Suggested type | Description |
|---|---|---|
| `movement_id` | TEXT / UUID | Primary key |
| `machine_id` | TEXT / UUID | Machine identifier |
| `slot_id` | TEXT / UUID | Affected slot |
| `transaction_id` | TEXT / UUID, nullable | Related purchase |
| `movement_type` | TEXT | `RESTOCK`, `DISPENSE`, `REMOVE`, or `ADJUSTMENT` |
| `quantity_delta` | INTEGER | Positive or negative quantity change |
| `occurred_at` | TIMESTAMP | Event time |
| `operator_id` | TEXT / UUID, nullable | Operator responsible for the change |

`machine_slot.quantity` supports fast reads. This ledger explains how the current quantity was reached.

### `cash_movement`

Provides an append-only physical-cash audit trail.

| Column | Suggested type | Description |
|---|---|---|
| `movement_id` | TEXT / UUID | Primary key |
| `machine_id` | TEXT / UUID | Machine identifier |
| `transaction_id` | TEXT / UUID, nullable | Related purchase |
| `denomination_minor` | INTEGER / BIGINT | Coin or note denomination |
| `quantity_delta` | INTEGER | Physical count added or removed |
| `movement_type` | TEXT | Insert, change, refund, load, collect, or adjustment |
| `occurred_at` | TIMESTAMP | Event time |
| `operator_id` | TEXT / UUID, nullable | Operator responsible for the movement |

Useful movement types are:

```text
CUSTOMER_INSERT
CHANGE_DISPENSE
CUSTOMER_REFUND
OPERATOR_LOAD
OPERATOR_COLLECT
MANUAL_ADJUSTMENT
```

This table allows expected software cash to be reconciled against the cash physically found by an operator.

## Single-Machine Purchase Workflow

Do not hold a database transaction open while waiting for a motor and product-drop sensor. Hardware may take seconds or time out entirely.

### 1. Reserve stock and change

In a short database transaction:

1. Read the slot and validate `quantity - reserved_quantity > 0`.
2. Increment `reserved_quantity` by one.
3. Calculate an exact change combination from available denominations.
4. Increment `reserved_count` for those denominations.
5. Save the purchase as `DISPENSING`.
6. Commit.

### 2. Operate hardware

Outside the database transaction:

```text
Start slot motor
Wait for product-drop sensor acknowledgement
```

### 3A. Successful dispense

In another short database transaction:

```text
slot.quantity          = slot.quantity - 1
slot.reserved_quantity = slot.reserved_quantity - 1
transaction.status     = COMPLETED
```

Commit inventory movements, cash movements, and denomination-level change records in the same database transaction.

### 3B. Failed dispense

Release inventory and change reservations. Set the purchase to `REFUND_PENDING` or `FAILED`, return the customer's money, and then mark it `REFUNDED`.

After a restart, unfinished work can be found with:

```sql
SELECT *
FROM purchase_transaction
WHERE status IN ('DISPENSING', 'REFUND_PENDING');
```

The application then reconciles persisted state with sensors and operator policy.

## Single-Machine Concurrency

A physical machine normally supports one active customer session. Use one application lock for that machine and database constraints as a second safety layer.

Optimistic locking can be expressed as:

```sql
UPDATE machine_slot
SET reserved_quantity = reserved_quantity + 1,
    version = version + 1
WHERE slot_id = :slotId
  AND version = :expectedVersion
  AND quantity - reserved_quantity > 0;
```

If zero rows are updated, the version changed or no unreserved stock remains.

---

# Part II: Fleet of Vending Machines

## Architecture

A fleet requires two durable layers:

1. Local embedded storage on every machine for offline operation.
2. A central relational database, such as PostgreSQL, for fleet-wide operations.

```text
Physical machine
├── Local SQLite
│   ├── current inventory
│   ├── cash counts
│   ├── active transaction
│   └── unsynchronized events
│
└── synchronization worker
            |
            v
Central API and PostgreSQL
├── machine registry and locations
├── consolidated inventory
├── transaction history
├── operator and maintenance data
├── reconciliation
└── analytics and alerts
```

The local machine remains the authority for immediate physical actions. The central database provides operational visibility, reporting, configuration, and fleet coordination.

## Fleet-Specific Tables

The central database retains the core tables described for a single machine but includes `machine_id` in all machine-owned records. It also adds the following tables.

### `location`

| Column | Suggested type | Description |
|---|---|---|
| `location_id` | UUID | Primary key |
| `name` | VARCHAR | Human-readable location |
| `address` | TEXT | Installation address |
| `timezone` | VARCHAR | Local timezone |
| `active` | BOOLEAN | Whether the location is operational |

### `operator`

| Column | Suggested type | Description |
|---|---|---|
| `operator_id` | UUID | Primary key |
| `name` | VARCHAR | Operator name |
| `employee_reference` | VARCHAR | External workforce identifier |
| `active` | BOOLEAN | Whether the operator may perform work |

Authentication credentials should generally remain in a dedicated identity provider rather than this domain table.

### `maintenance_record`

| Column | Suggested type | Description |
|---|---|---|
| `maintenance_id` | UUID | Primary key |
| `machine_id` | UUID | Serviced machine |
| `operator_id` | UUID, nullable | Assigned operator |
| `issue_type` | VARCHAR | Motor, sensor, network, cash, etc. |
| `status` | VARCHAR | Open, assigned, resolved, closed |
| `description` | TEXT | Problem details |
| `opened_at` | TIMESTAMP | Issue creation time |
| `resolved_at` | TIMESTAMP, nullable | Resolution time |

### `machine_heartbeat`

| Column | Suggested type | Description |
|---|---|---|
| `heartbeat_id` | UUID | Primary key |
| `machine_id` | UUID | Reporting machine |
| `recorded_at` | TIMESTAMP | Heartbeat time |
| `software_version` | VARCHAR | Running software |
| `network_status` | VARCHAR | Connectivity condition |
| `device_health` | JSON / JSONB | Sensor and hardware health summary |

For very large fleets, raw heartbeat data may be moved to a time-series system, while `machine.last_heartbeat_at` remains in PostgreSQL for fast status queries.

### `outbox_event`

Stores events that must be synchronized reliably. This table exists locally and may also exist centrally for downstream publication.

| Column | Suggested type | Description |
|---|---|---|
| `event_id` | UUID | Unique event and idempotency key |
| `machine_id` | UUID | Event source |
| `event_type` | VARCHAR | Purchase, restock, cash collection, etc. |
| `aggregate_id` | UUID / VARCHAR | Related transaction, slot, or machine |
| `payload` | JSON / JSONB | Serialized event data |
| `created_at` | TIMESTAMP | Event creation time |
| `published_at` | TIMESTAMP, nullable | Successful synchronization time |
| `retry_count` | INTEGER | Number of delivery attempts |

The business update and outbox row must be committed in the same local database transaction. A background worker sends unpublished events later. The central API treats `event_id` as an idempotency key so retries cannot duplicate purchases or stock movements.

### `payment_attempt`

Useful when a fleet supports card, UPI, wallet, or other external payments.

| Column | Suggested type | Description |
|---|---|---|
| `payment_attempt_id` | UUID | Primary key |
| `transaction_id` | UUID | Related purchase |
| `method` | VARCHAR | Cash, card, UPI, wallet |
| `provider_reference` | VARCHAR, nullable | External provider identifier |
| `amount_minor` | BIGINT | Attempted amount |
| `currency` | CHAR(3) | Currency |
| `status` | VARCHAR | Initiated, authorized, captured, failed, refunded |
| `idempotency_key` | VARCHAR | Retry-safe identifier |
| `created_at` | TIMESTAMP | Creation time |
| `updated_at` | TIMESTAMP | Last status change |

Do not store raw card numbers, PINs, CVVs, or other sensitive payment credentials in these tables.

## Fleet Synchronization Rules

### Offline-first behavior

The machine records purchases locally first. When connectivity returns, it uploads outbox events to the central API.

### Idempotency

Each event and external payment request must have a stable idempotency key. Retrying the same request must return the original result rather than create another transaction.

### Conflict handling

Physical stock changes performed by a machine are authoritative for that machine. Central configuration changes should carry versions. If a price or slot configuration update conflicts with a newer local version, the API should reject or reconcile it explicitly rather than silently overwrite physical state.

### Central transaction boundaries

The central database must not hold locks while waiting for machines to respond. Commands such as `RESTOCK_REQUESTED` or `DISABLE_MACHINE` should be stored as durable commands/events and acknowledged asynchronously.

## Recommended Indexes

```sql
CREATE UNIQUE INDEX ux_machine_slot_code
    ON machine_slot(machine_id, slot_code);

CREATE INDEX ix_transaction_machine_started
    ON purchase_transaction(machine_id, started_at);

CREATE INDEX ix_transaction_status
    ON purchase_transaction(status);

CREATE INDEX ix_inventory_movement_slot_time
    ON inventory_movement(slot_id, occurred_at);

CREATE INDEX ix_cash_movement_machine_time
    ON cash_movement(machine_id, occurred_at);

CREATE INDEX ix_outbox_unpublished
    ON outbox_event(published_at, created_at);

CREATE INDEX ix_heartbeat_machine_time
    ON machine_heartbeat(machine_id, recorded_at);
```

Indexes improve reads but increase storage and write cost. Add them for real query patterns, not automatically for every column.

## Common Design Mistakes

- Storing machine-specific quantity in the global `product` table.
- Using floating-point types for money.
- Recording only total cash while ignoring denominations.
- Updating current quantity without maintaining an audit ledger.
- Marking a purchase complete before the delivery sensor confirms it.
- Holding a database transaction open while operating physical hardware.
- Ignoring restart recovery for `DISPENSING` and `REFUND_PENDING` purchases.
- Assuming a Java Singleton coordinates different JVMs or machines.
- Requiring continuous network connectivity for a physical cash transaction.
- Sending retryable events without idempotency keys.

## Interview Summary

For a single machine, use an embedded relational database for product configuration, physical slot inventory, cash denominations, transaction recovery, and audit movements. Serialize the active customer session and keep hardware calls outside database transactions.

For a fleet, retain the local database so every machine remains operational offline, then synchronize idempotent outbox events to a central PostgreSQL database. The central system adds machine locations, operators, maintenance, heartbeats, fleet-wide history, reconciliation, and analytics.

---

# Complete Table Inventory

| Table | Single machine | Fleet central DB | Main use case |
|---|:---:|:---:|---|
| `machine` | Yes | Yes | Machine identity, administrative status, version, and latest operational metadata |
| `product` | Yes | Yes | Product catalogue, category, current price, and active status |
| `machine_slot` | Yes | Yes | Physical slot configuration, product assignment, capacity, stock, and reservations |
| `purchase_transaction` | Yes | Yes | Durable purchase lifecycle, monetary snapshots, failures, refunds, and restart recovery |
| `transaction_cash` | Yes | Yes | Exact inserted, change, and refund denominations for a purchase |
| `machine_cash_inventory` | Yes | Yes | Available and reserved physical coins/notes per denomination |
| `inventory_movement` | Yes | Yes | Append-only restock, dispense, removal, and adjustment audit trail |
| `cash_movement` | Yes | Yes | Append-only customer/operator physical-cash audit and reconciliation |
| `outbox_event` | Recommended | Recommended | Reliable offline synchronization and downstream event publication |
| `location` | No | Yes | Installation sites, addresses, timezones, and operational status |
| `operator` | Optional | Yes | Operator identity used by restocking, collection, and maintenance records |
| `maintenance_record` | Optional | Yes | Hardware/software issue lifecycle and operator servicing history |
| `machine_heartbeat` | No | Yes | Connectivity, deployed version, and device health monitoring |
| `payment_attempt` | If digital payments exist | If digital payments exist | External cashless authorization, capture, failure, refund, and idempotency tracking |
