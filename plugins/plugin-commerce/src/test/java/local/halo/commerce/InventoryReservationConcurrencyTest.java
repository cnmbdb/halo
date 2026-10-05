package local.halo.commerce;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.ReactiveExtensionClient;

class InventoryReservationConcurrencyTest {
    @Test void twoOrdersCannotReserveTheSameLastStockUnit() {
        var now = Instant.parse("2026-10-02T00:00:00Z");
        var initial = new Product();
        var metadata = new Metadata(); metadata.setName("last-item"); metadata.setVersion(4L);
        initial.setMetadata(metadata);
        initial.setSpec(new Product.Spec("Last item", "last-item", Product.Type.PHYSICAL, Product.State.PUBLISHED,
            "", List.of(), List.of(), List.of(new Product.Sku("sku", "Standard", 100, 1)), false));
        var current = new AtomicReference<>(initial);
        var fetchCount = new AtomicInteger();
        var bothInitialReads = new CyclicBarrier(2);
        var client = mock(ReactiveExtensionClient.class);
        when(client.fetch(Product.class, "last-item")).thenAnswer(call -> Mono.defer(() -> {
            var snapshot = copy(current.get());
            if (fetchCount.incrementAndGet() <= 2) {
                try { bothInitialReads.await(5, TimeUnit.SECONDS); }
                catch (Exception error) { return Mono.error(new IllegalStateException("Reservation test barrier failed", error)); }
            }
            return Mono.just(snapshot);
        }));
        when(client.update(any(Product.class))).thenAnswer(call -> Mono.defer(() -> {
            var update = call.getArgument(0, Product.class);
            var stored = current.get();
            if (!java.util.Objects.equals(update.getMetadata().getVersion(), stored.getMetadata().getVersion())) {
                return Mono.error(new OptimisticLockingFailureException("simulated stale resource version"));
            }
            var persisted = copy(update);
            persisted.getMetadata().setVersion(stored.getMetadata().getVersion() + 1);
            if (!current.compareAndSet(stored, persisted)) {
                return Mono.error(new OptimisticLockingFailureException("simultaneous resource update"));
            }
            update.getMetadata().setVersion(persisted.getMetadata().getVersion());
            return Mono.just(update);
        }));
        var inventory = new InventoryService(client, Clock.fixed(now, ZoneOffset.UTC));
        var expiry = now.plusSeconds(900);

        var outcomes = Flux.merge(
            inventory.reserve("last-item", "order-first", java.util.Map.of("sku", 1), expiry)
                .thenReturn("reserved").onErrorResume(InventoryService.InsufficientInventory.class,
                    error -> Mono.just("unavailable")).subscribeOn(Schedulers.parallel()),
            inventory.reserve("last-item", "order-second", java.util.Map.of("sku", 1), expiry)
                .thenReturn("reserved").onErrorResume(InventoryService.InsufficientInventory.class,
                    error -> Mono.just("unavailable")).subscribeOn(Schedulers.parallel())
        ).collectList().block(java.time.Duration.ofSeconds(10));

        assertNotNull(outcomes);
        assertEquals(1, outcomes.stream().filter("reserved"::equals).count());
        assertEquals(1, outcomes.stream().filter("unavailable"::equals).count());
        assertEquals(1, current.get().getStatus().reservations().size());
        assertEquals(1, current.get().getSpec().skus().getFirst().stock());
        verify(client, times(2)).update(any(Product.class));
    }

    private Product copy(Product source) {
        var copy = new Product();
        var metadata = new Metadata(); metadata.setName(source.getMetadata().getName());
        metadata.setVersion(source.getMetadata().getVersion()); copy.setMetadata(metadata);
        copy.setSpec(source.getSpec());
        copy.setStatus(source.getStatus() == null ? null : new Product.StockStatus(source.getStatus().reservations()));
        return copy;
    }
}
