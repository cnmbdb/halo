package local.halo.commerce;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class CommerceOrderTest {
    private final Instant now = Instant.parse("2026-10-02T00:00:00Z");
    @Test void validatesOrderNamesAndPhysicalShipping() {
        assertTrue(CommerceOrder.validName("order-abc123"));
        assertFalse(CommerceOrder.validName("../admin"));
        assertThrows(IllegalArgumentException.class, () -> spec(Product.Type.PHYSICAL, null));
        assertNotNull(spec(Product.Type.DIGITAL, null));
    }
    @Test void keepsImmutableServerPricedLineSnapshots() {
        var line = line();
        var spec = new CommerceOrder.Spec("alice", List.of(line), 1234, "CNY", null,
            CommerceOrder.State.AWAITING_PAYMENT, now, now.plusSeconds(900), null);
        assertThrows(UnsupportedOperationException.class, () -> spec.lines().clear());
        assertEquals(1234, spec.withState(CommerceOrder.State.CANCELLED, null).totalMinor());
        assertThrows(IllegalArgumentException.class, () -> new CommerceOrder.Spec("alice", List.of(line), 1,
            "CNY", null, CommerceOrder.State.AWAITING_PAYMENT, now, now.plusSeconds(900), null));
    }
    @Test void requiresBoundedExpirationAndValidCurrency() {
        var line = line();
        assertThrows(IllegalArgumentException.class, () -> new CommerceOrder.Spec("alice", List.of(line), 1234,
            "USD", null, CommerceOrder.State.AWAITING_PAYMENT, now, now.plusSeconds(900), null));
        assertThrows(IllegalArgumentException.class, () -> new CommerceOrder.Spec("alice", List.of(line), 1234,
            "CNY", null, CommerceOrder.State.AWAITING_PAYMENT, now, now.plusSeconds(86401), null));
    }
    @Test void mixedFulfillmentKeepsOrderOpenUntilPhysicalDeliveryCompletes() {
        var physical = new CheckoutPricing.Line("box", "Box", Product.Type.PHYSICAL, "sku", "One", 100, 1, 100);
        var digital = new CheckoutPricing.Line("ebook", "Ebook", Product.Type.DIGITAL, "key", "License", 200, 1, 200);
        var awaiting = new CommerceOrder.Spec("alice", List.of(physical, digital), 300, "CNY",
            new CommerceOrder.ShippingAddress("Alice", "1234567", "Main Street"), CommerceOrder.State.AWAITING_PAYMENT,
            now, now.plusSeconds(900), null);
        var paid = awaiting.withState(CommerceOrder.State.PAID, "trade-1");
        assertEquals(CommerceOrder.State.FULFILLING, paid.state());
        assertEquals(CommerceOrder.FulfillmentState.PROCESSING, paid.fulfillment().getFirst().state());
        assertEquals(CommerceOrder.FulfillmentState.DIGITAL_DELIVERED, paid.fulfillment().get(1).state());
        assertThrows(IllegalStateException.class, () -> paid.withShipment(
            new CommerceOrder.Shipment("Carrier", "TRACK-123", true), now.plusSeconds(1)));
        var shipped = paid.withShipment(new CommerceOrder.Shipment("Carrier", "TRACK-123", false), now.plusSeconds(1));
        assertEquals(CommerceOrder.State.FULFILLING, shipped.state());
        assertEquals(CommerceOrder.FulfillmentState.SHIPPED, shipped.fulfillment().getFirst().state());
        var delivered = shipped.withShipment(new CommerceOrder.Shipment("Carrier", "TRACK-123", true), now.plusSeconds(2));
        assertEquals(CommerceOrder.State.COMPLETED, delivered.state());
        assertEquals(CommerceOrder.FulfillmentState.DELIVERED, delivered.fulfillment().getFirst().state());
    }
    private CommerceOrder.Spec spec(Product.Type type, CommerceOrder.ShippingAddress address) {
        var original = line();
        var line = new CheckoutPricing.Line(original.productName(), original.productTitle(), type,
            original.skuId(), original.skuTitle(), original.unitPriceMinor(), original.quantity(), original.totalMinor());
        return new CommerceOrder.Spec("alice", List.of(line), 1234, "CNY", address,
            CommerceOrder.State.AWAITING_PAYMENT, now, now.plusSeconds(900), null);
    }
    private CheckoutPricing.Line line() {
        return new CheckoutPricing.Line("book", "Book", Product.Type.DIGITAL,
            "sku", "Standard", 1234, 1, 1234);
    }
}
