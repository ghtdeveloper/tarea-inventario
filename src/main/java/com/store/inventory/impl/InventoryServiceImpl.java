package com.store.inventory.impl;

import com.store.inventory.api.InsufficientStockException;
import com.store.inventory.api.InventoryService;
import com.store.inventory.api.OrderLimitExceededException;
import com.store.inventory.api.ProductCategory;
import com.store.inventory.api.Reservation;
import com.store.inventory.api.StockAlertListener;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class InventoryServiceImpl implements InventoryService {

    private static final int LOW_STOCK_THRESHOLD = 5;

    private final Clock clock;
    private final StockAlertListener alertListener;
    private final Map<String, ProductStock> products = new ConcurrentHashMap<>();
    private final Map<String, ReservationEntry> reservationsByOrder = new ConcurrentHashMap<>();

    public InventoryServiceImpl(Clock clock, StockAlertListener alertListener) {
        this.clock = clock;
        this.alertListener = alertListener;
    }

    @Override
    public void registerProduct(String sku, ProductCategory category) {
        products.putIfAbsent(sku, new ProductStock(sku, category));
    }

    @Override
    public void addStock(String sku, int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
        ProductStock product = requireRegistered(sku);
        synchronized (product) {
            product.addStock(quantity);
        }
    }

    @Override
    public Reservation reserve(String orderId, String sku, int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }

        ProductStock product = products.get(sku);

        if (product != null) {
            CategoryPolicy policy = CategoryPolicy.forCategory(product.category());
            policy.orderLimit().ifPresent(limit -> {
                if (quantity > limit) {
                    throw new OrderLimitExceededException(sku, quantity, limit);
                }
            });
        }

        if (product == null) {
            throw new InsufficientStockException(sku, quantity, 0);
        }

        synchronized (product) {
            Instant now = clock.instant();

            ReservationEntry existing = reservationsByOrder.get(orderId);
            if (existing != null && existing.isActive(now)) {
                return existing.toReservation();
            }

            int available = product.computeAvailable(now);
            if (available < quantity) {
                throw new InsufficientStockException(sku, quantity, available);
            }

            CategoryPolicy policy = CategoryPolicy.forCategory(product.category());
            Instant expiresAt = now.plus(policy.paymentTimeout());

            ReservationEntry entry = new ReservationEntry(orderId, sku, quantity, expiresAt);
            product.addReservation(entry);
            reservationsByOrder.put(orderId, entry);

            int availableAfter = product.computeAvailable(now);
            if (availableAfter <= LOW_STOCK_THRESHOLD && !product.isLowStockAlerted()) {
                product.markLowStockAlerted();
                alertListener.onLowStock(sku, availableAfter);
            }

            return entry.toReservation();
        }
    }

    @Override
    public void confirm(String orderId) {
        ReservationEntry entry = reservationsByOrder.get(orderId);
        if (entry == null) {
            throw new IllegalStateException("No active reservation for order " + orderId);
        }

        ProductStock product = products.get(entry.sku());
        synchronized (product) {
            Instant now = clock.instant();
            if (entry.isExpired(now) || entry.isConfirmed()) {
                throw new IllegalStateException("No active reservation for order " + orderId);
            }
            entry.confirm();
        }
    }

    @Override
    public int available(String sku) {
        ProductStock product = products.get(sku);
        if (product == null) return 0;
        synchronized (product) {
            return product.computeAvailable(clock.instant());
        }
    }

    private ProductStock requireRegistered(String sku) {
        ProductStock product = products.get(sku);
        if (product == null) {
            throw new IllegalArgumentException("Product not registered: " + sku);
        }
        return product;
    }
}
