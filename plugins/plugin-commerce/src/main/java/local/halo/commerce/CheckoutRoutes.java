package local.halo.commerce;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.theme.TemplateNameResolver;

@Configuration
public class CheckoutRoutes {
    @Bean
    RouterFunction<ServerResponse> commerceCheckoutRoutes(ReactiveExtensionClient client,
                                                          TemplateNameResolver templates) {
        return RouterFunctions.route()
            .GET("/shop/cart", request -> templates.resolveTemplateNameOrDefault(request.exchange(), "shop-cart")
                .flatMap(template -> ServerResponse.ok().render(template)))
            .GET("/shop/checkout", request -> quote(request, client)
                .flatMap(quote -> templates.resolveTemplateNameOrDefault(request.exchange(), "shop-checkout")
                    .flatMap(template -> ServerResponse.ok().render(template, Map.of("quote", quote))))
                .switchIfEmpty(ServerResponse.notFound().build())
                .onErrorResume(UnavailableProduct.class, e -> ServerResponse.notFound().build())
                .onErrorResume(IllegalArgumentException.class, e -> invalid()))
            .GET("/shop/api/quote", request -> quote(request, client)
                .flatMap(quote -> ServerResponse.ok().bodyValue(quote))
                .switchIfEmpty(ServerResponse.notFound().build())
                .onErrorResume(UnavailableProduct.class, e -> ServerResponse.notFound().build())
                .onErrorResume(IllegalArgumentException.class, e -> invalid()))
            .build();
    }

    private Mono<ServerResponse> invalid() {
        return ServerResponse.badRequest().bodyValue(Map.of("message", "商品或数量无效，请返回商品页重新选择。"));
    }

    private static final class UnavailableProduct extends RuntimeException {}

    private Mono<CheckoutPricing.Quote> quote(ServerRequest request, ReactiveExtensionClient client) {
        return Mono.defer(() -> {
            var names = request.queryParams().getOrDefault("product", List.of());
            var skus = request.queryParams().getOrDefault("sku", List.of());
            var quantities = request.queryParams().getOrDefault("quantity", names.size() == 1 ? List.of("1") : List.of());
            if (names.isEmpty() || names.size() > 100 || names.size() != skus.size()
                || names.size() != quantities.size()) {
                return Mono.error(new IllegalArgumentException("Invalid cart"));
            }
            var selections = new ArrayList<CheckoutPricing.Selection>();
            for (int index = 0; index < names.size(); index++) {
                var quantity = quantities.get(index);
                if (!quantity.matches("[0-9]{1,5}")) {
                    return Mono.error(new IllegalArgumentException("Invalid quantity"));
                }
                selections.add(new CheckoutPricing.Selection(names.get(index), skus.get(index), Integer.parseInt(quantity)));
            }
            return Flux.fromIterable(names).distinct().flatMap(name -> client.fetch(Product.class, name)
                    .filter(CatalogProjection::isPublished).switchIfEmpty(Mono.error(new UnavailableProduct())), 8)
                .collectMap(product -> product.getMetadata().getName())
                .map(catalog -> new CheckoutPricing().quote(selections, catalog, java.time.Instant.now()));
        });
    }
}
