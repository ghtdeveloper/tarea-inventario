package com.store.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.store.inventory.api.InsufficientStockException;
import com.store.inventory.api.InventoryService;
import com.store.inventory.api.OrderLimitExceededException;
import com.store.inventory.api.ProductCategory;
import com.store.inventory.api.Reservation;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ReservationServiceTest {

    private AdjustableClock clock;
    private List<String> stockAlerts;
    private InventoryService service;

    @BeforeEach
    void setUp() {
        clock = new AdjustableClock(Instant.parse("2025-01-01T00:00:00Z"));
        stockAlerts = new ArrayList<>();
        service = Inventory.create(clock, (sku, available) -> stockAlerts.add(sku + ":" + available));
    }

    @Test
    void expiredReservationReleasesUnits() {
        service.registerProduct("SKU-1", ProductCategory.STANDARD);
        service.addStock("SKU-1", 10);
        service.reserve("ORDER-1", "SKU-1", 4);

        assertEquals(6, service.available("SKU-1"));
        clock.advance(Duration.ofMinutes(16));
        assertEquals(10, service.available("SKU-1"));
    }

    @Test
    void cannotConfirmExpiredReservation() {
        service.registerProduct("SKU-1", ProductCategory.STANDARD);
        service.addStock("SKU-1", 10);
        service.reserve("ORDER-1", "SKU-1", 3);

        clock.advance(Duration.ofMinutes(16));

        assertThrows(IllegalStateException.class, () -> service.confirm("ORDER-1"));
    }

    @Test
    void preOrderReservationLasts24Hours() {
        service.registerProduct("SKU-1", ProductCategory.PRE_ORDER);
        service.addStock("SKU-1", 10);
        service.reserve("ORDER-1", "SKU-1", 5);

        clock.advance(Duration.ofHours(23));
        assertEquals(5, service.available("SKU-1"));

        clock.advance(Duration.ofHours(2));
        assertEquals(10, service.available("SKU-1"));
    }

    @Test
    void flashSaleReservationLasts5Minutes() {
        service.registerProduct("SKU-1", ProductCategory.FLASH_SALE);
        service.addStock("SKU-1", 10);
        service.reserve("ORDER-1", "SKU-1", 2);

        clock.advance(Duration.ofMinutes(4));
        assertEquals(8, service.available("SKU-1"));

        clock.advance(Duration.ofMinutes(2));
        assertEquals(10, service.available("SKU-1"));
    }

    @Test
    void flashSaleEnforcesOrderLimit() {
        service.registerProduct("SKU-1", ProductCategory.FLASH_SALE);
        service.addStock("SKU-1", 10);

        assertThrows(OrderLimitExceededException.class,
                () -> service.reserve("ORDER-1", "SKU-1", 3));
    }

    @Test
    void flashSaleAllowsWithinLimit() {
        service.registerProduct("SKU-1", ProductCategory.FLASH_SALE);
        service.addStock("SKU-1", 10);

        Reservation r = service.reserve("ORDER-1", "SKU-1", 2);

        assertEquals(2, r.quantity());
        assertEquals(8, service.available("SKU-1"));
    }

    @Test
    void idempotentReserveReturnsSameReservation() {
        service.registerProduct("SKU-1", ProductCategory.STANDARD);
        service.addStock("SKU-1", 10);

        Reservation first = service.reserve("ORDER-1", "SKU-1", 3);
        Reservation second = service.reserve("ORDER-1", "SKU-1", 3);

        assertEquals(first, second);
        assertEquals(7, service.available("SKU-1"));
    }

    @Test
    void idempotentReserveAfterExpirationCreatesNew() {
        service.registerProduct("SKU-1", ProductCategory.STANDARD);
        service.addStock("SKU-1", 10);

        Reservation first = service.reserve("ORDER-1", "SKU-1", 3);
        clock.advance(Duration.ofMinutes(16));

        Reservation second = service.reserve("ORDER-1", "SKU-1", 3);

        assertNotEquals(first.expiresAt(), second.expiresAt());
        assertEquals(7, service.available("SKU-1"));
    }

    @Test
    void lowStockAlertFires() {
        service.registerProduct("SKU-1", ProductCategory.STANDARD);
        service.addStock("SKU-1", 8);

        service.reserve("ORDER-1", "SKU-1", 3);

        assertEquals(List.of("SKU-1:5"), stockAlerts);
    }

    @Test
    void lowStockAlertNotRepeatedWithoutRestock() {
        service.registerProduct("SKU-1", ProductCategory.STANDARD);
        service.addStock("SKU-1", 8);

        service.reserve("ORDER-1", "SKU-1", 3);
        service.reserve("ORDER-2", "SKU-1", 1);

        assertEquals(1, stockAlerts.size());
    }

    @Test
    void lowStockAlertFiresAgainAfterRestock() {
        service.registerProduct("SKU-1", ProductCategory.STANDARD);
        service.addStock("SKU-1", 8);

        service.reserve("ORDER-1", "SKU-1", 4);
        assertEquals(1, stockAlerts.size());

        service.addStock("SKU-1", 5);
        service.reserve("ORDER-2", "SKU-1", 5);

        assertEquals(2, stockAlerts.size());
    }

    @Test
    void lowStockAlertDoesNotFireAboveThreshold() {
        service.registerProduct("SKU-1", ProductCategory.STANDARD);
        service.addStock("SKU-1", 20);

        service.reserve("ORDER-1", "SKU-1", 5);

        assertEquals(0, stockAlerts.size());
    }

    @Test
    void unknownProductReturnsZeroAvailable() {
        assertEquals(0, service.available("UNKNOWN"));
    }

    @Test
    void reserveUnknownProductThrowsInsufficientStock() {
        assertThrows(InsufficientStockException.class,
                () -> service.reserve("ORDER-1", "UNKNOWN", 1));
    }

    @Test
    void confirmNonExistentOrderThrows() {
        assertThrows(IllegalStateException.class,
                () -> service.confirm("NONEXISTENT"));
    }

    @Test
    void confirmAlreadyConfirmedThrows() {
        service.registerProduct("SKU-1", ProductCategory.STANDARD);
        service.addStock("SKU-1", 10);
        service.reserve("ORDER-1", "SKU-1", 3);
        service.confirm("ORDER-1");

        assertThrows(IllegalStateException.class,
                () -> service.confirm("ORDER-1"));
    }

    @Test
    void reserveZeroQuantityThrows() {
        service.registerProduct("SKU-1", ProductCategory.STANDARD);
        service.addStock("SKU-1", 10);

        assertThrows(IllegalArgumentException.class,
                () -> service.reserve("ORDER-1", "SKU-1", 0));
    }

    @Test
    void reserveNegativeQuantityThrows() {
        service.registerProduct("SKU-1", ProductCategory.STANDARD);

        assertThrows(IllegalArgumentException.class,
                () -> service.reserve("ORDER-1", "SKU-1", -1));
    }

    @Test
    void addStockZeroThrows() {
        service.registerProduct("SKU-1", ProductCategory.STANDARD);

        assertThrows(IllegalArgumentException.class,
                () -> service.addStock("SKU-1", 0));
    }

    @Test
    void addStockNegativeThrows() {
        service.registerProduct("SKU-1", ProductCategory.STANDARD);

        assertThrows(IllegalArgumentException.class,
                () -> service.addStock("SKU-1", -5));
    }

    @Test
    void addStockUnregisteredProductThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> service.addStock("UNREGISTERED", 10));
    }

    @Test
    void multipleReservationsSameProduct() {
        service.registerProduct("SKU-1", ProductCategory.STANDARD);
        service.addStock("SKU-1", 10);

        service.reserve("ORDER-1", "SKU-1", 3);
        service.reserve("ORDER-2", "SKU-1", 4);

        assertEquals(3, service.available("SKU-1"));
    }

    @Test
    void confirmedUnitsNotReleasedOnExpiration() {
        service.registerProduct("SKU-1", ProductCategory.STANDARD);
        service.addStock("SKU-1", 10);

        service.reserve("ORDER-1", "SKU-1", 3);
        service.confirm("ORDER-1");

        clock.advance(Duration.ofMinutes(20));

        assertEquals(7, service.available("SKU-1"));
    }

    @Test
    void reservationExpiresAtCorrectTimePerCategory() {
        service.registerProduct("STD", ProductCategory.STANDARD);
        service.registerProduct("PRE", ProductCategory.PRE_ORDER);
        service.registerProduct("FLASH", ProductCategory.FLASH_SALE);
        service.addStock("STD", 10);
        service.addStock("PRE", 10);
        service.addStock("FLASH", 10);

        Instant now = clock.instant();
        Reservation std = service.reserve("O1", "STD", 1);
        Reservation pre = service.reserve("O2", "PRE", 1);
        Reservation flash = service.reserve("O3", "FLASH", 1);

        assertEquals(now.plus(Duration.ofMinutes(15)), std.expiresAt());
        assertEquals(now.plus(Duration.ofHours(24)), pre.expiresAt());
        assertEquals(now.plus(Duration.ofMinutes(5)), flash.expiresAt());
    }

    @Test
    void expiredReservationFreesStockForOthers() {
        service.registerProduct("SKU-1", ProductCategory.FLASH_SALE);
        service.addStock("SKU-1", 2);

        service.reserve("ORDER-1", "SKU-1", 2);
        assertEquals(0, service.available("SKU-1"));

        clock.advance(Duration.ofMinutes(6));

        Reservation r = service.reserve("ORDER-2", "SKU-1", 2);
        assertEquals(2, r.quantity());
        assertEquals(0, service.available("SKU-1"));
    }

    @Test
    void mixedConfirmedAndExpiredReservations() {
        service.registerProduct("SKU-1", ProductCategory.STANDARD);
        service.addStock("SKU-1", 10);

        service.reserve("ORDER-1", "SKU-1", 3);
        service.confirm("ORDER-1");

        service.reserve("ORDER-2", "SKU-1", 4);

        clock.advance(Duration.ofMinutes(16));

        assertEquals(7, service.available("SKU-1"));
    }

    static class AdjustableClock extends Clock {
        private Instant now;

        AdjustableClock(Instant start) {
            this.now = start;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }
}
