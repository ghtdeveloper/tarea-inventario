package com.store.inventory.impl;

import com.store.inventory.api.ProductCategory;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

final class ProductStock {

    private final String sku;
    private final ProductCategory category;
    private int totalStock;
    private boolean lowStockAlerted;
    private final List<ReservationEntry> reservations = new ArrayList<>();

    ProductStock(String sku, ProductCategory category) {
        this.sku = sku;
        this.category = category;
    }

    String sku() { return sku; }
    ProductCategory category() { return category; }
    boolean isLowStockAlerted() { return lowStockAlerted; }

    void addStock(int quantity) {
        totalStock += quantity;
        lowStockAlerted = false;
    }

    void markLowStockAlerted() {
        lowStockAlerted = true;
    }

    void addReservation(ReservationEntry entry) {
        reservations.add(entry);
    }

    int computeAvailable(Instant now) {
        int committed = 0;
        for (ReservationEntry r : reservations) {
            if (r.isConfirmed() || r.isActive(now)) {
                committed += r.quantity();
            }
        }
        return totalStock - committed;
    }
}
