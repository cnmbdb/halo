package local.halo.commerce;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProductValidationTest {
    @Test void rejectsNegativePriceAndStock() {
        assertThrows(IllegalArgumentException.class, () -> new Product.Sku("id", "Title", -1, 1));
        assertThrows(IllegalArgumentException.class, () -> new Product.Sku("id", "Title", 1, -1));
    }
    @Test void rejectsMissingOrDuplicateSkus() {
        assertThrows(IllegalArgumentException.class, () -> spec(List.of()));
        var sku = new Product.Sku("id", "Title", 100, 1);
        assertThrows(IllegalArgumentException.class, () -> spec(List.of(sku, sku)));
    }
    @Test void rejectsUnsafeProductPath() {
        assertThrows(IllegalArgumentException.class, () -> new Product.Spec("Title", "../admin", Product.Type.PHYSICAL,
            Product.State.DRAFT, "", List.of(), List.of(), List.of(new Product.Sku("id", "Title", 100, 1)), false));
    }
    @Test void publishedDigitalSkuMustHaveEnoughUniqueCodesForStock() {
        assertThrows(IllegalArgumentException.class, () -> new Product.Spec("Title", "title", Product.Type.DIGITAL,
            Product.State.PUBLISHED, "", List.of(), List.of(), List.of(new Product.Sku("id", "Title", 100, 2)), false));
        assertThrows(IllegalArgumentException.class, () -> new Product.Spec("Title", "title", Product.Type.DIGITAL,
            Product.State.DRAFT, "", List.of(), List.of(), List.of(
                new Product.Sku("one", "One", 100, 1, List.of("reused")),
                new Product.Sku("two", "Two", 100, 1, List.of("reused"))), false));
    }
    private Product.Spec spec(List<Product.Sku> skus) {
        return new Product.Spec("Title", "title", Product.Type.DIGITAL, Product.State.DRAFT, "", List.of(), List.of(), skus, false);
    }
}
