package local.halo.commerce;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.app.extension.ReactiveExtensionClient;

@Configuration
public class EpayRoutes {
    @Bean
    RouterFunction<ServerResponse> configuredEpayRoutes(ReactiveExtensionClient client,
            InventoryService inventory, EpaySettingsProvider provider) {
        return request -> {
            if (!request.path().equals("/shop/payment/notify")
                && !request.path().matches("/shop/api/orders/[^/]+/payment")) return Mono.empty();
            return provider.current().map(settings -> settings.forLocalRequest(request.uri())).flatMap(settings ->
                epayRoutes(client, inventory, settings, new EpayPaymentService(settings)).route(request));
        };
    }

    RouterFunction<ServerResponse> epayRoutes(ReactiveExtensionClient client, InventoryService inventory,
                                               EpaySettings settings, EpayPaymentService payments) {
        var routes = RouterFunctions.route()
            .GET("/shop/api/orders/{name}/payment", request -> BuyerIdentity.resolve(request, false).flatMap(buyer -> {
                var name = request.pathVariable("name");
                var type = request.queryParam("type").orElse("");
                return client.fetch(CommerceOrder.class, name).filter(order -> ownedBy(order, buyer))
                    .switchIfEmpty(Mono.error(new OrderNotFound()))
                    .map(order -> payments.request(order, type))
                    .map(fields -> paymentLocation(payments.submitUrl(), fields))
                    .flatMap(location -> ServerResponse.status(HttpStatus.FOUND)
                        .location(location).header("Cache-Control", "no-store").build());
            }))
            .GET("/shop/payment/notify", request -> {
                var parameters = new LinkedHashMap<String, String>();
                for (var entry : request.queryParams().entrySet()) {
                    if (entry.getValue().size() != 1) return ServerResponse.badRequest().bodyValue("fail");
                    parameters.put(entry.getKey(), entry.getValue().getFirst());
                }
                var orderName = parameters.get("out_trade_no");
                if (!CommerceOrder.validName(orderName)) return ServerResponse.badRequest().bodyValue("fail");
                return client.fetch(CommerceOrder.class, orderName)
                    .switchIfEmpty(Mono.error(new OrderNotFound()))
                    .flatMap(order -> {
                        var receipt = payments.protocol().verifySuccess(parameters, settings.merchantKey(),
                            settings.merchantId(), orderName, order.getSpec().totalMinor());
                        if (order.getSpec().state() == CommerceOrder.State.PAID
                            || order.getSpec().state() == CommerceOrder.State.FULFILLING
                            || order.getSpec().state() == CommerceOrder.State.COMPLETED) {
                            if (!receipt.tradeNo().equals(order.getSpec().gatewayTradeNo())) {
                                return Mono.error(new IllegalStateException("Payment trade number conflicts with order"));
                            }
                            return commitAll(order, inventory).thenReturn("success");
                        }
                        if (order.getSpec().state() == CommerceOrder.State.REFUND_REQUIRED) {
                            if (!receipt.tradeNo().equals(order.getSpec().gatewayTradeNo())) {
                                return Mono.error(new IllegalStateException("Payment trade number conflicts with order"));
                            }
                            return releaseAll(order, inventory).thenReturn("success");
                        }
                        if (order.getSpec().state() != CommerceOrder.State.AWAITING_PAYMENT
                            && order.getSpec().state() != CommerceOrder.State.CANCELLED
                            && order.getSpec().state() != CommerceOrder.State.EXPIRED) {
                            return Mono.error(new IllegalStateException("Order cannot accept payment"));
                        }
                        var wasCancelled = order.getSpec().state() == CommerceOrder.State.CANCELLED
                            || order.getSpec().state() == CommerceOrder.State.EXPIRED;
                        var expired = !order.getSpec().expiresAt().isAfter(java.time.Instant.now());
                        var nextState = expired || wasCancelled ? CommerceOrder.State.REFUND_REQUIRED : CommerceOrder.State.PAID;
                        order.setSpec(order.getSpec().withState(nextState, receipt.tradeNo()));
                        return client.update(order).flatMap(updated -> expired || wasCancelled
                            ? releaseAll(updated, inventory).thenReturn("success")
                            : commitAll(updated, inventory).thenReturn("success"));
                    }).flatMap(body -> ServerResponse.ok().contentType(MediaType.TEXT_PLAIN).bodyValue(body));
            })
            .build();
        return routes.filter((request, next) -> next.handle(request)
            .onErrorResume(BuyerIdentity.LoginRequired.class, e -> ServerResponse.status(HttpStatus.UNAUTHORIZED).build())
            .onErrorResume(OrderNotFound.class, e -> ServerResponse.notFound().build())
            .onErrorResume(IllegalArgumentException.class, e -> ServerResponse.badRequest().bodyValue("fail"))
            .onErrorResume(IllegalStateException.class, e -> ServerResponse.status(HttpStatus.CONFLICT).bodyValue("fail"))
            .onErrorResume(Throwable.class, e -> ServerResponse.status(HttpStatus.SERVICE_UNAVAILABLE).bodyValue("fail")));
    }

    private Mono<Void> commitAll(CommerceOrder order, InventoryService inventory) {
        return Flux.fromIterable(order.getSpec().lines().stream().map(CheckoutPricing.Line::productName).distinct().toList())
            .concatMap(name -> inventory.commitPaid(name, order.getMetadata().getName())).then();
    }
    private Mono<Void> releaseAll(CommerceOrder order, InventoryService inventory) {
        return Flux.fromIterable(order.getSpec().lines().stream().map(CheckoutPricing.Line::productName).distinct().toList())
            .concatMap(name -> inventory.release(name, order.getMetadata().getName())).then();
    }
    private boolean ownedBy(CommerceOrder order, String buyer) {
        return order != null && order.getMetadata() != null && order.getMetadata().getName() != null
            && order.getSpec() != null && order.getSpec().buyer().equals(buyer);
    }
    private Mono<String> authenticated() {
        return ReactiveSecurityContextHolder.getContext().map(SecurityContext::getAuthentication)
            .filter(Authentication::isAuthenticated).filter(auth -> !(auth instanceof AnonymousAuthenticationToken))
            .map(Authentication::getName).filter(name -> name != null && !name.isBlank())
            .switchIfEmpty(Mono.error(new Unauthenticated()));
    }
    private java.net.URI paymentLocation(String action, Map<String, String> fields) {
        var query = fields.entrySet().stream().map(entry ->
            java.net.URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8) + "="
                + java.net.URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8))
            .collect(java.util.stream.Collectors.joining("&"));
        return java.net.URI.create(action + (action.contains("?") ? "&" : "?") + query);
    }
    private static final class Unauthenticated extends RuntimeException {}
    private static final class OrderNotFound extends RuntimeException {}
}
