package local.halo.commerce;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.theme.TemplateNameResolver;

class CheckoutRoutesTest {
    private ReactiveExtensionClient client;
    private WebTestClient http;
    private Product product;
    @BeforeEach void setup() {
        client = mock(ReactiveExtensionClient.class);
        product = new Product();
        var metadata = new Metadata(); metadata.setName("book"); product.setMetadata(metadata);
        product.setSpec(new Product.Spec("Book", "book", Product.Type.DIGITAL, Product.State.PUBLISHED,
            "", List.of(), List.of(), List.of(new Product.Sku("default", "Standard", 1999, 2, List.of("code-1", "code-2"))), false));
        when(client.fetch(eq(Product.class), anyString())).thenAnswer(call ->
            "book".equals(call.getArgument(1)) ? Mono.just(product) : Mono.empty());
        http = WebTestClient.bindToRouterFunction(new CheckoutRoutes()
            .commerceCheckoutRoutes(client, mock(TemplateNameResolver.class))).build();
    }
    @Test void quotesCurrentServerPriceAndIgnoresClientPrice() {
        http.get().uri("/shop/api/quote?product=book&sku=default&quantity=2&priceMinor=1").exchange()
            .expectStatus().isOk().expectBody().jsonPath("$.totalMinor").isEqualTo(3998)
            .jsonPath("$.requiresShipping").isEqualTo(false)
            .jsonPath("$.lines[0].unitPriceMinor").isEqualTo(1999);
    }
    @Test void quotesRepeatedProductsAndRejectsCombinedOverstock() {
        http.get().uri("/shop/api/quote?product=book&sku=default&quantity=1&product=book&sku=default&quantity=1")
            .exchange().expectStatus().isOk().expectBody().jsonPath("$.lines.length()").isEqualTo(1)
            .jsonPath("$.totalMinor").isEqualTo(3998);
        http.get().uri("/shop/api/quote?product=book&sku=default&quantity=2&product=book&sku=default&quantity=1")
            .exchange().expectStatus().isBadRequest();
        verify(client, times(2)).fetch(Product.class, "book");
    }
    @Test void rejectsUnbalancedCartAndDoesNotReturnPartialQuotes() {
        http.get().uri("/shop/api/quote?product=book&sku=default&quantity=1&product=book")
            .exchange().expectStatus().isBadRequest();
        http.get().uri("/shop/api/quote?product=book&sku=default&quantity=1&product=missing&sku=default&quantity=1")
            .exchange().expectStatus().isNotFound();
    }
    @Test void rejectsInsufficientStockAndMalformedQuantity() {
        for (var quantity : new String[] { "3", "0", "-1", "1.5", "10001", "abc" }) {
            http.get().uri("/shop/api/quote?product=book&sku=default&quantity=" + quantity)
                .exchange().expectStatus().isBadRequest();
        }
    }
    @Test void unavailableCatalogEntriesAreNotExposed() {
        http.get().uri("/shop/api/quote?product=missing&sku=default").exchange().expectStatus().isNotFound();
        var spec = product.getSpec();
        product.setSpec(new Product.Spec(spec.title(), spec.slug(), spec.type(), Product.State.DRAFT,
            "", List.of(), List.of(), spec.skus(), false));
        http.get().uri("/shop/api/quote?product=book&sku=default").exchange().expectStatus().isNotFound();
    }
}
