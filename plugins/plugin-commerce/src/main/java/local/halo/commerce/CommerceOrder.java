package local.halo.commerce;

import java.time.Instant;
import java.util.List;
import java.util.regex.Pattern;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import run.halo.app.extension.AbstractExtension;
import run.halo.app.extension.GVK;

@GVK(group = "commerce.halo.run", version = "v1alpha1", kind = "CommerceOrder",
    plural = "orders", singular = "order")
public class CommerceOrder extends AbstractExtension {
    private static final Pattern ORDER_NAME = Pattern.compile("order-[a-z0-9-]{1,120}");
    private Spec spec;
    public Spec getSpec() { return spec; }
    public void setSpec(Spec spec) { this.spec = spec; }

    public enum State { AWAITING_PAYMENT, PAID, CANCELLED, EXPIRED, FULFILLING, COMPLETED, REFUND_REQUIRED, REFUNDED, RESERVATION_FAILED }
    public enum FulfillmentState { AWAITING_PAYMENT, PROCESSING, DIGITAL_DELIVERED, SHIPPED, DELIVERED }
    public record FulfillmentLine(int lineIndex, String productName, String skuId, Product.Type type,
                                  FulfillmentState state, String carrier, String trackingNumber,
                                  Instant updatedAt) {}
    public record Shipment(String carrier, String trackingNumber, boolean delivered) {
        public Shipment {
            if (carrier == null || carrier.isBlank() || carrier.length() > 80
                || trackingNumber == null || !trackingNumber.matches("[A-Za-z0-9._/-]{1,100}")) {
                throw new IllegalArgumentException("A valid carrier and tracking number are required");
            }
        }
    }
    public record ShippingAddress(@NotBlank String recipient, @NotBlank String phone, @NotBlank String address) {
        public ShippingAddress {
            if (recipient == null || recipient.isBlank() || recipient.length() > 80
                || phone == null || !phone.matches("[0-9+() -]{6,32}")
                || address == null || address.isBlank() || address.length() > 500) {
                throw new IllegalArgumentException("A valid shipping recipient, phone and address are required");
            }
        }
    }
    public record Spec(@NotBlank String buyer, @NotNull List<CheckoutPricing.Line> lines,
                       long totalMinor, @NotBlank String currency, ShippingAddress shippingAddress,
                       @NotNull State state, @NotNull Instant createdAt, @NotNull Instant expiresAt,
                       String gatewayTradeNo, List<FulfillmentLine> fulfillment) {
        public Spec(String buyer, List<CheckoutPricing.Line> lines, long totalMinor, String currency,
                    ShippingAddress shippingAddress, State state, Instant createdAt, Instant expiresAt,
                    String gatewayTradeNo) {
            this(buyer, lines, totalMinor, currency, shippingAddress, state, createdAt, expiresAt,
                gatewayTradeNo, null);
        }
        public Spec {
            if (buyer == null || buyer.isBlank() || lines == null || lines.isEmpty() || lines.size() > 100
                || totalMinor <= 0 || totalMinor > 9007199254740991L || !"CNY".equals(currency)
                || state == null || createdAt == null || expiresAt == null || !expiresAt.isAfter(createdAt)
                || expiresAt.isAfter(createdAt.plusSeconds(86400))
                || lines.stream().anyMatch(line -> line == null || line.quantity() < 1 || line.quantity() > 10000)) {
                throw new IllegalArgumentException("Invalid commerce order snapshot");
            }
            long computedTotal = 0;
            for (var line : lines) {
                if (line == null || line.productName() == null || line.productTitle() == null
                    || line.type() == null || line.skuId() == null || line.skuTitle() == null
                    || line.unitPriceMinor() < 0 || line.quantity() < 1 || line.quantity() > 10000
                    || line.totalMinor() != Math.multiplyExact(line.unitPriceMinor(), line.quantity())) {
                    throw new IllegalArgumentException("Invalid order line snapshot");
                }
                computedTotal = Math.addExact(computedTotal, line.totalMinor());
            }
            if (computedTotal != totalMinor) {
                throw new IllegalArgumentException("Order total must equal its immutable line snapshots");
            }
            if (lines.stream().anyMatch(line -> line.type() == Product.Type.PHYSICAL) && shippingAddress == null) {
                throw new IllegalArgumentException("Physical goods require a shipping address");
            }
            if (gatewayTradeNo != null && gatewayTradeNo.length() > 160) {
                throw new IllegalArgumentException("Invalid gateway transaction identifier");
            }
            lines = List.copyOf(lines);
            fulfillment = fulfillment == null ? initialFulfillment(lines, state, createdAt) : List.copyOf(fulfillment);
            if (fulfillment.size() != lines.size()) throw new IllegalArgumentException("Fulfillment must track every order line");
            for (int i = 0; i < lines.size(); i++) {
                var line = lines.get(i); var status = fulfillment.get(i);
                if (status == null || status.lineIndex() != i || !line.productName().equals(status.productName())
                    || !line.skuId().equals(status.skuId()) || line.type() != status.type() || status.state() == null
                    || status.updatedAt() == null) {
                    throw new IllegalArgumentException("Fulfillment line must match the immutable order line");
                }
                if (line.type() == Product.Type.DIGITAL && (status.state() == FulfillmentState.SHIPPED
                    || status.state() == FulfillmentState.DELIVERED || status.carrier() != null || status.trackingNumber() != null)) {
                    throw new IllegalArgumentException("Digital lines cannot have shipment tracking");
                }
            }
        }
        public Spec withState(State next, String tradeNo) {
            if (next == State.PAID) {
                var statuses = new java.util.ArrayList<FulfillmentLine>();
                for (int i = 0; i < lines.size(); i++) {
                    var line = lines.get(i);
                    statuses.add(new FulfillmentLine(i, line.productName(), line.skuId(), line.type(),
                        line.type() == Product.Type.DIGITAL ? FulfillmentState.DIGITAL_DELIVERED : FulfillmentState.PROCESSING,
                        null, null, Instant.now()));
                }
                next = lines.stream().anyMatch(line -> line.type() == Product.Type.PHYSICAL)
                    ? State.FULFILLING : State.COMPLETED;
                return new Spec(buyer, lines, totalMinor, currency, shippingAddress, next, createdAt, expiresAt,
                    tradeNo, statuses);
            }
            return new Spec(buyer, lines, totalMinor, currency, shippingAddress, next, createdAt, expiresAt,
                tradeNo, fulfillment);
        }
        public Spec withShipment(Shipment shipment, Instant now) {
            if (state != State.FULFILLING && state != State.PAID) {
                throw new IllegalStateException("Only paid orders can be fulfilled");
            }
            boolean anyPhysical = fulfillment.stream().anyMatch(line -> line.type() == Product.Type.PHYSICAL);
            if (!anyPhysical) throw new IllegalStateException("Order has no physical items");
            if (shipment.delivered() && fulfillment.stream().filter(line -> line.type() == Product.Type.PHYSICAL)
                .anyMatch(line -> line.state() != FulfillmentState.SHIPPED && line.state() != FulfillmentState.DELIVERED)) {
                throw new IllegalStateException("Physical items must be shipped before delivery can be confirmed");
            }
            var updated = fulfillment.stream().map(line -> {
                if (line.type() == Product.Type.DIGITAL) return line;
                if (line.state() == FulfillmentState.DELIVERED && !shipment.delivered()) {
                    throw new IllegalStateException("Delivered items cannot return to shipped state");
                }
                return new FulfillmentLine(line.lineIndex(), line.productName(), line.skuId(), line.type(),
                    shipment.delivered() ? FulfillmentState.DELIVERED : FulfillmentState.SHIPPED,
                    shipment.carrier(), shipment.trackingNumber(), now);
            }).toList();
            boolean complete = updated.stream().allMatch(line -> line.state() == FulfillmentState.DIGITAL_DELIVERED
                || line.state() == FulfillmentState.DELIVERED);
            return new Spec(buyer, lines, totalMinor, currency, shippingAddress,
                complete ? State.COMPLETED : State.FULFILLING, createdAt, expiresAt, gatewayTradeNo, updated);
        }

        private static List<FulfillmentLine> initialFulfillment(List<CheckoutPricing.Line> lines, State state, Instant now) {
            boolean paid = state == State.PAID || state == State.FULFILLING || state == State.COMPLETED;
            return java.util.stream.IntStream.range(0, lines.size()).mapToObj(index -> {
                var line = lines.get(index);
                var lineState = !paid ? FulfillmentState.AWAITING_PAYMENT
                    : line.type() == Product.Type.DIGITAL ? FulfillmentState.DIGITAL_DELIVERED :
                        state == State.COMPLETED ? FulfillmentState.DELIVERED : FulfillmentState.PROCESSING;
                return new FulfillmentLine(index, line.productName(), line.skuId(), line.type(), lineState, null, null, now);
            }).toList();
        }
    }

    public static boolean validName(String name) { return name != null && ORDER_NAME.matcher(name).matches(); }
}
