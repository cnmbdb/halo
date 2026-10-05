package local.halo.commerce;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import org.springframework.dao.OptimisticLockingFailureException;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.ReactiveExtensionClient;

class DigitalInventoryTest {
    @Test void paidCommitConsumesCodesOnceAndStoresBuyerDeliveryByOrder() {
        var now = Instant.parse("2026-10-02T00:00:00Z");
        var product = new Product(); var metadata = new Metadata(); metadata.setName("ebook"); product.setMetadata(metadata);
        product.setSpec(new Product.Spec("Ebook", "ebook", Product.Type.DIGITAL, Product.State.PUBLISHED,
            "", List.of(), List.of(), List.of(new Product.Sku("license", "License", 500, 2,
            List.of("secret-one", "secret-two"))), false));
        product.setStatus(new Product.StockStatus(Map.of("order-123", new Product.Reservation(Map.of("license", 1),
            now.plusSeconds(900), Product.ReservationState.HELD, now))));
        var current = new AtomicReference<>(product);
        var client = mock(ReactiveExtensionClient.class);
        when(client.fetch(Product.class, "ebook")).thenAnswer(call -> Mono.just(current.get()));
        when(client.update(any(Product.class))).thenAnswer(call -> {
            var updated = call.getArgument(0, Product.class); current.set(updated); return Mono.just(updated);
        });
        var inventory = new InventoryService(client, Clock.fixed(now, ZoneOffset.UTC));

        inventory.commitPaid("ebook", "order-123").block();
        inventory.commitPaid("ebook", "order-123").block();

        assertEquals(1, current.get().getSpec().skus().getFirst().stock());
        assertEquals(List.of("secret-two"), current.get().getSpec().skus().getFirst().digitalCodes());
        assertEquals(List.of("secret-one"), current.get().getStatus().digitalDeliveries().get("order-123").get("license"));
        verify(client, times(1)).update(any(Product.class));
    }

    @Test void concurrentPaidCommitsAllocateTheReservedCodeOnlyOnce() {
        var now = Instant.parse("2026-10-02T00:00:00Z");
        var initial = new Product(); var metadata = new Metadata(); metadata.setName("ebook"); metadata.setVersion(4L);
        initial.setMetadata(metadata);
        initial.setSpec(new Product.Spec("Ebook", "ebook", Product.Type.DIGITAL, Product.State.PUBLISHED,
            "", List.of(), List.of(), List.of(new Product.Sku("license", "License", 500, 2,
            List.of("secret-one", "secret-two"))), false));
        initial.setStatus(new Product.StockStatus(Map.of("order-123", new Product.Reservation(Map.of("license", 1),
            now.plusSeconds(900), Product.ReservationState.HELD, now))));
        var current = new AtomicReference<>(initial);
        var fetchCount = new AtomicInteger();
        var bothInitialReads = new CyclicBarrier(2);
        var client = mock(ReactiveExtensionClient.class);
        when(client.fetch(Product.class, "ebook")).thenAnswer(call -> Mono.defer(() -> {
            var snapshot = copy(current.get());
            if (fetchCount.incrementAndGet() <= 2) {
                try { bothInitialReads.await(5, TimeUnit.SECONDS); }
                catch (Exception error) { return Mono.error(new IllegalStateException("Concurrent test barrier failed", error)); }
            }
            return Mono.just(snapshot);
        }));
        when(client.update(any(Product.class))).thenAnswer(call -> Mono.defer(() -> {
            var update = call.getArgument(0, Product.class);
            var stored = current.get();
            if (!java.util.Objects.equals(update.getMetadata().getVersion(), stored.getMetadata().getVersion())) {
                return Mono.error(new OptimisticLockingFailureException("simulated stale resource version"));
            }
            update.getMetadata().setVersion(update.getMetadata().getVersion() + 1);
            current.set(copy(update));
            return Mono.just(update);
        }));
        var inventory = new InventoryService(client, Clock.fixed(now, ZoneOffset.UTC));

        Mono.zip(
            inventory.commitPaid("ebook", "order-123").subscribeOn(Schedulers.parallel()),
            inventory.commitPaid("ebook", "order-123").subscribeOn(Schedulers.parallel())
        ).block();

        var stored = current.get();
        assertEquals(1, stored.getSpec().skus().getFirst().stock());
        assertEquals(List.of("secret-two"), stored.getSpec().skus().getFirst().digitalCodes());
        assertEquals(List.of("secret-one"), stored.getStatus().digitalDeliveries().get("order-123").get("license"));
        verify(client, times(2)).update(any(Product.class));
    }

    private Product copy(Product source) {
        var copy = new Product();
        var metadata = new Metadata(); metadata.setName(source.getMetadata().getName());
        metadata.setVersion(source.getMetadata().getVersion()); copy.setMetadata(metadata);
        copy.setSpec(source.getSpec());
        copy.setStatus(source.getStatus() == null ? null
            : new Product.StockStatus(source.getStatus().reservations(), source.getStatus().digitalDeliveries()));
        return copy;
    }
}
