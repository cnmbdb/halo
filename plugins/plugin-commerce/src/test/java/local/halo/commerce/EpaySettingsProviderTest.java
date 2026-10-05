package local.halo.commerce;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import run.halo.app.extension.ConfigMap;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.extension.Secret;

class EpaySettingsProviderTest {
    @Test void readsSavedThemeSettingsAndSecretFreshForEachRequest() {
        var client = mock(ReactiveExtensionClient.class);
        var config = new ConfigMap();
        config.setData(Map.of("commerce_payment", """
            {"enabled":true,"submit_url":"https://pay.example/submit.php","merchant_id":"123",
             "merchant_secret":"store-payment","notify_url":"https://shop.example/shop/payment/notify",
             "return_url":"https://shop.example/shop/orders","site_name":"2333 Store"}
            """));
        var secret = new Secret();
        secret.setData(Map.of("merchant_key", "first-key".getBytes(StandardCharsets.UTF_8)));
        when(client.fetch(ConfigMap.class, "theme-earth-configMap")).thenReturn(Mono.just(config));
        when(client.fetch(Secret.class, "store-payment")).thenReturn(Mono.just(secret));
        var provider = new EpaySettingsProvider(client, empty());
        var first = provider.current().block();
        assertTrue(first.configured());
        assertEquals("123", first.merchantId());
        assertEquals("first-key", first.merchantKey());
        secret.setData(Map.of("merchant_key", "rotated-key".getBytes(StandardCharsets.UTF_8)));
        assertEquals("rotated-key", provider.current().block().merchantKey());
        assertEquals("first-key", first.merchantKey());
        config.setData(Map.of("commerce_payment", config.getData().get("commerce_payment").replace("true", "false")));
        var disabled = provider.current().block();
        assertFalse(disabled.configured());
        assertEquals("rotated-key", disabled.merchantKey(), "Callbacks retain verification credentials when disabled");
    }
    @Test void missingSecretDoesNotEnablePaymentOrFallbackToEnvironment() {
        var client = mock(ReactiveExtensionClient.class);
        var config = new ConfigMap();
        config.setData(Map.of("commerce_payment", "{\"enabled\":true,\"merchant_secret\":\"missing\"}"));
        when(client.fetch(ConfigMap.class, "theme-earth-configMap")).thenReturn(Mono.just(config));
        when(client.fetch(Secret.class, "missing")).thenReturn(Mono.empty());
        var provider = new EpaySettingsProvider(client, new EpaySettings("https://pay.example/submit.php", "123", "env-key",
            "https://shop.example/notify", "https://shop.example/orders", ""));
        assertFalse(provider.current().block().configured());
        assertThrows(IllegalStateException.class, () -> provider.current().block().merchantKey());
    }
    @Test void olderInstallWithoutThemePaymentGroupKeepsEnvironmentConfiguration() {
        var client = mock(ReactiveExtensionClient.class);
        when(client.fetch(ConfigMap.class, "theme-earth-configMap")).thenReturn(Mono.empty());
        var fallback = empty();
        assertSame(fallback, new EpaySettingsProvider(client, fallback).current().block());
    }
    @Test void disabledAndUnconnectedChannelsAreNotAvailable() {
        var client = mock(ReactiveExtensionClient.class);
        var config = new ConfigMap();
        config.setData(Map.of("commerce_payment", "{\"wxpay_enabled\":false,\"alipay_enabled\":true,\"alipay_provider\":\"official\",\"usdt_enabled\":true,\"usdt_provider\":\"tokenpay\"}"));
        when(client.fetch(ConfigMap.class, "theme-earth-configMap")).thenReturn(Mono.just(config));
        var settings = new EpaySettingsProvider(client, empty()).current().block();
        assertFalse(settings.paymentEnabled("wxpay"));
        assertFalse(settings.paymentEnabled("alipay"));
        assertFalse(settings.paymentEnabled("usdt"));
        config.setData(Map.of("commerce_payment", config.getData().get("commerce_payment").replace("tokenpay", "epay")));
        assertTrue(new EpaySettingsProvider(client, empty()).current().block().paymentEnabled("usdt"));
    }
    private EpaySettings empty() { return new EpaySettings("", "", "", "", "", ""); }
}
