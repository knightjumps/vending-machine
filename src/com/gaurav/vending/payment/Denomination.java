package com.gaurav.vending.payment;

/**
 * Values in paise, avoiding floating point money.
 */
public enum Denomination {
    FIFTY_PAISE(50), ONE_RUPEE(100), TWO_RUPEES(200), FIVE_RUPEES(500), TEN_RUPEES(1_000), TWENTY_RUPEES(2_000);
    private final long value;

    Denomination(long value) {
        this.value = value;
    }

    public long valueInPaise() {
        return value;
    }
}
