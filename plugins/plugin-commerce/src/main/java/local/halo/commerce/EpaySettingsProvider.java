package local.halo.commerce;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import run.halo.app.extension.ConfigMap;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.extension.Secret;

/** Resolves a fresh settings snapshot per request. Theme config stores only a Secret reference. */
@Component
public class EpaySettingsProvider {
    private final ReactiveExtensionClient client;
    private final EpaySettings fallback;
    private final ObjectMapper mapper = new ObjectMapper();

    public EpaySettingsProvider(ReactiveExtensionClient client, EpaySettings fallback) {
        this.client = client;
        this.fallback = fallback;
    }

    public Mono<EpaySettings> current() {
        return Mono.defer(() -> client.fetch(ConfigMap.class, "theme-earth-configMap"))
            .filter(config -> config.getData() != null && config.getData().containsKey("commerce_payment"))
            .flatMap(config -> Mono.fromCallable(() -> mapper.readTree(config.getData().get("commerce_payment"))))
            .flatMap(config -> key(config.path("merchant_secret").asText("")).map(key ->
                new EpaySettings(config.path("submit_url").asText(""), config.path("merchant_id").asText(""), key,
                    config.path("notify_url").asText(""), config.path("return_url").asText(""),
                    config.path("site_name").asText(""), config.path("enabled").asBoolean(false))
                    .withPaymentTypes(java.util.stream.Stream.of("alipay", "wxpay", "usdt")
                        .filter(type -> config.path(type + "_enabled").asBoolean(!type.equals("usdt"))
                            && config.path(type + "_provider").asText("epay").equals("epay"))
                        .collect(java.util.stream.Collectors.toSet()))))
            .defaultIfEmpty(fallback);
    }

    private Mono<String> key(String name) {
        if (name.isBlank()) return Mono.just("");
        return client.fetch(Secret.class, name).map(secret -> {
            if (secret.getStringData() != null && secret.getStringData().containsKey("merchant_key")) {
                return secret.getStringData().get("merchant_key");
            }
            var bytes = secret.getData() == null ? null : secret.getData().get("merchant_key");
            return bytes == null ? "" : new String(bytes, StandardCharsets.UTF_8);
        }).defaultIfEmpty("");
    }
}
