package local.halo.commerce;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CatalogPresentationTest {
    private CatalogProjection.Item item(String name, String category, boolean featured) {
        return new CatalogProjection.Item(name, name, name, Product.Type.PHYSICAL, "Text", List.of(),
            List.of(category), List.of(new CatalogProjection.PublicSku("sku", "Standard", 100, 1)), featured);
    }
    @Test void filtersBeforeApplyingModuleLimit() {
        var items = List.of(item("excluded", "books", false), item("selected", "digital", true), item("second", "digital", true));
        var selected = new CatalogPresentation().select(items, Map.of("type", "featured", "category", "digital", "limit", 1));
        assertEquals(List.of("selected"), selected.stream().map(CatalogProjection.Item::name).toList());
    }
    @Test void relatedProductsExcludeCurrentAndUnrelatedCategories() {
        var current = item("current", "books", false);
        var items = List.of(current, item("unrelated", "digital", true), item("related", "books", false));
        assertEquals("related", new CatalogPresentation().related(items, current, 4).getFirst().name());
    }
    @Test void normalizesPartialModulesAndPreservesConfiguredOrder() {
        var modules = new CatalogPresentation().modules(Map.of("modules", List.of(
            Map.of("type", "promotion", "title", "First"), "invalid",
            Map.of("type", "unknown"), Map.of("type", "banner", "title", "Second"))));
        assertEquals(2, modules.size());
        var first = (Map<?, ?>) modules.get(0);
        var second = (Map<?, ?>) modules.get(1);
        assertEquals("First", first.get("title"));
        assertEquals("Second", second.get("title"));
        assertEquals(true, first.get("enabled"));
        assertEquals(List.of(), second.get("slides"));
    }
    @Test void nullCategoryDoesNotHideAllProducts() {
        var module = new java.util.HashMap<String, Object>(); module.put("category", null);
        assertEquals(1, new CatalogPresentation().select(List.of(item("one", "books", false)), module).size());
    }
    @Test void missingConfigProvidesDefaultModuleAndMalformedLimitUsesFallback() {
        assertEquals(1, new CatalogPresentation().modules(null).size());
        var items = List.of(item("one", "books", false));
        assertEquals(1, new CatalogPresentation().select(items, Map.of("limit", "invalid")).size());
    }

    @Test void readsModulesFromHaloJsonSettingsTree() {
        var settings = new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(Map.of("modules", List.of(
            Map.of("type", "banner", "enabled", true, "title", "精选"))));
        var modules = new CatalogPresentation().modules(settings);
        assertEquals(1, modules.size());
        assertEquals("banner", ((Map<?, ?>) modules.getFirst()).get("type"));
        assertEquals("精选", ((Map<?, ?>) modules.getFirst()).get("title"));
    }
}
