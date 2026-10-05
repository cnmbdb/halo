package local.halo.commerce;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import run.halo.app.extension.Metadata;

class InventoryAvailabilityTest {
    private final Instant now = Instant.parse("2026-10-02T00:00:00Z");
    @Test void onlyUnexpiredHeldReservationsReduceSellableStock() {
        var product = product();
        product.setStatus(new Product.StockStatus(Map.of(
            "held", reservation(3, now.plusSeconds(60), Product.ReservationState.HELD),
            "expired", reservation(4, now, Product.ReservationState.HELD),
            "released", reservation(2, now.plusSeconds(60), Product.ReservationState.RELEASED),
            "committed", reservation(1, now.plusSeconds(60), Product.ReservationState.COMMITTED))));
        assertEquals(7, new InventoryAvailability().available(product, "sku", now));
    }
    @Test void elapsedReservationNoLongerPreventsQuote() {
        var product = product();
        product.setStatus(new Product.StockStatus(Map.of("old", reservation(5, now.minusSeconds(1), Product.ReservationState.HELD))));
        var quote = new CheckoutPricing().quote(List.of(new CheckoutPricing.Selection("book", "sku", 5)),
            Map.of("book", product), now);
        assertEquals(500, quote.totalMinor());
    }
    @Test void activeReservationMakesQuoteRejectUnavailableUnits() {
        var product = product();
        product.setStatus(new Product.StockStatus(Map.of("held", reservation(9, now.plusSeconds(10), Product.ReservationState.HELD))));
        assertThrows(IllegalArgumentException.class, () -> new CheckoutPricing().quote(
            List.of(new CheckoutPricing.Selection("book", "sku", 2)), Map.of("book", product), now));
        assertEquals(1, new InventoryAvailability().available(product, "sku", now));
    }
    @Test void reservationSnapshotsAreImmutableAndValidateQuantities() {
        var input = new java.util.HashMap<>(Map.of("sku", 2));
        var reservation = reservation(input.get("sku"), now.plusSeconds(1), Product.ReservationState.HELD);
        input.put("sku", 1);
        assertEquals(2, reservation.quantities().get("sku"));
        assertThrows(UnsupportedOperationException.class, () -> reservation.quantities().put("sku", 4));
        assertThrows(IllegalArgumentException.class, () -> new Product.Reservation(Map.of("sku", 0), now,
            Product.ReservationState.HELD, now));
    }
    private Product.Reservation reservation(int quantity, Instant expiry, Product.ReservationState state) {
        return new Product.Reservation(Map.of("sku", quantity), expiry, state, now);
    }
    private Product product() {
        var product = new Product(); var metadata = new Metadata(); metadata.setName("book"); product.setMetadata(metadata);
        product.setSpec(new Product.Spec("Book", "book", Product.Type.PHYSICAL, Product.State.PUBLISHED,
            "", List.of(), List.of(), List.of(new Product.Sku("sku", "Standard", 100, 10)), false));
        return product;
    }
}
