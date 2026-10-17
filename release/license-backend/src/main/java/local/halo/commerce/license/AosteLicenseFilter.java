package local.halo.commerce.license;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import run.halo.app.extension.ConfigMap;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.security.AfterSecurityWebFilter;

/** Enforce before handlers, so disabling JavaScript cannot bypass authorization. */
@Component
public class AosteLicenseFilter implements AfterSecurityWebFilter {
    private final AosteLicenseService licenses;
    private final ReactiveExtensionClient client;
    private final ObjectMapper json = new ObjectMapper();

    public AosteLicenseFilter(AosteLicenseService licenses, ReactiveExtensionClient client) {
        this.licenses = licenses;
        this.client = client;
    }

    static boolean exempt(String path) {
        // Keep existing transactions, payment signatures, and the recovery/admin UI operational.
        return path.equals("/shop/api/admin/license") || path.startsWith("/shop/api/admin/license/")
            || path.startsWith("/shop/payment/") || path.equals("/shop/orders")
            || path.startsWith("/shop/api/orders/") && !path.endsWith("/payment")
            || path.startsWith("/shop/api/admin/orders") || path.startsWith("/shop/api/admin/updates/")
            || path.equals("/console") || path.startsWith("/console/")
            || path.equals("/uc") || path.startsWith("/uc/")
            || path.startsWith("/apis/") && !commerceApi(path)
            || path.startsWith("/actuator/") || path.startsWith("/assets/")
            || path.startsWith("/plugins/") || path.startsWith("/themes/")
            || path.startsWith("/upload/") || path.startsWith("/attachments/")
            || path.equals("/login") || path.startsWith("/login/") || path.equals("/logout")
            || path.startsWith("/oauth2/") || path.startsWith("/webjars/")
            || path.equals("/favicon.ico") || path.equals("/robots.txt");
    }

    static boolean commerceApi(String path) {
        return path.startsWith("/apis/commerce.halo.run/") || path.startsWith("/apis/api.commerce.halo.run/");
    }

    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().pathWithinApplication().value();
        if (exempt(path) || (path.equals("/shop/api/orders") && exchange.getRequest().getMethod() == org.springframework.http.HttpMethod.GET)) return chain.filter(exchange);
        Mono<Boolean> requiresLicense = path.equals("/shop") || path.startsWith("/shop/") || commerceApi(path)
            ? Mono.just(true) : activeAoste(exchange);
        return requiresLicense.flatMap(required -> {
            if (!required) return chain.filter(exchange);
            return licenses.status().onErrorReturn(AosteLicenseService.unavailable()).flatMap(status ->
                status.valid() ? chain.filter(exchange) : denied(exchange, status.state().equals("unavailable")));
        });
    }

    private Mono<Boolean> activeAoste(ServerWebExchange exchange) {
        String preview = exchange.getRequest().getQueryParams().getFirst("preview-theme");
        if ("theme-aoset".equals(preview)) return Mono.just(true);
        return client.fetch(ConfigMap.class, "system").flatMap(config -> {
            if (config.getData() == null || !config.getData().containsKey("theme")) return Mono.empty();
            return Mono.just(isAoste(config.getData().get("theme")));
        }).switchIfEmpty(client.fetch(ConfigMap.class, "system-default").map(config ->
            config.getData() != null && isAoste(config.getData().get("theme")))).defaultIfEmpty(false);
    }

    private boolean isAoste(String theme) {
        try { return "theme-aoset".equals(json.readTree(theme).path("active").asText()); }
        catch (Exception ignored) { return false; }
    }

    private Mono<Void> denied(ServerWebExchange exchange, boolean unavailable) {
        var response = exchange.getResponse();
        response.setStatusCode(unavailable ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.FORBIDDEN);
        response.getHeaders().setCacheControl("no-store");
        String path = exchange.getRequest().getPath().value();
        boolean api = path.startsWith("/shop/api/") || path.startsWith("/apis/");
        response.getHeaders().setContentType(api ? MediaType.APPLICATION_JSON : MediaType.TEXT_HTML);
        String body = api ? "{\"code\":\"AOSTE_LICENSE_REQUIRED\",\"message\":\"请先在后台更新页完成主题与插件授权。\"}"
            : "<!doctype html><html lang=\"zh-CN\"><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><title>AOSTE 授权</title><body style=\"font:16px system-ui;max-width:560px;margin:15vh auto;padding:24px;line-height:1.8\"><h1>" + (unavailable ? "授权服务暂时不可用" : "AOSTE 尚未授权") + "</h1><p>请站点管理员前往后台更新页完成授权。</p><a href=\"/console/aoste/updates\">前往后台授权</a><p>如无授权码请<a href=\"https://hfz.pw/shop/2678.html\" target=\"_blank\" rel=\"noopener noreferrer\">前往获取</a>（免费）</p></body></html>";
        return response.writeWith(Mono.just(response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8))));
    }
}
