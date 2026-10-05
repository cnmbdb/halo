package local.halo.commerce;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.ReactiveExtensionClient;

class AdminOrderRoutesTest {
    private ReactiveExtensionClient client;
    private CommerceOrder order;
    private org.springframework.web.reactive.function.server.RouterFunction<org.springframework.web.reactive.function.server.ServerResponse> routes;

    @BeforeEach void setup() {
        client = mock(ReactiveExtensionClient.class);
        var now = Instant.parse("2026-10-02T00:00:00Z");
        order = new CommerceOrder(); var metadata = new Metadata(); metadata.setName("order-123"); order.setMetadata(metadata);
        var line = new CheckoutPricing.Line("box", "Box", Product.Type.PHYSICAL, "sku", "One", 1000, 1, 1000);
        order.setSpec(new CommerceOrder.Spec("alice", List.of(line), 1000, "CNY",
            new CommerceOrder.ShippingAddress("Alice", "1234567", "Main Street"),
            CommerceOrder.State.AWAITING_PAYMENT, now, now.plusSeconds(900), null).withState(CommerceOrder.State.PAID, "trade-1"));
        when(client.fetch(CommerceOrder.class, "order-123")).thenReturn(Mono.just(order));
        when(client.update(any(CommerceOrder.class))).thenAnswer(call -> Mono.just(call.getArgument(0)));
        when(client.list(eq(CommerceOrder.class), any(), any())).thenReturn(Flux.just(order));
        routes = new AdminOrderRoutes().commerceAdminOrderRoutes(client);
    }

    @Test void orderManagementRequiresExplicitPermission() {
        WebTestClient.bindToRouterFunction(routes).build().get().uri("/shop/api/admin/orders").exchange()
            .expectStatus().isUnauthorized();
        as("buyer", "ROLE_USER").get().uri("/shop/api/admin/orders").exchange().expectStatus().isForbidden();
        as("manager", "commerce:orders:manage").get().uri("/shop/api/admin/orders?page=1").exchange()
            .expectStatus().isOk().expectBody().jsonPath("$.total").isEqualTo(1).jsonPath("$.items[0].metadata.name").isEqualTo("order-123");
    }

    @Test void haloSuperAdminCanReadOrdersAndUpdateShipment() {
        var admin = as("admin", "ROLE_super-role");
        admin.get().uri("/shop/api/admin/orders?page=1").exchange()
            .expectStatus().isOk().expectBody().jsonPath("$.items[0].metadata.name").isEqualTo("order-123");
        admin.post().uri("/shop/api/admin/orders/order-123/shipment").contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"carrier\":\"Carrier\",\"trackingNumber\":\"TRACK-123\",\"delivered\":false}")
            .exchange().expectStatus().isOk();
        as("buyer", "ROLE_authenticated").get().uri("/shop/api/admin/orders").exchange().expectStatus().isForbidden();
    }

    @Test void shipmentProgressIsTrackedAndMixedCompletionCannotSkipDelivery() {
        var manager = as("manager", "commerce:orders:manage");
        manager.post().uri("/shop/api/admin/orders/order-123/shipment").contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"carrier\":\"Carrier\",\"trackingNumber\":\"TRACK-123\",\"delivered\":true}")
            .exchange().expectStatus().isEqualTo(409);
        manager.post().uri("/shop/api/admin/orders/order-123/shipment").contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"carrier\":\"Carrier\",\"trackingNumber\":\"TRACK-123\",\"delivered\":false}")
            .exchange().expectStatus().isOk().expectBody().jsonPath("$.spec.state").isEqualTo("FULFILLING")
            .jsonPath("$.spec.fulfillment[0].state").isEqualTo("SHIPPED");
        manager.post().uri("/shop/api/admin/orders/order-123/shipment").contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"carrier\":\"Carrier\",\"trackingNumber\":\"TRACK-123\",\"delivered\":true}")
            .exchange().expectStatus().isOk().expectBody().jsonPath("$.spec.state").isEqualTo("COMPLETED")
            .jsonPath("$.spec.fulfillment[0].state").isEqualTo("DELIVERED");
    }

    @Test void rejectsInvalidTrackingInput() {
        as("manager", "commerce:orders:manage").post().uri("/shop/api/admin/orders/order-123/shipment")
            .contentType(MediaType.APPLICATION_JSON).bodyValue("{\"carrier\":\"Carrier\",\"trackingNumber\":\"<script>\",\"delivered\":false}")
            .exchange().expectStatus().isBadRequest();
        verify(client, never()).update(any(CommerceOrder.class));
    }

    private WebTestClient as(String name, String authority) {
        var authentication = UsernamePasswordAuthenticationToken.authenticated(name, "", AuthorityUtils.createAuthorityList(authority));
        return WebTestClient.bindToRouterFunction(routes).webFilter((exchange, chain) -> chain.filter(exchange)
            .contextWrite(org.springframework.security.core.context.ReactiveSecurityContextHolder.withAuthentication(authentication))).build();
    }
}
