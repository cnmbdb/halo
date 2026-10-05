package local.halo.commerce;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import run.halo.app.extension.Metadata;

class CatalogProjectionTest {
    private Product product(Product.State state) {
        var product = new Product();
        var metadata = new Metadata();
        metadata.setName("sample");
        product.setMetadata(metadata);
        var codes = state == Product.State.PUBLISHED ? List.of("code-a", "code-b") : List.<String>of();
        product.setSpec(new Product.Spec("Sample", "sample", Product.Type.DIGITAL, state,
            "Description", null, null, List.of(new Product.Sku("default", "Standard", 1999, 2, codes)), true));
        return product;
    }
    @Test void draftAndArchivedProductsCannotBeProjected() {
        for (var state : List.of(Product.State.DRAFT, Product.State.ARCHIVED)) {
            var p = product(state);
            assertFalse(CatalogProjection.isPublished(p));
            assertThrows(IllegalArgumentException.class, () -> CatalogProjection.project(p));
        }
    }
    @Test void deletingProductsAreExcluded() {
        var p = product(Product.State.PUBLISHED);
        p.getMetadata().setDeletionTimestamp(Instant.now());
        assertFalse(CatalogProjection.isPublished(p));
    }
    @Test void publicProductRetainsIntegerPriceAndNormalizesCollections() {
        var result = CatalogProjection.project(product(Product.State.PUBLISHED));
        assertEquals(1999, result.skus().getFirst().priceMinor());
        assertEquals(Product.Type.DIGITAL, result.type());
        assertTrue(result.images().isEmpty());
        assertTrue(result.categories().isEmpty());
        assertEquals(CatalogProjection.PublicSku.class, result.skus().getFirst().getClass());
        assertFalse(java.util.Arrays.stream(result.skus().getFirst().getClass().getRecordComponents())
            .anyMatch(component -> component.getName().equals("digitalCodes")));
        assertThrows(UnsupportedOperationException.class, () -> result.skus().clear());
    }
    @Test void showsStockAfterActiveReservationsAndRestoresAfterExpiry() {
        var p = product(Product.State.PUBLISHED);
        var now = Instant.parse("2026-10-03T00:00:00Z");
        p.setStatus(new Product.StockStatus(java.util.Map.of("order-test", new Product.Reservation(java.util.Map.of("default", 1), now.plusSeconds(60), Product.ReservationState.HELD, now))));
        assertEquals(1, CatalogProjection.project(p, now).skus().getFirst().stock());
        assertEquals(2, CatalogProjection.project(p, now.plusSeconds(61)).skus().getFirst().stock());
    }
}
