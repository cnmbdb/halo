package local.halo.commerce;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.util.List;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.theme.TemplateNameResolver;

class CatalogRoutesTest {
    @Test void publicCatalogFiltersPublicationAndCategory() {
        var client = mock(ReactiveExtensionClient.class);
        var published = product("published", Product.State.PUBLISHED, "books");
        var draft = product("draft", Product.State.DRAFT, "books");
        var other = product("other", Product.State.PUBLISHED, "software");
        when(client.list(eq(Product.class), any(), any())).thenAnswer(call -> {
            Predicate<Product> predicate = call.getArgument(1);
            return Flux.fromIterable(List.of(published, draft, other)).filter(predicate);
        });
        var routes = new CatalogRoutes().commerceRoutes(client, mock(TemplateNameResolver.class));
        WebTestClient.bindToRouterFunction(routes).build().get()
            .uri("/shop/api/products?category=books").exchange().expectStatus().isOk()
            .expectBody().jsonPath("$.length()").isEqualTo(1)
            .jsonPath("$[0].name").isEqualTo("published")
            .jsonPath("$[0].skus[0].priceMinor").isEqualTo(1500)
            .jsonPath("$[0].metadata").doesNotExist();
    }
    private Product product(String name, Product.State state, String category) {
        var product = new Product();
        var metadata = new Metadata(); metadata.setName(name); product.setMetadata(metadata);
        product.setSpec(new Product.Spec(name, name, Product.Type.PHYSICAL, state, "Text", List.of(),
            List.of(category), List.of(new Product.Sku("sku", "Standard", 1500, 5)), false));
        return product;
    }
}
