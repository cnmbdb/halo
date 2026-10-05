package local.halo.commerce;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.ReactiveExtensionClient;

class OrderExpiryServiceTest {
    private final Instant now = Instant.parse("2026-10-02T12:00:00Z");
    @Test void expiresPendingOrderThenReleasesItsReservations() {
        var order = order(now.minusSeconds(901));
        var client = mock(ReactiveExtensionClient.class);
        var inventory = mock(InventoryService.class);
        when(client.list(eq(CommerceOrder.class), any(), any())).thenAnswer(call -> Flux.just(order)
            .filter(call.getArgument(1)));
        when(client.fetch(CommerceOrder.class, "order-123")).thenReturn(Mono.just(order));
        when(client.update(any(CommerceOrder.class))).thenAnswer(call -> Mono.just(call.getArgument(0)));
        when(inventory.release("book", "order-123")).thenReturn(Mono.just(new Product()));
        var service = new OrderExpiryService(client, inventory, Clock.fixed(now, ZoneOffset.UTC));
        service.expireOrders().block();
        assertEquals(CommerceOrder.State.EXPIRED, order.getSpec().state());
        verify(inventory).release("book", "order-123");
    }
    @Test void doesNotExpireOrdersWithTimeRemaining() {
        var order = order(now.plusSeconds(60));
        var client = mock(ReactiveExtensionClient.class);
        when(client.list(eq(CommerceOrder.class), any(), any())).thenAnswer(call -> Flux.just(order)
            .filter(call.getArgument(1)));
        new OrderExpiryService(client, mock(InventoryService.class),
            Clock.fixed(now, ZoneOffset.UTC)).expireOrders().block();
        verify(client, never()).update(any(CommerceOrder.class));
    }
    private CommerceOrder order(Instant expiresAt) {
        var order = new CommerceOrder(); var metadata = new Metadata(); metadata.setName("order-123"); order.setMetadata(metadata);
        var created = now.minusSeconds(1800);
        var line = new CheckoutPricing.Line("book", "Book", Product.Type.PHYSICAL, "sku", "Standard", 1000, 1, 1000);
        order.setSpec(new CommerceOrder.Spec("alice", List.of(line), 1000, "CNY",
            new CommerceOrder.ShippingAddress("Alice", "1234567", "Main Street"),
            CommerceOrder.State.AWAITING_PAYMENT, created, expiresAt, null));
        return order;
    }
}
