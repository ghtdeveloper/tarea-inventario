package com.store.inventory;

import com.store.inventory.api.InventoryService;
import com.store.inventory.api.StockAlertListener;
import com.store.inventory.impl.InventoryServiceImpl;
import java.time.Clock;

public final class Inventory {

    private Inventory() {
    }

    public static InventoryService create(Clock clock, StockAlertListener alertListener) {
        return new InventoryServiceImpl(clock, alertListener);
    }
}
