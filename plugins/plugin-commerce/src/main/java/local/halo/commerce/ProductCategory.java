package local.halo.commerce;

import jakarta.validation.constraints.NotBlank;
import run.halo.app.extension.AbstractExtension;
import run.halo.app.extension.GVK;

@GVK(group = "commerce.halo.run", version = "v1alpha1", kind = "ProductCategory", plural = "productcategories", singular = "productcategory")
public class ProductCategory extends AbstractExtension {
    private Spec spec;
    public Spec getSpec() { return spec; }
    public void setSpec(Spec spec) { this.spec = spec; }
    public record Spec(@NotBlank String title, @NotBlank String slug, String description, int priority) {
        public Spec {
            if (title == null || title.isBlank()) {
                throw new IllegalArgumentException("Category title is required");
            }
            if (slug == null || !slug.matches("[a-z0-9]+(?:-[a-z0-9]+)*")) {
                throw new IllegalArgumentException("Category slug is invalid");
            }
        }
    }
}
