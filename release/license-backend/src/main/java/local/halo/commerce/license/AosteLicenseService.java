package local.halo.commerce.license;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.extension.Secret;

/** One server-side credential and decision for the theme and commerce plugin. */
@Component
public class AosteLicenseService {
    static final String SECRET = "aoste-authorization";
    static final String VERIFY_URL = "https://ets.txsw.top/api/authorization/aoste";
    private final ReactiveExtensionClient client;
    private final WebClient ets;
    private String cachedKey;
    private Mono<Status> cached;

    @org.springframework.beans.factory.annotation.Autowired
    public AosteLicenseService(ReactiveExtensionClient client) {
        this(client, WebClient.builder().codecs(c -> c.defaultCodecs().maxInMemorySize(16384)).build());
    }

    AosteLicenseService(ReactiveExtensionClient client, WebClient ets) {
        this.client = client;
        this.ets = ets;
    }

    public record Status(boolean valid, String state, String message, String expiresAt) {}

    public Mono<Status> status() {
        return client.fetch(Secret.class, SECRET)
            .map(s -> s.getData() == null ? "" : new String(s.getData().getOrDefault("key", new byte[0]), StandardCharsets.UTF_8))
            .defaultIfEmpty("").flatMap(this::cachedVerification).map(status ->
                status.valid() && status.expiresAt() != null && !Instant.parse(status.expiresAt()).isAfter(Instant.now())
                    ? new Status(false, "expired", "AOSTE 授权已到期。", status.expiresAt()) : status);
    }

    private synchronized Mono<Status> cachedVerification(String key) {
        if (key.isBlank()) return Mono.just(missing());
        if (!key.equals(cachedKey) || cached == null) {
            cachedKey = key;
            cached = verify(key).cache(result -> Duration.ofSeconds(result.valid() ? 60 : 10),
                error -> Duration.ZERO, () -> Duration.ZERO);
        }
        return cached;
    }

    public Mono<Status> verify(String key) {
        if (!key.matches("ets_ak_[A-Za-z0-9_-]{43}")) return Mono.just(new Status(false, "invalid", "授权码格式不正确。", null));
        return ets.post().uri(VERIFY_URL).bodyValue(Map.of("key", key))
            .exchangeToMono(response -> {
                if (response.statusCode().value() == 401 || response.statusCode().value() == 403)
                    return response.releaseBody().thenReturn(new Status(false, "invalid", "授权码无效、已撤销或没有 AOSTE 使用权限。", null));
                if (!response.statusCode().is2xxSuccessful())
                    return response.releaseBody().thenReturn(unavailable());
                return response.bodyToMono(String.class).map(text -> {
                    try {
                    JsonNode body = new com.fasterxml.jackson.databind.ObjectMapper().readTree(text);
                    String expires = body.path("expiresAt").isTextual() ? body.path("expiresAt").asText() : null;
                    boolean valid = body.path("valid").asBoolean(false) && "aoste".equals(body.path("product").asText());
                    if (expires != null) valid &= Instant.parse(expires).isAfter(Instant.now());
                    return new Status(valid, valid ? "active" : "invalid", valid ? "已授权，主题与插件共用此授权。" : "AOSTE 授权不可用。", expires);
                    } catch (Exception ignored) { return unavailable(); }
                }).defaultIfEmpty(unavailable());
            }).timeout(Duration.ofSeconds(8)).onErrorReturn(unavailable());
    }

    public Mono<Status> save(String key) {
        String normalized = key == null ? "" : key.trim();
        return verify(normalized).flatMap(status -> {
            if (!status.valid()) return Mono.just(status);
            return client.fetch(Secret.class, SECRET).flatMap(secret -> {
                secret.setData(Map.of("key", normalized.getBytes(StandardCharsets.UTF_8)));
                return client.update(secret);
            }).switchIfEmpty(Mono.defer(() -> {
                Secret secret = new Secret();
                secret.setApiVersion("v1alpha1");
                secret.setKind("Secret");
                Metadata metadata = new Metadata();
                metadata.setName(SECRET);
                secret.setMetadata(metadata);
                secret.setType("Opaque");
                secret.setData(Map.of("key", normalized.getBytes(StandardCharsets.UTF_8)));
                return client.create(secret);
            })).map(ignored -> { invalidate(); return status; });
        });
    }

    public Mono<Status> clear() {
        return client.fetch(Secret.class, SECRET).flatMap(secret -> {
            secret.setData(Map.of("key", new byte[0]));
            return client.update(secret);
        }).then(Mono.fromSupplier(() -> { invalidate(); return missing(); }));
    }

    private synchronized void invalidate() { cached = null; cachedKey = null; }
    static Status missing() { return new Status(false, "missing", "尚未授权，请在后台更新页填写授权码。", null); }
    static Status unavailable() { return new Status(false, "unavailable", "暂时无法连接授权服务，请稍后重试。", null); }
}
