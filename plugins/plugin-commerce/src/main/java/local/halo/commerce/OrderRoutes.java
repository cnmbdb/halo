package local.halo.commerce;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.theme.TemplateNameResolver;

@Configuration
public class OrderRoutes {
    public record CreateOrderRequest(List<CheckoutPricing.Selection> selections,
                                     CommerceOrder.ShippingAddress shippingAddress) {}
    public record OrderReceipt(String name, CommerceOrder.State state, Instant expiresAt,
                               long totalMinor, String currency) {}
    public record DigitalDelivery(String productTitle, String skuTitle, int quantity, List<String> codes) {}

    private final Clock clock;
    public OrderRoutes() { this(Clock.systemUTC()); }
    OrderRoutes(Clock clock) { this.clock = clock; }

    @Bean
    RouterFunction<ServerResponse> configuredCommerceOrderRoutes(ReactiveExtensionClient client,
            InventoryService inventory, TemplateNameResolver templates, EpaySettingsProvider provider) {
        return commerceOrderRoutes(client, inventory, templates, provider.current());
    }

    RouterFunction<ServerResponse> commerceOrderRoutes(ReactiveExtensionClient client,
            InventoryService inventory, TemplateNameResolver templates, EpaySettings epaySettings) {
        return commerceOrderRoutes(client, inventory, templates, Mono.just(epaySettings));
    }

    private RouterFunction<ServerResponse> commerceOrderRoutes(ReactiveExtensionClient client,
            InventoryService inventory, TemplateNameResolver templates, Mono<EpaySettings> paymentSettings) {
        var routes = RouterFunctions.route()
            .POST("/shop/api/orders", request -> BuyerIdentity.resolve(request, true).flatMap(buyer -> request.bodyToMono(CreateOrderRequest.class)
                .switchIfEmpty(Mono.error(new ServerWebInputException("Order body is required")))
                .flatMap(body -> create(body, buyer, client, inventory))
                .flatMap(receipt -> ServerResponse.status(HttpStatus.CREATED).bodyValue(receipt))))
            .GET("/shop/api/orders", request -> BuyerIdentity.resolve(request, false).flatMap(buyer -> client.list(CommerceOrder.class,
                    order -> ownedBy(order, buyer), Comparator.comparing((CommerceOrder order) -> order.getSpec().createdAt()).reversed())
                .map(this::receipt).collectList().flatMap(orders -> ServerResponse.ok().bodyValue(orders))))
            .GET("/shop/api/orders/{name}", request -> BuyerIdentity.resolve(request, false).flatMap(buyer -> client.fetch(
                    CommerceOrder.class, request.pathVariable("name")).filter(order -> ownedBy(order, buyer))
                .flatMap(order -> ServerResponse.ok().bodyValue(order))
                .switchIfEmpty(ServerResponse.notFound().build())))
            .GET("/shop/api/orders/{name}/digital-delivery", request -> BuyerIdentity.resolve(request, false).flatMap(buyer ->
                digitalDelivery(request.pathVariable("name"), buyer, client)))
            .POST("/shop/api/orders/{name}/cancel", request -> BuyerIdentity.resolve(request, false).flatMap(buyer ->
                cancel(request.pathVariable("name"), buyer, client, inventory)))
            .GET("/shop/orders", request -> BuyerIdentity.resolve(request, false).flatMap(buyer -> client.list(CommerceOrder.class,
                    order -> ownedBy(order, buyer), Comparator.comparing((CommerceOrder order) -> order.getSpec().createdAt()).reversed())
                .collectList().flatMap(orders -> templates.resolveTemplateNameOrDefault(request.exchange(), "shop-orders")
                    .flatMap(template -> {
                        var digitalOrderNames = orders.stream().filter(order -> order.getSpec().state() == CommerceOrder.State.PAID
                                || order.getSpec().state() == CommerceOrder.State.FULFILLING
                                || order.getSpec().state() == CommerceOrder.State.COMPLETED)
                            .filter(order -> order.getSpec().lines().stream().anyMatch(line -> line.type() == Product.Type.DIGITAL))
                            .map(order -> order.getMetadata().getName()).collect(java.util.stream.Collectors.toUnmodifiableSet());
                        return paymentSettings.map(settings -> settings.forLocalRequest(request.uri())).flatMap(settings -> ServerResponse.ok().render(template, Map.of("orders", orders,
                            "digitalOrderNames", digitalOrderNames, "paymentConfigured", settings.configured(),
                            "alipayEnabled", settings.paymentEnabled("alipay"), "wxpayEnabled", settings.paymentEnabled("wxpay"),
                            "usdtEnabled", settings.paymentEnabled("usdt"))));
                    }))))
            .build();
        return routes.filter((request, next) -> next.handle(request)
            .onErrorResume(BuyerIdentity.LoginRequired.class, e -> ServerResponse.status(HttpStatus.UNAUTHORIZED).build())
            .onErrorResume(InventoryService.InsufficientInventory.class, e -> ServerResponse.status(HttpStatus.CONFLICT)
                .bodyValue(Map.of("message", "库存刚刚发生变化，请调整数量后重试。")))
            .onErrorResume(ServerWebInputException.class, e -> ServerResponse.badRequest()
                .bodyValue(Map.of("message", "订单信息不完整或无效。")))
            .onErrorResume(IllegalArgumentException.class, e -> ServerResponse.badRequest()
                .bodyValue(Map.of("message", "商品、数量或收货信息无效。")))
            .onErrorResume(IllegalStateException.class, e -> ServerResponse.status(HttpStatus.CONFLICT)
                .bodyValue(Map.of("message", "订单状态已变化，请刷新后重试。")))
            .onErrorResume(OrderNotFound.class, e -> ServerResponse.notFound().build()));
    }

    private Mono<ServerResponse> cancel(String name, String buyer, ReactiveExtensionClient client,
                                       InventoryService inventory) {
        if (!CommerceOrder.validName(name)) return ServerResponse.notFound().build();
        return client.fetch(CommerceOrder.class, name).filter(order -> ownedBy(order, buyer))
            .switchIfEmpty(Mono.error(new OrderNotFound()))
            .flatMap(order -> {
                var spec = order.getSpec();
                if (spec.state() == CommerceOrder.State.CANCELLED) return Mono.just(receipt(order));
                if (spec.state() != CommerceOrder.State.AWAITING_PAYMENT) {
                    return Mono.error(new IllegalStateException("Order cannot be cancelled in its current state"));
                }
                return releaseAll(name, spec.lines(), inventory)
                    .then(updateState(order, CommerceOrder.State.CANCELLED, client)).map(this::receipt);
            }).flatMap(receipt -> ServerResponse.ok().bodyValue(receipt));
    }

    private Mono<ServerResponse> digitalDelivery(String name, String buyer, ReactiveExtensionClient client) {
        if (!CommerceOrder.validName(name)) return ServerResponse.notFound().build();
        return client.fetch(CommerceOrder.class, name).filter(order -> ownedBy(order, buyer))
            .switchIfEmpty(Mono.error(new OrderNotFound()))
            .flatMap(order -> {
                var state = order.getSpec().state();
                if (state != CommerceOrder.State.PAID && state != CommerceOrder.State.FULFILLING
                    && state != CommerceOrder.State.COMPLETED) {
                    return Mono.error(new IllegalStateException("Digital delivery requires a paid order"));
                }
                var lines = order.getSpec().lines().stream().filter(line -> line.type() == Product.Type.DIGITAL).toList();
                return Flux.fromIterable(lines).concatMap(line -> client.fetch(Product.class, line.productName())
                    .map(product -> {
                        var status = product.getStatus();
                        var bySku = status == null ? Map.<String, List<String>>of()
                            : status.digitalDeliveries().getOrDefault(name, Map.of());
                        var codes = bySku.get(line.skuId());
                        if (codes == null || codes.size() != line.quantity()) {
                            throw new IllegalStateException("Digital delivery is not ready");
                        }
                        return new DigitalDelivery(line.productTitle(), line.skuTitle(), line.quantity(), codes);
                    }).switchIfEmpty(Mono.error(new IllegalStateException("Digital delivery is not ready"))))
                    .collectList().flatMap(deliveries -> ServerResponse.ok().bodyValue(deliveries));
            });
    }

    private Mono<OrderReceipt> create(CreateOrderRequest request, String buyer,
                                      ReactiveExtensionClient client, InventoryService inventory) {
        if (request == null || request.selections() == null || request.selections().isEmpty()
            || request.selections().size() > 100) {
            return Mono.error(new IllegalArgumentException("Cart is invalid"));
        }
        var selections = List.copyOf(request.selections());
        var names = selections.stream().map(CheckoutPricing.Selection::productName).distinct().toList();
        return Flux.fromIterable(names).flatMap(name -> client.fetch(Product.class, name)
            .filter(CatalogProjection::isPublished).switchIfEmpty(Mono.error(new IllegalArgumentException("Product unavailable"))), 8)
            .collectMap(product -> product.getMetadata().getName())
            .flatMap(catalog -> {
                if (BuyerIdentity.guest(buyer) && catalog.values().stream().anyMatch(product -> !BuyerIdentity.allowed(product))) {
                    return Mono.error(new BuyerIdentity.LoginRequired());
                }
                var quote = new CheckoutPricing().quote(selections, catalog, clock.instant());
                if (quote.requiresShipping() && request.shippingAddress() == null) {
                    return Mono.error(new IllegalArgumentException("Shipping address is required"));
                }
                var now = clock.instant();
                var expiresAt = now.plus(Duration.ofMinutes(15));
                var name = "order-" + UUID.randomUUID().toString().replace("-", "");
                var order = new CommerceOrder();
                var metadata = new Metadata(); metadata.setName(name); order.setMetadata(metadata);
                order.setSpec(new CommerceOrder.Spec(buyer, quote.lines(), quote.totalMinor(), "CNY",
                    quote.requiresShipping() ? request.shippingAddress() : null,
                    CommerceOrder.State.AWAITING_PAYMENT, now, expiresAt, null));
                return client.create(order).flatMap(created -> reserveAll(name, quote.lines(), expiresAt, inventory)
                    .thenReturn(receipt(created))
                    .onErrorResume(error -> compensate(name, quote.lines(), created, client, inventory)
                        .then(Mono.error(error))));
            });
    }

    private Mono<Void> reserveAll(String orderName, List<CheckoutPricing.Line> lines,
                                  Instant expiresAt, InventoryService inventory) {
        var byProduct = quantities(lines);
        return Flux.fromIterable(byProduct.entrySet())
            .concatMap(entry -> inventory.reserve(entry.getKey(), orderName, entry.getValue(), expiresAt))
            .then();
    }

    private Mono<Void> releaseAll(String orderName, List<CheckoutPricing.Line> lines, InventoryService inventory) {
        return Flux.fromIterable(quantities(lines).keySet())
            .concatMap(name -> inventory.release(name, orderName))
            .then();
    }

    private Mono<Void> compensate(String name, List<CheckoutPricing.Line> lines, CommerceOrder order,
                                  ReactiveExtensionClient client, InventoryService inventory) {
        return Flux.fromIterable(quantities(lines).keySet())
            .concatMap(product -> inventory.release(product, name).onErrorResume(error -> Mono.empty()))
            .then(updateState(order, CommerceOrder.State.RESERVATION_FAILED, client).then())
            .onErrorResume(error -> Mono.empty());
    }

    private Map<String, Map<String, Integer>> quantities(List<CheckoutPricing.Line> lines) {
        var result = new java.util.LinkedHashMap<String, Map<String, Integer>>();
        for (var line : lines) {
            var skus = new java.util.LinkedHashMap<>(result.getOrDefault(line.productName(), Map.of()));
            skus.merge(line.skuId(), line.quantity(), Math::addExact);
            result.put(line.productName(), Map.copyOf(skus));
        }
        return Map.copyOf(result);
    }

    private Mono<CommerceOrder> updateState(CommerceOrder order, CommerceOrder.State state,
                                            ReactiveExtensionClient client) {
        order.setSpec(order.getSpec().withState(state, order.getSpec().gatewayTradeNo()));
        return client.update(order);
    }

    private boolean ownedBy(CommerceOrder order, String buyer) {
        return order != null && order.getMetadata() != null && order.getSpec() != null
            && CommerceOrder.validName(order.getMetadata().getName()) && order.getSpec().buyer().equals(buyer);
    }

    private OrderReceipt receipt(CommerceOrder order) {
        var spec = order.getSpec();
        return new OrderReceipt(order.getMetadata().getName(), spec.state(), spec.expiresAt(), spec.totalMinor(), spec.currency());
    }

    private Mono<String> authenticated() {
        return ReactiveSecurityContextHolder.getContext().map(SecurityContext::getAuthentication)
            .filter(Authentication::isAuthenticated).filter(authentication -> !(authentication instanceof AnonymousAuthenticationToken))
            .map(Authentication::getName).filter(name -> name != null && !name.isBlank())
            .switchIfEmpty(Mono.error(new Unauthenticated()));
    }

    private static final class Unauthenticated extends RuntimeException {}
    private static final class OrderNotFound extends RuntimeException {}
}
