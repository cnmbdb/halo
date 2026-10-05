package local.halo.commerce;

import java.time.Instant;

public final class InventoryAvailability {
    public long available(Product product, String skuId, Instant now) {
        if (product == null || product.getSpec() == null || now == null) {
            throw new IllegalArgumentException("Product and current time are required");
        }
        var sku = product.getSpec().skus().stream().filter(item -> item.id().equals(skuId))
            .findFirst().orElseThrow(() -> new IllegalArgumentException("SKU is unavailable"));
        long held = 0;
        var status = product.getStatus();
        if (status != null) {
            for (var reservation : status.reservations().values()) {
                if (reservation.state() == Product.ReservationState.HELD && reservation.expiresAt().isAfter(now)) {
                    held = Math.addExact(held, reservation.quantities().getOrDefault(skuId, 0));
                }
            }
        }
        return Math.max(0, sku.stock() - held);
    }
}
