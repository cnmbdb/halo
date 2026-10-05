package local.halo.commerce;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;
import run.halo.app.extension.ReactiveExtensionClient;

/** Per-product atomic inventory reservations backed by Halo extension version checks. */
@Component
public final class InventoryService {
    private static final int MAX_RESERVATIONS = 10000;
    public static final class InsufficientInventory extends RuntimeException {}
    private final ReactiveExtensionClient client;
    private final Clock clock;

    @Autowired
    public InventoryService(ReactiveExtensionClient client) { this(client, Clock.systemUTC()); }
    InventoryService(ReactiveExtensionClient client, Clock clock) { this.client = client; this.clock = clock; }

    public Mono<Product> reserve(String productName, String orderName, Map<String, Integer> quantities,
                                 Instant expiresAt) {
        if (productName == null || productName.isBlank() || orderName == null
            || !orderName.matches("order-[a-z0-9-]{1,120}") || quantities == null || quantities.isEmpty()
            || quantities.size() > 100 || quantities.values().stream().anyMatch(q -> q == null || q < 1 || q > 10000)
            || expiresAt == null || !expiresAt.isAfter(clock.instant())
            || expiresAt.isAfter(clock.instant().plus(Duration.ofHours(24)))) {
            return Mono.error(new IllegalArgumentException("Invalid order inventory reservation"));
        }
        var immutableQuantities = Map.copyOf(quantities);
        return Mono.defer(() -> client.fetch(Product.class, productName)
            .switchIfEmpty(Mono.error(new IllegalArgumentException("Product unavailable")))
            .flatMap(product -> {
                if (!CatalogProjection.isPublished(product)) return Mono.error(new IllegalArgumentException("Product unavailable"));
                var previous = reservations(product);
                var existing = previous.get(orderName);
                if (existing != null) {
                    if (existing.state() == Product.ReservationState.HELD
                        && existing.quantities().equals(immutableQuantities) && existing.expiresAt().isAfter(clock.instant())) {
                        return Mono.just(product);
                    }
                    return Mono.error(new IllegalStateException("Order already has an inventory reservation"));
                }
                var availability = new InventoryAvailability();
                for (var entry : immutableQuantities.entrySet()) {
                    if (availability.available(product, entry.getKey(), clock.instant()) < entry.getValue()) {
                        return Mono.error(new InsufficientInventory());
                    }
                }
                pruneTerminal(previous);
                if (previous.size() >= MAX_RESERVATIONS) {
                    return Mono.error(new IllegalStateException("Reservation history limit reached"));
                }
                previous.put(orderName, new Product.Reservation(immutableQuantities, expiresAt,
                    Product.ReservationState.HELD, clock.instant()));
                product.setStatus(new Product.StockStatus(previous));
                return client.update(product);
            }))
            .retryWhen(Retry.backoff(8, Duration.ofMillis(25))
                .filter(OptimisticLockingFailureException.class::isInstance));
    }

    public Mono<Product> release(String productName, String orderName) {
        return transition(productName, orderName, Product.ReservationState.RELEASED);
    }

    public Mono<Product> commitPaid(String productName, String orderName) {
        return transition(productName, orderName, Product.ReservationState.COMMITTED);
    }

    private Mono<Product> transition(String productName, String orderName, Product.ReservationState target) {
        return Mono.defer(() -> client.fetch(Product.class, productName)
            .switchIfEmpty(Mono.error(new IllegalArgumentException("Product unavailable")))
            .flatMap(product -> {
                var status = reservations(product);
                var reservation = status.get(orderName);
                if (reservation == null) return Mono.error(new IllegalArgumentException("Reservation unavailable"));
                if (reservation.state() == target) return Mono.just(product);
                if (reservation.state() != Product.ReservationState.HELD) {
                    return Mono.error(new IllegalStateException("Reservation is already finalized"));
                }
                var now = clock.instant();
                if (target == Product.ReservationState.COMMITTED && !reservation.expiresAt().isAfter(now)) {
                    return Mono.error(new IllegalStateException("Reservation expired before payment confirmation"));
                }
                if (target == Product.ReservationState.COMMITTED) {
                    var spec = product.getSpec();
                    var codesBySku = new HashMap<String, java.util.List<String>>();
                    var updatedSkus = spec.skus().stream().map(sku -> {
                        var quantity = reservation.quantities().getOrDefault(sku.id(), 0);
                        if (quantity > sku.stock()) throw new IllegalStateException("Reserved stock is inconsistent");
                        var remainingCodes = sku.digitalCodes();
                        if (spec.type() == Product.Type.DIGITAL && quantity > 0) {
                            if (sku.digitalCodes().size() < quantity) {
                                throw new IllegalStateException("Reserved digital delivery codes are inconsistent");
                            }
                            codesBySku.put(sku.id(), List.copyOf(sku.digitalCodes().subList(0, quantity)));
                            remainingCodes = List.copyOf(sku.digitalCodes().subList(quantity, sku.digitalCodes().size()));
                        }
                        return new Product.Sku(sku.id(), sku.title(), sku.priceMinor(), sku.stock() - quantity,
                            remainingCodes);
                    }).toList();
                    product.setSpec(new Product.Spec(spec.title(), spec.slug(), spec.type(), spec.state(),
                        spec.description(), spec.images(), spec.categories(), updatedSkus, spec.featured()));
                    if (!codesBySku.isEmpty()) {
                        var deliveries = new HashMap<>(product.getStatus() == null ? Map.<String, Map<String, List<String>>>of()
                            : product.getStatus().digitalDeliveries());
                        if (deliveries.putIfAbsent(orderName, Map.copyOf(codesBySku)) != null) {
                            throw new IllegalStateException("Digital delivery has already been allocated");
                        }
                        product.setStatus(new Product.StockStatus(status, deliveries));
                    }
                }
                status.put(orderName, new Product.Reservation(reservation.quantities(), reservation.expiresAt(), target, now));
                var deliveries = product.getStatus() == null ? Map.<String, Map<String, List<String>>>of()
                    : product.getStatus().digitalDeliveries();
                product.setStatus(new Product.StockStatus(status, deliveries));
                return client.update(product);
            }))
            .retryWhen(Retry.backoff(8, Duration.ofMillis(25))
                .filter(OptimisticLockingFailureException.class::isInstance));
    }

    private Map<String, Product.Reservation> reservations(Product product) {
        return new HashMap<>(product.getStatus() == null ? Map.of() : product.getStatus().reservations());
    }

    private void pruneTerminal(Map<String, Product.Reservation> reservations) {
        var cutoff = clock.instant().minus(Duration.ofDays(30));
        reservations.entrySet().removeIf(entry -> entry.getValue().state() != Product.ReservationState.HELD
            && entry.getValue().updatedAt().isBefore(cutoff));
    }
}
