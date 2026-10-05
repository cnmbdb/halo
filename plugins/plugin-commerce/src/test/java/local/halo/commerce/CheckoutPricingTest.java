package local.halo.commerce;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import run.halo.app.extension.Metadata;

class CheckoutPricingTest {
    private final CheckoutPricing pricing = new CheckoutPricing();

    @Test void pricesMixedCartFromServerAndSnapshotsNames() {
        var physical = product("physical", Product.Type.PHYSICAL, 1999, 10);
        var digital = product("digital", Product.Type.DIGITAL, 500, 10);
        var quote = pricing.quote(List.of(selection("physical", 2), selection("digital", 1)),
            Map.of("physical", physical, "digital", digital));
        assertEquals(4498, quote.totalMinor());
        assertTrue(quote.requiresShipping());
        physical.setSpec(new Product.Spec("Changed", "changed", Product.Type.PHYSICAL,
            Product.State.PUBLISHED, "", List.of(), List.of(), List.of(new Product.Sku("sku", "Changed", 1, 1)), false));
        assertEquals("physical title", quote.lines().getFirst().productTitle());
        assertEquals(1999, quote.lines().getFirst().unitPriceMinor());
        assertThrows(UnsupportedOperationException.class, () -> quote.lines().clear());
    }
    @Test void digitalCartDoesNotRequireShipping() {
        var quote = pricing.quote(List.of(selection("digital", 1)),
            Map.of("digital", product("digital", Product.Type.DIGITAL, 500, 1)));
        assertFalse(quote.requiresShipping());
    }
    @Test void duplicateSelectionsCannotBypassStockCheck() {
        assertThrows(IllegalArgumentException.class, () -> pricing.quote(
            List.of(selection("p", 3), selection("p", 3)), Map.of("p", product("p", Product.Type.PHYSICAL, 100, 5))));
        var quote = pricing.quote(List.of(selection("p", 2), selection("p", 1)),
            Map.of("p", product("p", Product.Type.PHYSICAL, 100, 5)));
        assertEquals(1, quote.lines().size());
        assertEquals(3, quote.lines().getFirst().quantity());
    }
    @Test void rejectsUnavailableProductAndUnknownSku() {
        var p = product("p", Product.Type.PHYSICAL, 100, 1);
        assertThrows(IllegalArgumentException.class, () -> pricing.quote(
            List.of(new CheckoutPricing.Selection("p", "foreign", 1)), Map.of("p", p)));
        assertThrows(IllegalArgumentException.class, () -> pricing.quote(List.of(selection("missing", 1)), Map.of("p", p)));
        var spec = p.getSpec();
        p.setSpec(new Product.Spec(spec.title(), spec.slug(), spec.type(), Product.State.DRAFT,
            spec.description(), spec.images(), spec.categories(), spec.skus(), spec.featured()));
        assertThrows(IllegalArgumentException.class, () -> pricing.quote(List.of(selection("p", 1)), Map.of("p", p)));
    }
    @Test void rejectsInvalidCartAndQuantity() {
        assertThrows(IllegalArgumentException.class, () -> pricing.quote(List.of(), Map.of()));
        assertThrows(IllegalArgumentException.class, () -> selection("p", 0));
        assertThrows(IllegalArgumentException.class, () -> selection("p", 10001));
        assertThrows(IllegalArgumentException.class, () -> pricing.quote(List.of(selection("p", 6000), selection("p", 6000)), Map.of()));
    }
    @Test void rejectsUnsafeAmountsAndArithmeticOverflow() {
        var p = product("p", Product.Type.DIGITAL, 9007199254740991L, 10000);
        assertThrows(IllegalArgumentException.class, () -> pricing.quote(List.of(selection("p", 2)), Map.of("p", p)));
        assertThrows(ArithmeticException.class, () -> pricing.quote(List.of(selection("p", 10000)), Map.of("p", p)));
    }
    private CheckoutPricing.Selection selection(String name, int quantity) {
        return new CheckoutPricing.Selection(name, "sku", quantity);
    }
    private Product product(String name, Product.Type type, long price, long stock) {
        var p = new Product();
        var metadata = new Metadata(); metadata.setName(name); p.setMetadata(metadata);
        var codes = type == Product.Type.DIGITAL
            ? java.util.stream.IntStream.range(0, Math.toIntExact(stock)).mapToObj(i -> "code-" + i).toList() : List.<String>of();
        p.setSpec(new Product.Spec(name + " title", name, type, Product.State.PUBLISHED,
            "", List.of(), List.of(), List.of(new Product.Sku("sku", "Standard", price, stock, codes)), false));
        return p;
    }
}
