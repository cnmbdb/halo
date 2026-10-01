package run.halo.shop.extension;

import java.time.Instant;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import run.halo.app.extension.AbstractExtension;
import run.halo.app.extension.GVK;

@Data
@EqualsAndHashCode(callSuper = true)
@GVK(group = "shop.cnmbdb.github.io", version = "v1alpha1", kind = "ShopOrder", plural = "shoporders", singular = "shoporder")
public class ShopOrder extends AbstractExtension {
    private Spec spec;

    @Data
    public static class Spec {
        private String customerName;
        private String phone;
        private String address;
        private List<Item> items;
        private long totalCents;
        private String status;
        private Instant createdAt;
    }

    @Data
    public static class Item {
        private String productName;
        private String title;
        private int quantity;
        private long unitPriceCents;
    }
}
