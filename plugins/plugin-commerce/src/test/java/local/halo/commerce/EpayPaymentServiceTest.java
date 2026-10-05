package local.halo.commerce;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class EpayPaymentServiceTest {
    private final Instant now = Instant.parse("2026-10-02T00:00:00Z");
    private final EpaySettings settings = new EpaySettings("https://pay.example/submit.php", "12345",
        "test-secret", "https://shop.example/shop/payment/notify", "https://shop.example/shop/orders", "商城网站");
    @Test void producesSignedProviderFieldsWithoutLeakingSecret() {
        var service = new EpayPaymentService(settings);
        var order = order();
        var fields = service.request(order, "alipay");
        assertEquals("12345", fields.get("pid")); assertEquals("order-123", fields.get("out_trade_no"));
        assertEquals("30.00", fields.get("money")); assertEquals("alipay", fields.get("type"));
        assertEquals(service.protocol().sign(fields, "test-secret"), fields.get("sign"));
        assertFalse(fields.containsValue("test-secret")); assertEquals("https://pay.example/submit.php", service.submitUrl());
    }
    @Test void requiresConfigurationPendingPaymentAndSupportedMethod() {
        var unconfigured = new EpayPaymentService(new EpaySettings("", "", "", "", "", ""));
        assertFalse(new EpaySettings("", "", "", "", "", "").configured());
        assertThrows(IllegalStateException.class, () -> unconfigured.request(order(), "alipay"));
        assertThrows(IllegalArgumentException.class, () -> new EpayPaymentService(settings).request(order(), "internal"));
        var cancelled = order(); cancelled.setSpec(cancelled.getSpec().withState(CommerceOrder.State.CANCELLED, null));
        assertThrows(IllegalStateException.class, () -> new EpayPaymentService(settings).request(cancelled, "alipay"));
    }
    @Test void requiresPublicHttpsGatewayAndCallbacks() {
        var insecure = new EpaySettings("http://pay.example/submit", "12345", "test-secret",
            "https://shop.example/notify", "https://shop.example/return", "");
        assertFalse(insecure.configured());
        assertThrows(IllegalStateException.class, () -> new EpayPaymentService(insecure).request(order(), "wxpay"));
    }
    @Test void usdtUsesOriginalCnyAmountAndStandardSignedNotification() {
        var service = new EpayPaymentService(settings.withPaymentTypes(java.util.Set.of("usdt")));
        var request = service.request(order(), "usdt");
        assertEquals("usdt", request.get("type"));
        assertEquals("30.00", request.get("money"));
        assertEquals(service.protocol().sign(request, "test-secret"), request.get("sign"));
        var callback = new java.util.HashMap<String, String>(request);
        callback.put("money", "30");
        callback.put("trade_no", "usdt-trade-1");
        callback.put("trade_status", "TRADE_SUCCESS");
        callback.put("sign", service.protocol().sign(callback, "test-secret"));
        var receipt = service.protocol().verifySuccess(callback, "test-secret", "12345", "order-123", 3000);
        assertEquals("usdt", receipt.paymentType());
        assertEquals(3000, receipt.amountMinor());
        assertThrows(IllegalStateException.class, () -> new EpayPaymentService(settings.withPaymentTypes(java.util.Set.of())).request(order(), "usdt"));
    }
    @Test void normalizesGatewayBaseUrlToSubmitEndpoint() {
        var base = new EpaySettings("https://pay.example", "123", "key", "https://shop.example/notify", "https://shop.example/return", "Shop");
        assertEquals("https://pay.example/submit.php", base.submitUrl());
    }
    @Test void localCheckoutCanUseDefaultCallbacksWithoutChangingPublicConfiguration() {
        var incomplete = new EpaySettings("https://pay.example", "123", "key", "", "", "Shop");
        assertFalse(incomplete.forLocalRequest(java.net.URI.create("https://shop.example/shop/orders")).configured());
        var local = incomplete.forLocalRequest(java.net.URI.create("http://localhost:8090/shop/orders"));
        assertTrue(local.configured());
        var fields = new EpayPaymentService(local).request(order(), "wxpay");
        assertEquals("http://localhost:8090/shop/payment/notify", fields.get("notify_url"));
        assertEquals("http://localhost:8090/shop/orders", fields.get("return_url"));
        assertEquals(new EpayProtocol().sign(fields, "key"), fields.get("sign"));
    }
    private CommerceOrder order() {
        var order = new CommerceOrder(); var metadata = new run.halo.app.extension.Metadata(); metadata.setName("order-123"); order.setMetadata(metadata);
        var line = new CheckoutPricing.Line("book", "Book", Product.Type.DIGITAL, "sku", "Standard", 1500, 2, 3000);
        order.setSpec(new CommerceOrder.Spec("alice", List.of(line), 3000, "CNY", null,
            CommerceOrder.State.AWAITING_PAYMENT, now, now.plusSeconds(900), null));
        return order;
    }
}
