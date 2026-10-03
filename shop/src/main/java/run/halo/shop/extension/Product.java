package run.halo.shop.extension;

import lombok.Data;
import lombok.EqualsAndHashCode;
import run.halo.app.extension.AbstractExtension;
import run.halo.app.extension.GVK;

@Data
@EqualsAndHashCode(callSuper = true)
@GVK(group = "shop.cnmbdb.github.io", version = "v1alpha1", kind = "Product", plural = "products", singular = "product")
public class Product extends AbstractExtension {
    private Spec spec;

    @Data
    public static class Spec {
        private String title;
        private String description;
        private String imageUrl;
        private long priceCents;
        private int stock;
        private boolean published;
    }
}
