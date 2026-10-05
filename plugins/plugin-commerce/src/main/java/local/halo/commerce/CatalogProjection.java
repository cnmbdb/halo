package local.halo.commerce;

import java.util.List;

/** Explicit public representation; fulfillment secrets belong to separate private resources. */
public final class CatalogProjection {
    private CatalogProjection() {}
    public record PublicSku(String id, String title, long priceMinor, long stock) {}
    public record Item(String name, String title, String slug, Product.Type type,
                       String description, List<String> images, List<String> categories,
                       List<PublicSku> skus, boolean featured) {}
    public static boolean isPublished(Product product) {
        return product.getSpec() != null
            && product.getSpec().state() == Product.State.PUBLISHED
            && product.getMetadata() != null
            && product.getMetadata().getDeletionTimestamp() == null;
    }
    public static Item project(Product product) {
        return project(product, java.time.Instant.now());
    }
    static Item project(Product product, java.time.Instant now) {
        if (!isPublished(product)) throw new IllegalArgumentException("Product is not published");
        var spec = product.getSpec();
        return new Item(product.getMetadata().getName(), spec.title(), spec.slug(), spec.type(),
            spec.description(), safe(spec.images()), safe(spec.categories()),
            safe(spec.skus()).stream().map(sku -> new PublicSku(sku.id(), sku.title(), sku.priceMinor(),
                new InventoryAvailability().available(product, sku.id(), now))).toList(),
            spec.featured());
    }
    private static <T> List<T> safe(List<T> value) {
        return value == null ? List.of() : List.copyOf(value);
    }
}
