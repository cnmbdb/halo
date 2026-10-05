package local.halo.commerce;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class ProductCategoryValidationTest {
    @Test void rejectsBlankTitleAndUnsafeSlug() {
        assertThrows(IllegalArgumentException.class, () -> new ProductCategory.Spec(" ", "books", "", 0));
        assertThrows(IllegalArgumentException.class, () -> new ProductCategory.Spec("Books", "../admin", "", 0));
    }
    @Test void supportsNegativePriorityForPinnedCategories() {
        var spec = new ProductCategory.Spec("Books", "digital-books", "Description", -10);
        assertEquals(-10, spec.priority());
        assertEquals("digital-books", spec.slug());
    }
}
