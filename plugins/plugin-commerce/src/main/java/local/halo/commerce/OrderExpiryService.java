package local.halo.commerce;

import java.time.Clock;
import java.util.Comparator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.app.extension.ReactiveExtensionClient;

@Component
public class OrderExpiryService {
    private static final Logger log = LoggerFactory.getLogger(OrderExpiryService.class);
    private final ReactiveExtensionClient client;
    private final InventoryService inventory;
    private final Clock clock;

    @Autowired
    public OrderExpiryService(ReactiveExtensionClient client, InventoryService inventory) {
        this(client, inventory, Clock.systemUTC());
    }
    OrderExpiryService(ReactiveExtensionClient client, InventoryService inventory, Clock clock) {
        this.client = client; this.inventory = inventory; this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${commerce.order.expiry-scan-ms:60000}")
    public Mono<Void> expireOrders() {
        var now = clock.instant();
        return client.list(CommerceOrder.class, order -> order.getSpec() != null
                && order.getSpec().state() == CommerceOrder.State.AWAITING_PAYMENT
                && !order.getSpec().expiresAt().isAfter(now),
                Comparator.comparing(order -> order.getSpec().expiresAt()))
            .concatMap(this::expireOne, 1)
            .then();
    }

    private Mono<Void> expireOne(CommerceOrder candidate) {
        var name = candidate.getMetadata().getName();
        return client.fetch(CommerceOrder.class, name)
            .flatMap(order -> {
                if (order.getSpec().state() != CommerceOrder.State.AWAITING_PAYMENT
                    || order.getSpec().expiresAt().isAfter(clock.instant())) return Mono.empty();
                order.setSpec(order.getSpec().withState(CommerceOrder.State.EXPIRED, null));
                return client.update(order).flatMap(expired -> Flux.fromIterable(expired.getSpec().lines()
                        .stream().map(CheckoutPricing.Line::productName).distinct().toList())
                    .concatMap(product -> inventory.release(product, name)).then());
            })
            .doOnError(error -> log.warn("Could not expire commerce order {}: {}", name, error.getClass().getSimpleName()))
            .onErrorResume(error -> Mono.empty());
    }
}
