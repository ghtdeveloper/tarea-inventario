package com.store.inventory.impl;

import com.store.inventory.api.ProductCategory;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import java.util.OptionalInt;

final class CategoryPolicy {

    private static final Map<ProductCategory, CategoryPolicy> POLICIES = new EnumMap<>(ProductCategory.class);

    static {
        POLICIES.put(ProductCategory.STANDARD, new CategoryPolicy(Duration.ofMinutes(15), OptionalInt.empty()));
        POLICIES.put(ProductCategory.PRE_ORDER, new CategoryPolicy(Duration.ofHours(24), OptionalInt.empty()));
        POLICIES.put(ProductCategory.FLASH_SALE, new CategoryPolicy(Duration.ofMinutes(5), OptionalInt.of(2)));
    }

    private final Duration paymentTimeout;
    private final OptionalInt orderLimit;

    private CategoryPolicy(Duration paymentTimeout, OptionalInt orderLimit) {
        this.paymentTimeout = paymentTimeout;
        this.orderLimit = orderLimit;
    }

    Duration paymentTimeout() {
        return paymentTimeout;
    }

    OptionalInt orderLimit() {
        return orderLimit;
    }

    static CategoryPolicy forCategory(ProductCategory category) {
        CategoryPolicy policy = POLICIES.get(category);
        if (policy == null) {
            throw new IllegalArgumentException("No policy defined for category: " + category);
        }
        return policy;
    }
}
