package run.halo.shop;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.shop.extension.Product;
import run.halo.shop.extension.ShopOrder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShopServiceTest {
    @Mock ReactiveExtensionClient client;

    @Test
    void orderUsesServerPriceAndSnapshotsProduct() {
        var product = new Product();
        var metadata = new Metadata();
        metadata.setName("tea");
        product.setMetadata(metadata);
        var spec = new Product.Spec();
        spec.setTitle("Tea");
        spec.setPriceCents(1299);
        spec.setStock(5);
        spec.setPublished(true);
        product.setSpec(spec);
        when(client.fetch(Product.class, "tea")).thenReturn(Mono.just(product));
        when(client.create(any(ShopOrder.class))).thenAnswer(invocation -> {
            ShopOrder order = invocation.getArgument(0);
            order.getMetadata().setName("shop-order-test");
            return Mono.just(order);
        });

        var request = new ShopService.OrderRequest("Alice", "123456", "Somewhere",
            List.of(new ShopService.OrderItemRequest("tea", 2)));
        var receipt = new ShopService(client).placeOrder(request).block();

        assertEquals(2598, receipt.totalCents());
        var captured = ArgumentCaptor.forClass(ShopOrder.class);
        verify(client).create(captured.capture());
        assertEquals(1299, captured.getValue().getSpec().getItems().getFirst().getUnitPriceCents());
        assertEquals("PENDING_CONFIRMATION", captured.getValue().getSpec().getStatus());
    }

    @Test
    void rejectsInvalidQuantityBeforeReadingProducts() {
        var request = new ShopService.OrderRequest("Alice", "123456", "Somewhere",
            List.of(new ShopService.OrderItemRequest("tea", 0)));
        assertThrows(ResponseStatusException.class, () -> new ShopService(client).placeOrder(request));
    }
}
