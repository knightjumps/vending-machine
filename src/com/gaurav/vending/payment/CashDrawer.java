package com.gaurav.vending.payment;

import java.util.*;

/**
 * Physical cash reserve plus exact bounded change calculation.
 */
public final class CashDrawer {
    private final Map<Denomination, Integer> counts = new EnumMap<>(Denomination.class);

    public CashDrawer() {
        for (Denomination d : Denomination.values()) counts.put(d, 0);
    }

    public void deposit(List<Denomination> cash) {
        cash.forEach(d -> counts.merge(d, 1, Integer::sum));
    }

    public boolean removeExactNotes(List<Denomination> cash) {
        Map<Denomination, Integer> requested = new EnumMap<>(Denomination.class);
        cash.forEach(d -> requested.merge(d, 1, Integer::sum));
        if (requested.entrySet().stream().anyMatch(e -> counts.get(e.getKey()) < e.getValue())) return false;
        requested.forEach((d, n) -> counts.merge(d, -n, Integer::sum));
        return true;
    }

    /**
     * Returns empty when the drawer cannot physically make the requested change.
     */
    public Optional<List<Denomination>> reserveChange(long amount) {
        if (amount < 0) throw new IllegalArgumentException();
        if (amount == 0) return Optional.of(List.of());
        List<Denomination> available = new ArrayList<>();
        counts.forEach((d, n) -> {
            for (int i = 0; i < n; i++) available.add(d);
        });
        available.sort(Comparator.comparingLong(Denomination::valueInPaise).reversed());
        List<Denomination> chosen = new ArrayList<>();
        if (!find(available, 0, amount, chosen)) return Optional.empty();
        chosen.forEach(d -> counts.merge(d, -1, Integer::sum));
        return Optional.of(List.copyOf(chosen));
    }

    private boolean find(List<Denomination> a, int i, long remaining, List<Denomination> chosen) {
        if (remaining == 0) return true;
        if (remaining < 0 || i == a.size()) return false;
        Denomination d = a.get(i);
        if (d.valueInPaise() <= remaining) {
            chosen.add(d);
            if (find(a, i + 1, remaining - d.valueInPaise(), chosen)) return true;
            chosen.removeLast();
        }
        return find(a, i + 1, remaining, chosen);
    }

    public List<Denomination> collectAll() {
        List<Denomination> out = new ArrayList<>();
        counts.forEach((d, n) -> {
            for (int i = 0; i < n; i++) out.add(d);
            counts.put(d, 0);
        });
        return List.copyOf(out);
    }
}
