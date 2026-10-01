package run.halo.shop;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.shop.extension.Product;
import run.halo.shop.extension.ShopOrder;

@Component
@RequiredArgsConstructor
public class ShopService {
    private final ReactiveExtensionClient client;

    public Flux<ProductView> products() {
        return client.listAll(Product.class, ListOptions.builder().build(), Sort.unsorted())
            .filter(this::available)
            .map(this::toView);
    }

    public Mono<ProductView> product(String name) {
        return client.fetch(Product.class, name)
            .filter(this::available)
            .map(this::toView)
            .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)));
    }

    public Mono<OrderReceipt> placeOrder(OrderRequest request) {
        validate(request);
        return Flux.fromIterable(request.items())
            .concatMap(item -> client.fetch(Product.class, item.productName())
                .filter(this::available)
                .switchIfEmpty(Mono.error(new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Product is unavailable")))
                .map(product -> snapshot(product, item.quantity())))
            .collectList()
            .flatMap(items -> {
                long total;
                try {
                    total = items.stream().mapToLong(item ->
                        Math.multiplyExact(item.getUnitPriceCents(), item.getQuantity()))
                        .reduce(0L, Math::addExact);
                } catch (ArithmeticException e) {
                    return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Order total is too large"));
                }
                var order = new ShopOrder();
                var metadata = new Metadata();
                metadata.setGenerateName("shop-order-");
                order.setMetadata(metadata);
                var spec = new ShopOrder.Spec();
                spec.setCustomerName(request.customerName().trim());
                spec.setPhone(request.phone().trim());
                spec.setAddress(request.address().trim());
                spec.setItems(new ArrayList<>(items));
                spec.setTotalCents(total);
                spec.setStatus("PENDING_CONFIRMATION");
                spec.setCreatedAt(Instant.now());
                order.setSpec(spec);
                return client.create(order)
                    .map(created -> new OrderReceipt(created.getMetadata().getName(), total));
            });
    }

    private ShopOrder.Item snapshot(Product product, int quantity) {
        var spec = product.getSpec();
        if (spec.getPriceCents() < 0 || spec.getStock() < quantity) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Insufficient stock");
        }
        var item = new ShopOrder.Item();
        item.setProductName(product.getMetadata().getName());
        item.setTitle(spec.getTitle());
        item.setQuantity(quantity);
        item.setUnitPriceCents(spec.getPriceCents());
        return item;
    }

    private boolean available(Product product) {
        return product.getSpec() != null && product.getSpec().isPublished()
            && product.getSpec().getTitle() != null && !product.getSpec().getTitle().isBlank()
            && product.getSpec().getPriceCents() >= 0 && product.getSpec().getStock() >= 0
            && product.getMetadata().getDeletionTimestamp() == null;
    }

    private ProductView toView(Product product) {
        var spec = product.getSpec();
        return new ProductView(product.getMetadata().getName(), spec.getTitle(),
            spec.getDescription(), spec.getImageUrl(), spec.getPriceCents(), spec.getStock());
    }

    private void validate(OrderRequest request) {
        if (request == null || !bounded(request.customerName(), 100)
            || !bounded(request.phone(), 40) || !bounded(request.address(), 500)
            || request.items() == null || request.items().isEmpty()
            || request.items().size() > 20) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid order");
        }
        Set<String> names = new HashSet<>();
        for (var item : request.items()) {
            if (item == null || !bounded(item.productName(), 200)
                || item.quantity() < 1 || item.quantity() > 99
                || !names.add(item.productName())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid order item");
            }
        }
    }

    private boolean bounded(String value, int max) {
        return value != null && !value.isBlank() && value.length() <= max;
    }

    public record ProductView(String name, String title, String description,
                              String imageUrl, long priceCents, int stock) {}
    public record OrderItemRequest(String productName, int quantity) {}
    public record OrderRequest(String customerName, String phone, String address,
                               List<OrderItemRequest> items) {}
    public record OrderReceipt(String orderName, long totalCents) {}
}
