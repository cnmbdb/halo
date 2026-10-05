package local.halo.commerce;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.time.Instant;

/** Builds order line snapshots exclusively from the current server catalog. */
public final class CheckoutPricing {
    private static final long MAX_SAFE_AMOUNT = 9007199254740991L;
    private static final int MAX_LINES = 100;

    public record Selection(String productName, String skuId, int quantity) {
        public Selection {
            if (productName == null || productName.isBlank() || skuId == null || skuId.isBlank()
                || quantity < 1 || quantity > 10000) {
                throw new IllegalArgumentException("A selection requires product, SKU and quantity between 1 and 10000");
            }
        }
    }

    public record Line(String productName, String productTitle, Product.Type type,
                       String skuId, String skuTitle, long unitPriceMinor,
                       int quantity, long totalMinor) {}

    public record Quote(List<Line> lines, long totalMinor, boolean requiresShipping) {
        public Quote {
            lines = List.copyOf(lines);
        }
    }

    private record Key(String productName, String skuId) {}

    public Quote quote(List<Selection> selections, Map<String, Product> catalog) {
        return quote(selections, catalog, Instant.now());
    }

    public Quote quote(List<Selection> selections, Map<String, Product> catalog, Instant now) {
        if (selections == null || selections.isEmpty() || selections.size() > MAX_LINES) {
            throw new IllegalArgumentException("Checkout requires between 1 and 100 selections");
        }
        var quantities = new LinkedHashMap<Key, Integer>();
        for (var selection : selections) {
            if (selection == null) {
                throw new IllegalArgumentException("Selection must not be null");
            }
            var key = new Key(selection.productName(), selection.skuId());
            var quantity = Math.addExact(quantities.getOrDefault(key, 0), selection.quantity());
            if (quantity > 10000) {
                throw new IllegalArgumentException("Quantity limit exceeded");
            }
            quantities.put(key, quantity);
        }
        var lines = new ArrayList<Line>();
        long total = 0;
        boolean shipping = false;
        for (var entry : quantities.entrySet()) {
            var key = entry.getKey();
            var product = catalog.get(key.productName());
            if (product == null || product.getMetadata() == null
                || !key.productName().equals(product.getMetadata().getName())
                || product.getMetadata().getDeletionTimestamp() != null || product.getSpec() == null
                || product.getSpec().state() != Product.State.PUBLISHED) {
                throw new IllegalArgumentException("Product is unavailable");
            }
            var spec = product.getSpec();
            var sku = spec.skus().stream().filter(item -> item.id().equals(key.skuId()))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("SKU is unavailable"));
            var quantity = entry.getValue();
            if (new InventoryAvailability().available(product, sku.id(), now) < quantity) {
                throw new IllegalArgumentException("Insufficient stock");
            }
            long lineTotal = Math.multiplyExact(sku.priceMinor(), quantity);
            total = Math.addExact(total, lineTotal);
            if (total > MAX_SAFE_AMOUNT) {
                throw new IllegalArgumentException("Order amount exceeds supported range");
            }
            shipping |= spec.type() == Product.Type.PHYSICAL;
            lines.add(new Line(key.productName(), spec.title(), spec.type(), sku.id(), sku.title(),
                sku.priceMinor(), quantity, lineTotal));
        }
        return new Quote(lines, total, shipping);
    }
}
