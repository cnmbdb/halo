package local.halo.commerce;

import java.util.Comparator;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.theme.TemplateNameResolver;

@Configuration
public class CatalogRoutes {
    private static boolean matchesCategory(Product product, String category) {
        return category.isEmpty() || (product.getSpec().categories() != null
            && product.getSpec().categories().contains(category));
    }

    @Bean
    RouterFunction<ServerResponse> commerceRoutes(ReactiveExtensionClient client, TemplateNameResolver templates) {
        return RouterFunctions.route()
            .GET("/shop", request -> Mono.zip(
                client.list(Product.class, p -> CatalogProjection.isPublished(p) && matchesCategory(p, request.queryParam("category").orElse("")),
                    Comparator.comparing(p -> p.getSpec().title())).map(CatalogProjection::project).collectList(),
                client.list(ProductCategory.class, c -> c.getSpec() != null
                    && c.getMetadata().getDeletionTimestamp() == null,
                    Comparator.comparingInt(c -> c.getSpec().priority())).collectList())
                .flatMap(data -> templates.resolveTemplateNameOrDefault(request.exchange(), "shop")
                    .flatMap(template -> ServerResponse.ok().render(template,
                        Map.of("products", data.getT1(), "productCategories", data.getT2(), "catalogPresentation", new CatalogPresentation())))))
            .GET("/shop/products/{slug}", request -> client.list(Product.class,
                    CatalogProjection::isPublished, Comparator.comparing(p -> p.getSpec().title()))
                .collectList()
                .flatMap(rawProducts -> {
                    var products = rawProducts.stream().map(CatalogProjection::project).toList();
                    var matches = products.stream().filter(p -> p.slug().equals(request.pathVariable("slug"))).toList();
                    if (matches.isEmpty()) return ServerResponse.notFound().build();
                    if (matches.size() > 1) return ServerResponse.status(409).build();
                    return templates.resolveTemplateNameOrDefault(request.exchange(), "shop-product")
                        .flatMap(template -> ServerResponse.ok().render(template,
                            Map.of("product", matches.getFirst(), "products", products,
                                "descriptionHtml", new ProductDescription().render(matches.getFirst().description(), rawProducts.stream().filter(p -> p.getMetadata().getName().equals(matches.getFirst().name())).findFirst().map(p -> p.getMetadata().getAnnotations() == null ? "text" : p.getMetadata().getAnnotations().getOrDefault("commerce.halo.run/description-format", "text")).orElse("text")),
                                "catalogPresentation", new CatalogPresentation())));
                }))
            .GET("/shop/api/products", request -> ServerResponse.ok().body(
                client.list(Product.class, p -> CatalogProjection.isPublished(p) && matchesCategory(p, request.queryParam("category").orElse("")),
                    Comparator.comparing(p -> p.getSpec().title())).map(CatalogProjection::project), CatalogProjection.Item.class))
            .build();
    }
}
