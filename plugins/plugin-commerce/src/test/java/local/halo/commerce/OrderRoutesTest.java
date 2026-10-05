package local.halo.commerce;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.theme.TemplateNameResolver;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;

class OrderRoutesTest {
    private ReactiveExtensionClient client;
    private WebTestClient http;
    private org.springframework.web.reactive.function.server.RouterFunction<org.springframework.web.reactive.function.server.ServerResponse> routes;
    private CommerceOrder order;
    private InventoryService inventory;
    @BeforeEach void setup() {
        client = mock(ReactiveExtensionClient.class);
        inventory = mock(InventoryService.class);
        routes = new OrderRoutes().commerceOrderRoutes(client, inventory, mock(TemplateNameResolver.class),
            new EpaySettings("", "", "", "", "", ""));
        http = WebTestClient.bindToRouterFunction(routes).build();
        order = new CommerceOrder(); var metadata = new Metadata(); metadata.setName("order-123"); order.setMetadata(metadata);
        var now = Instant.parse("2026-10-02T00:00:00Z");
        var line = new CheckoutPricing.Line("book", "Book", Product.Type.PHYSICAL, "sku", "Standard", 1000, 1, 1000);
        order.setSpec(new CommerceOrder.Spec("alice", List.of(line), 1000, "CNY",
            new CommerceOrder.ShippingAddress("Alice", "+1 555 123 4567", "10 Main Street"),
            CommerceOrder.State.AWAITING_PAYMENT, now, now.plusSeconds(900), null));
        when(client.fetch(CommerceOrder.class, "order-123")).thenReturn(Mono.just(order));
        when(client.update(any(CommerceOrder.class))).thenAnswer(call -> Mono.just(call.getArgument(0)));
        when(client.list(eq(CommerceOrder.class), any(), any())).thenAnswer(call -> Flux.just(order)
            .filter(call.getArgument(1)));
    }
    @Test void createsPhysicalOrderFromServerPriceAndReservesInventory() {
        var product = product("book", Product.Type.PHYSICAL, 1999, 4);
        when(client.fetch(Product.class, "book")).thenReturn(Mono.just(product));
        when(client.create(any(CommerceOrder.class))).thenAnswer(call -> Mono.just(call.getArgument(0)));
        var inventory = (InventoryService) org.mockito.Mockito.mock(InventoryService.class);
        when(inventory.reserve(eq("book"), anyString(), anyMap(), any())).thenAnswer(call -> Mono.just(product));
        var orderRoutes = new OrderRoutes().commerceOrderRoutes(client, inventory, mock(TemplateNameResolver.class),
            new EpaySettings("", "", "", "", "", ""));
        var body = "{\"selections\":[{\"productName\":\"book\",\"skuId\":\"sku\",\"quantity\":2}],\"totalMinor\":1,\"shippingAddress\":{\"recipient\":\"Alice\",\"phone\":\"+1 555 123 4567\",\"address\":\"10 Main Street\"}}";
        asUser(orderRoutes, "alice").post().uri("/shop/api/orders").contentType(MediaType.APPLICATION_JSON).bodyValue(body).exchange()
            .expectStatus().isCreated().expectBody().jsonPath("$.state").isEqualTo("AWAITING_PAYMENT")
            .jsonPath("$.totalMinor").isEqualTo(3998);
        var orderCaptor = org.mockito.ArgumentCaptor.forClass(CommerceOrder.class);
        verify(client).create(orderCaptor.capture());
        assertEquals("alice", orderCaptor.getValue().getSpec().buyer());
        assertEquals(1999, orderCaptor.getValue().getSpec().lines().getFirst().unitPriceMinor());
        verify(inventory).reserve(eq("book"), anyString(), eq(Map.of("sku", 2)), any());
    }
    @Test void guestPurchaseRequiresProductPermissionAndKeepsOrdersSessionScoped() {
        var product = product("book", Product.Type.DIGITAL, 1000, 1);
        when(client.fetch(Product.class, "book")).thenReturn(Mono.just(product));
        when(client.create(any(CommerceOrder.class))).thenAnswer(call -> Mono.just(call.getArgument(0)));
        when(inventory.reserve(eq("book"), anyString(), anyMap(), any())).thenReturn(Mono.just(product));
        var body = "{\"selections\":[{\"productName\":\"book\",\"skuId\":\"sku\",\"quantity\":1}]}";
        http.post().uri("/shop/api/orders").contentType(MediaType.APPLICATION_JSON).bodyValue(body)
            .exchange().expectStatus().isUnauthorized();
        verify(client, never()).create(any(CommerceOrder.class));
        product.getMetadata().setAnnotations(Map.of("commerce.halo.run/allow-guest-purchase", "true"));
        var result = http.post().uri("/shop/api/orders").contentType(MediaType.APPLICATION_JSON).bodyValue(body)
            .exchange().expectStatus().isCreated().expectBody().returnResult();
        var captured = org.mockito.ArgumentCaptor.forClass(CommerceOrder.class);
        verify(client).create(captured.capture());
        assertTrue(captured.getValue().getSpec().buyer().startsWith("guest:"));
        var cookie = result.getResponseCookies().getFirst("SESSION");
        assertNotNull(cookie);
        when(client.fetch(CommerceOrder.class, captured.getValue().getMetadata().getName())).thenReturn(Mono.just(captured.getValue()));
        http.get().uri("/shop/api/orders/" + captured.getValue().getMetadata().getName()).cookie("SESSION", cookie.getValue())
            .exchange().expectStatus().isOk();
        http.get().uri("/shop/api/orders/" + captured.getValue().getMetadata().getName()).exchange().expectStatus().isUnauthorized();
    }
    @Test void requiresShippingAddressForPhysicalProducts() {
        var product = product("book", Product.Type.PHYSICAL, 1999, 4);
        when(client.fetch(Product.class, "book")).thenReturn(Mono.just(product));
        var orderRoutes = new OrderRoutes().commerceOrderRoutes(client, mock(InventoryService.class), mock(TemplateNameResolver.class),
            new EpaySettings("", "", "", "", "", ""));
        var body = "{\"selections\":[{\"productName\":\"book\",\"skuId\":\"sku\",\"quantity\":1}]}";
        asUser(orderRoutes, "alice").post().uri("/shop/api/orders").contentType(MediaType.APPLICATION_JSON).bodyValue(body).exchange()
            .expectStatus().isBadRequest();
        verify(client, never()).create(any(CommerceOrder.class));
    }
    @Test void rejectsAnonymousOrderRequests() {
        http.get().uri("/shop/api/orders").exchange().expectStatus().isUnauthorized();
    }
    @Test void foreignBuyerCannotReadOrderOrShippingDetails() {
        asUser("mallory").get().uri("/shop/api/orders/order-123").exchange()
            .expectStatus().isNotFound();
    }
    @Test void digitalDeliveryRequiresPaymentAndOnlyTheBuyerCanReadIt() {
        var digitalLine = new CheckoutPricing.Line("ebook", "Ebook", Product.Type.DIGITAL, "ebook-key", "License",
            1000, 1, 1000);
        order.setSpec(new CommerceOrder.Spec("alice", List.of(digitalLine), 1000, "CNY", null,
            CommerceOrder.State.AWAITING_PAYMENT, order.getSpec().createdAt(), order.getSpec().expiresAt(), null));
        asUser("alice").get().uri("/shop/api/orders/order-123/digital-delivery").exchange().expectStatus().isEqualTo(409);
        order.setSpec(order.getSpec().withState(CommerceOrder.State.PAID, "trade-1"));
        asUser("mallory").get().uri("/shop/api/orders/order-123/digital-delivery").exchange().expectStatus().isNotFound();
        var product = product("ebook", Product.Type.DIGITAL, 1000, 0);
        product.setStatus(new Product.StockStatus(Map.of(), Map.of("order-123", Map.of("ebook-key", List.of("secret-code")))));
        when(client.fetch(Product.class, "ebook")).thenReturn(Mono.just(product));
        for (int attempt = 0; attempt < 2; attempt++) {
            asUser("alice").get().uri("/shop/api/orders/order-123/digital-delivery").exchange().expectStatus().isOk()
                .expectBody().jsonPath("$[0].productTitle").isEqualTo("Ebook")
                .jsonPath("$[0].codes[0]").isEqualTo("secret-code");
        }
    }
    @Test void ownerCanCancelPendingOrderAndReleaseItsStock() {
        when(inventory.release("book", "order-123")).thenReturn(Mono.just(new Product()));
        asUser("alice").post().uri("/shop/api/orders/order-123/cancel").exchange()
            .expectStatus().isOk().expectBody().jsonPath("$.state").isEqualTo("CANCELLED");
        verify(inventory).release("book", "order-123");
        verify(client).update(order);
    }
    @Test void foreignBuyerCannotCancelAnOrder() {
        asUser("mallory").post().uri("/shop/api/orders/order-123/cancel").exchange().expectStatus().isNotFound();
        verify(inventory, never()).release(anyString(), anyString());
        verify(client, never()).update(any(CommerceOrder.class));
    }
    @Test void onlyOwnersSeeTheirOrdersAndShippingDetails() {
        asUser("alice").get().uri("/shop/api/orders/order-123").exchange()
            .expectStatus().isOk().expectBody().jsonPath("$.spec.buyer").isEqualTo("alice")
            .jsonPath("$.spec.shippingAddress.address").isEqualTo("10 Main Street");
        asUser("mallory").get().uri("/shop/api/orders").exchange()
            .expectStatus().isOk().expectBody().jsonPath("$.length()").isEqualTo(0);
    }
    @Test void buyerCanReadShipmentTrackingButAnotherBuyerCannot() {
        order.setSpec(order.getSpec().withState(CommerceOrder.State.PAID, "trade-1")
            .withShipment(new CommerceOrder.Shipment("Carrier", "TRACK-123", false), Instant.parse("2026-10-02T00:01:00Z")));
        asUser("alice").get().uri("/shop/api/orders/order-123").exchange().expectStatus().isOk()
            .expectBody().jsonPath("$.spec.fulfillment[0].state").isEqualTo("SHIPPED")
            .jsonPath("$.spec.fulfillment[0].trackingNumber").isEqualTo("TRACK-123");
        asUser("mallory").get().uri("/shop/api/orders/order-123").exchange().expectStatus().isNotFound();
    }
    private Product product(String name, Product.Type type, long price, long stock) {
        var product = new Product(); var metadata = new Metadata(); metadata.setName(name); product.setMetadata(metadata);
        product.setSpec(new Product.Spec("Book", "book", type, Product.State.PUBLISHED,
            "", List.of(), List.of(), List.of(new Product.Sku("sku", "Standard", price, stock,
                type == Product.Type.DIGITAL ? List.of("secret-code") : List.of())), false));
        return product;
    }
    private WebTestClient asUser(String name) { return asUser(routes, name); }
    private WebTestClient asUser(org.springframework.web.reactive.function.server.RouterFunction<org.springframework.web.reactive.function.server.ServerResponse> router, String name) {
        var authentication = UsernamePasswordAuthenticationToken.authenticated(name, "",
            AuthorityUtils.createAuthorityList("ROLE_USER"));
        return WebTestClient.bindToRouterFunction(router).webFilter((exchange, chain) -> chain.filter(exchange)
            .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication))).build();
    }
}
