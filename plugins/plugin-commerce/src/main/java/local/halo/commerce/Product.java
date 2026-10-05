package local.halo.commerce;

import java.util.List;
import java.util.Map;
import java.time.Instant;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import run.halo.app.extension.AbstractExtension;
import run.halo.app.extension.GVK;

@GVK(group = "commerce.halo.run", version = "v1alpha1", kind = "Product", plural = "products", singular = "product")
public class Product extends AbstractExtension {
    private Spec spec;
    public Spec getSpec() { return spec; }
    public void setSpec(Spec spec) { this.spec = spec; }
    private StockStatus status;
    public StockStatus getStatus() { return status; }
    public void setStatus(StockStatus status) { this.status = status; }
    public enum ReservationState { HELD, RELEASED, COMMITTED }
    public record Reservation(Map<String, Integer> quantities, Instant expiresAt,
                              ReservationState state, Instant updatedAt) {
        public Reservation {
            quantities = quantities == null ? Map.of() : Map.copyOf(quantities);
            if (quantities.isEmpty() || quantities.values().stream().anyMatch(q -> q == null || q < 1 || q > 10000)
                || expiresAt == null || state == null || updatedAt == null) {
                throw new IllegalArgumentException("Invalid inventory reservation");
            }
        }
    }
    public record StockStatus(Map<String, Reservation> reservations,
                              Map<String, Map<String, List<String>>> digitalDeliveries) {
        public StockStatus(Map<String, Reservation> reservations) { this(reservations, Map.of()); }
        public StockStatus {
            reservations = reservations == null ? Map.of() : Map.copyOf(reservations);
            if (digitalDeliveries == null) {
                digitalDeliveries = Map.of();
            } else {
                var deliveries = new java.util.HashMap<String, Map<String, List<String>>>();
                digitalDeliveries.forEach((order, skus) -> deliveries.put(order,
                    skus == null ? Map.of() : skus.entrySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                        Map.Entry::getKey, entry -> List.copyOf(entry.getValue())))));
                digitalDeliveries = Map.copyOf(deliveries);
            }
        }
    }

    public enum Type { PHYSICAL, DIGITAL }
    public enum State { DRAFT, PUBLISHED, ARCHIVED }
    public record Sku(@NotBlank String id, @NotBlank String title,
                      @Min(0) long priceMinor, @Min(0) long stock, List<String> digitalCodes) {
        public Sku(String id, String title, long priceMinor, long stock) {
            this(id, title, priceMinor, stock, List.of());
        }
        public Sku {
            if (id == null || id.isBlank() || title == null || title.isBlank()
                || priceMinor < 0 || priceMinor > 9007199254740991L || stock < 0 || stock > 9007199254740991L) {
                throw new IllegalArgumentException("SKU requires valid identifiers and nonnegative safe integer amounts");
            }
            digitalCodes = digitalCodes == null ? List.of() : List.copyOf(digitalCodes);
            if (digitalCodes.size() > 100000 || digitalCodes.stream().anyMatch(code -> code == null || code.isBlank()
                || code.length() > 4096)) {
                throw new IllegalArgumentException("Digital delivery codes must be nonblank and at most 4096 characters");
            }
        }
    }
    public record Spec(@NotBlank String title, @NotBlank String slug,
                       @NotNull Type type, @NotNull State state,
                       String description, List<String> images,
                       List<String> categories, List<Sku> skus,
                       boolean featured) {
        public Spec {
            if (title == null || title.isBlank() || slug == null
                || !slug.matches("[a-z0-9]+(?:-[a-z0-9]+)*") || type == null || state == null) {
                throw new IllegalArgumentException("Product title, slug, type and state are required");
            }
            if (skus == null || skus.isEmpty()) {
                throw new IllegalArgumentException("At least one SKU is required");
            }
            var ids = new java.util.HashSet<String>();
            for (var sku : skus) {
                if (sku == null || !ids.add(sku.id())) {
                    throw new IllegalArgumentException("SKU identifiers must be unique");
                }
            }
            var codeSet = new java.util.HashSet<String>();
            for (var sku : skus) {
                for (var code : sku.digitalCodes()) {
                    if (!codeSet.add(code)) throw new IllegalArgumentException("Digital delivery codes must be unique");
                }
                if (type == Type.DIGITAL && state == State.PUBLISHED && sku.digitalCodes().size() < sku.stock()) {
                    throw new IllegalArgumentException("Published digital SKU stock cannot exceed its delivery codes");
                }
            }
            skus = List.copyOf(skus);
            images = images == null ? List.of() : List.copyOf(images);
            categories = categories == null ? List.of() : List.copyOf(categories);
        }
    }
}
