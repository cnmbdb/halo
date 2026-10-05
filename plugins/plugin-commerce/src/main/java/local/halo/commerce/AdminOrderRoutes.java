package local.halo.commerce;

import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;
import run.halo.app.extension.ReactiveExtensionClient;

@Configuration
public class AdminOrderRoutes {
    public record OrderPage(List<CommerceOrder> items, long total, int page, int size) {}
    public record ShipmentRequest(String carrier, String trackingNumber, boolean delivered) {}

    private final Clock clock;
    public AdminOrderRoutes() { this(Clock.systemUTC()); }
    AdminOrderRoutes(Clock clock) { this.clock = clock; }

    @Bean
    RouterFunction<ServerResponse> commerceAdminOrderRoutes(ReactiveExtensionClient client) {
        var routes = RouterFunctions.route()
            .GET("/shop/api/admin/orders", request -> manager().flatMap(ignored -> {
                int page;
                try { page = Integer.parseInt(request.queryParam("page").orElse("1")); }
                catch (NumberFormatException error) { return ServerResponse.badRequest().build(); }
                if (page < 1 || page > 100000) return ServerResponse.badRequest().build();
                var order = Comparator.comparing((CommerceOrder value) -> value.getSpec().createdAt()).reversed();
                var total = client.list(CommerceOrder.class, value -> true, order).count();
                var items = client.list(CommerceOrder.class, value -> true, order).skip((long) (page - 1) * 20)
                    .take(20).collectList();
                return Mono.zip(total, items).flatMap(result -> ServerResponse.ok().bodyValue(
                    new OrderPage(result.getT2(), result.getT1(), page, 20)));
            }))
            .POST("/shop/api/admin/orders/{name}/shipment", request -> manager().flatMap(ignored -> {
                var name = request.pathVariable("name");
                if (!CommerceOrder.validName(name)) return ServerResponse.notFound().build();
                return request.bodyToMono(ShipmentRequest.class).switchIfEmpty(Mono.error(new IllegalArgumentException()))
                    .flatMap(body -> client.fetch(CommerceOrder.class, name).switchIfEmpty(Mono.error(new MissingOrder()))
                        .flatMap(order -> {
                            var shipment = new CommerceOrder.Shipment(body.carrier(), body.trackingNumber(), body.delivered());
                            order.setSpec(order.getSpec().withShipment(shipment, clock.instant()));
                            return client.update(order);
                        }))
                    .flatMap(order -> ServerResponse.ok().bodyValue(order));
            }))
            .build();
        return routes.filter((request, next) -> next.handle(request)
            .onErrorResume(Unauthenticated.class, error -> ServerResponse.status(HttpStatus.UNAUTHORIZED).build())
            .onErrorResume(Forbidden.class, error -> ServerResponse.status(HttpStatus.FORBIDDEN).build())
            .onErrorResume(MissingOrder.class, error -> ServerResponse.notFound().build())
            .onErrorResume(IllegalArgumentException.class, error -> ServerResponse.badRequest()
                .bodyValue(Map.of("message", "物流信息格式无效。")))
            .onErrorResume(IllegalStateException.class, error -> ServerResponse.status(HttpStatus.CONFLICT)
                .bodyValue(Map.of("message", "订单当前状态不允许此物流更新。"))));
    }

    private Mono<String> manager() {
        return ReactiveSecurityContextHolder.getContext().map(SecurityContext::getAuthentication)
            .filter(Authentication::isAuthenticated)
            .filter(authentication -> !(authentication instanceof AnonymousAuthenticationToken))
            .switchIfEmpty(Mono.error(new Unauthenticated()))
            .filter(authentication -> authentication.getAuthorities().stream().anyMatch(authority ->
                authority.getAuthority().equals("commerce:orders:manage")
                    || authority.getAuthority().equals("ROLE_super-role")))
            .switchIfEmpty(Mono.error(new Forbidden())).map(Authentication::getName);
    }

    private static final class Unauthenticated extends RuntimeException {}
    private static final class Forbidden extends RuntimeException {}
    private static final class MissingOrder extends RuntimeException {}
}
