package local.halo.commerce;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.ReactiveExtensionClient;

class EpayRoutesTest {
    private final String key = "test-secret";
    private ReactiveExtensionClient client;
    private InventoryService inventory;
    private EpayPaymentService payments;
    private CommerceOrder order;
    private WebTestClient http;
    private WebTestClient buyer;
    @BeforeEach void setup() {
        client = mock(ReactiveExtensionClient.class); inventory = mock(InventoryService.class);
        var now = Instant.parse("2099-10-02T00:00:00Z");
        order = new CommerceOrder(); var metadata = new Metadata(); metadata.setName("order-123"); order.setMetadata(metadata);
        var line = new CheckoutPricing.Line("book", "Book", Product.Type.PHYSICAL, "sku", "Standard", 1000, 1, 1000);
        order.setSpec(new CommerceOrder.Spec("alice", List.of(line), 1000, "CNY",
            new CommerceOrder.ShippingAddress("Alice", "1234567", "Main Street"),
            CommerceOrder.State.AWAITING_PAYMENT, now, now.plusSeconds(900), null));
        when(client.fetch(CommerceOrder.class, "order-123")).thenReturn(Mono.just(order));
        when(client.update(any(CommerceOrder.class))).thenAnswer(call -> Mono.just(call.getArgument(0)));
        when(inventory.commitPaid("book", "order-123")).thenReturn(Mono.just(new Product()));
        when(inventory.release("book", "order-123")).thenReturn(Mono.just(new Product()));
        var settings = new EpaySettings("https://pay.example/submit.php", "12345", key,
            "https://shop.example/shop/payment/notify", "https://shop.example/shop/orders", "Shop");
        payments = new EpayPaymentService(settings);
        var routes = new EpayRoutes().epayRoutes(client, inventory, settings, payments);
        http = WebTestClient.bindToRouterFunction(routes).build();
        buyer = WebTestClient.bindToRouterFunction(routes).webFilter((exchange, chain) -> chain.filter(exchange)
            .contextWrite(org.springframework.security.core.context.ReactiveSecurityContextHolder.withAuthentication(
                org.springframework.security.authentication.UsernamePasswordAuthenticationToken.authenticated(
                    "alice", "", org.springframework.security.core.authority.AuthorityUtils.createAuthorityList("ROLE_USER"))))).build();
    }
    @Test void rejectsUnsignedWrongAmountAndRepeatedParameters() {
        http.get().uri("/shop/payment/notify?pid=12345&out_trade_no=order-123").exchange().expectStatus().isBadRequest();
        var wrongAmount = signed("10.01");
        http.get().uri(uri(wrongAmount)).exchange().expectStatus().isBadRequest();
        http.get().uri("/shop/payment/notify?pid=12345&pid=12345&out_trade_no=order-123").exchange()
            .expectStatus().isBadRequest();
        verify(inventory, never()).commitPaid(anyString(), anyString());
    }
    @Test void verifiesCallbackThenCommitsReservedInventoryOnlyOnce() {
        var uri = uri(signed("10.00"));
        buyer.get().uri(uri).exchange().expectStatus().isOk().expectBody(String.class).isEqualTo("success");
        assertEquals(CommerceOrder.State.FULFILLING, order.getSpec().state());
        assertEquals("gateway-1", order.getSpec().gatewayTradeNo());
        buyer.get().uri(uri).exchange().expectStatus().isOk().expectBody(String.class).isEqualTo("success");
        verify(inventory, times(2)).commitPaid("book", "order-123");
    }
    @Test void latePaymentIsFlaggedForRefundAndNeverCommitsStock() {
        order.setSpec(order.getSpec().withState(CommerceOrder.State.CANCELLED, null));
        buyer.get().uri(uri(signed("10.00"))).exchange().expectStatus().isOk().expectBody(String.class).isEqualTo("success");
        assertEquals(CommerceOrder.State.REFUND_REQUIRED, order.getSpec().state());
        verify(inventory, never()).commitPaid(anyString(), anyString());
        verify(inventory).release("book", "order-123");
    }
    @Test void redirectsBuyerDirectlyToGatewayWithoutExposingMerchantSecret() {
        var response = buyer.get().uri("/shop/api/orders/order-123/payment?type=alipay").exchange()
            .expectStatus().isFound().expectHeader().valueEquals("Cache-Control", "no-store")
            .expectBody().returnResult();
        var location = response.getResponseHeaders().getLocation().toString();
        assertTrue(location.startsWith("https://pay.example/submit.php?"));
        assertTrue(location.contains("out_trade_no=order-123"));
        assertTrue(location.contains("type=alipay"));
        assertFalse(location.contains(key));
        http.get().uri("/shop/api/orders/order-123/payment?type=alipay").exchange().expectStatus().isUnauthorized();
    }
    private Map<String, String> signed(String amount) {
        var fields = new java.util.LinkedHashMap<>(Map.of("pid", "12345", "trade_no", "gateway-1",
            "out_trade_no", "order-123", "type", "alipay", "name", "Book", "money", amount,
            "trade_status", "TRADE_SUCCESS", "sign_type", "MD5"));
        fields.put("sign", payments.protocol().sign(fields, key)); return fields;
    }
    private String uri(Map<String, String> fields) {
        var query = fields.entrySet().stream().map(entry -> entry.getKey() + "=" +
            java.net.URLEncoder.encode(entry.getValue(), java.nio.charset.StandardCharsets.UTF_8)).collect(java.util.stream.Collectors.joining("&"));
        return "/shop/payment/notify?" + query;
    }
}
