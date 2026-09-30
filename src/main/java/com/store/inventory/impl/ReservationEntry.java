package com.store.inventory.impl;

import com.store.inventory.api.Reservation;
import java.time.Instant;

final class ReservationEntry {

    private final String orderId;
    private final String sku;
    private final int quantity;
    private final Instant expiresAt;
    private boolean confirmed;

    ReservationEntry(String orderId, String sku, int quantity, Instant expiresAt) {
        this.orderId = orderId;
        this.sku = sku;
        this.quantity = quantity;
        this.expiresAt = expiresAt;
    }

    String orderId() { return orderId; }
    String sku() { return sku; }
    int quantity() { return quantity; }
    boolean isConfirmed() { return confirmed; }

    void confirm() {
        confirmed = true;
    }

    boolean isExpired(Instant now) {
        return !confirmed && now.isAfter(expiresAt);
    }

    boolean isActive(Instant now) {
        return !confirmed && !now.isAfter(expiresAt);
    }

    Reservation toReservation() {
        return new Reservation(orderId, sku, quantity, expiresAt);
    }
}
