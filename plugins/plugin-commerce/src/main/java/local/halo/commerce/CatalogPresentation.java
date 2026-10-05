package local.halo.commerce;

import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Set;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Applies display configuration without changing catalog data. */
public final class CatalogPresentation {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public List<?> modules(Object rawHome) {
        var home = settings(rawHome);
        if (home != null && home.get("modules") instanceof List<?> modules) {
            return modules.stream().filter(Map.class::isInstance).map(value -> {
                var raw = (Map<?, ?>) value;
                var result = new LinkedHashMap<String, Object>();
                raw.forEach((key, item) -> {
                    if (key instanceof String name && item != null) result.put(name, item);
                });
                result.putIfAbsent("type", "products");
                result.putIfAbsent("enabled", true);
                result.putIfAbsent("title", "");
                result.putIfAbsent("description", "");
                result.put("slides", raw.get("slides") instanceof List<?> slides
                    ? slides.stream().filter(Map.class::isInstance).toList() : List.of());
                return result;
            }).filter(module -> Set.of("banner", "categories", "featured", "products", "promotion")
                .contains(module.get("type"))).toList();
        }
        return List.of(Map.of("type", "products", "title", "全部商品", "enabled", true, "limit", 8));
    }

    public Map<String, Object> settings(Object value) {
        if (value instanceof JsonNode node && node.isObject()) {
            return OBJECT_MAPPER.convertValue(node, new TypeReference<>() {});
        }
        if (value instanceof Map<?, ?> raw) {
            var result = new LinkedHashMap<String, Object>();
            raw.forEach((key, item) -> {
                if (key instanceof String name) result.put(name, item);
            });
            return result;
        }
        return null;
    }

    public List<CatalogProjection.Item> select(List<CatalogProjection.Item> products, Map<String, Object> module) {
        var category = module.get("category") instanceof String value ? value.trim() : "";
        var featuredOnly = "featured".equals(module.get("type"));
        return products.stream()
            .filter(p -> !featuredOnly || p.featured())
            .filter(p -> category.isBlank() || p.categories().contains(category))
            .limit(limit(module.get("limit"), 8, 48)).toList();
    }
    public List<CatalogProjection.Item> related(List<CatalogProjection.Item> products,
            CatalogProjection.Item current, Object count) {
        return products.stream().filter(p -> !p.name().equals(current.name()))
            .filter(p -> current.categories().isEmpty()
                || p.categories().stream().anyMatch(current.categories()::contains))
            .limit(limit(count, 4, 12)).toList();
    }
    private long limit(Object value, int fallback, int maximum) {
        try {
            return Math.max(1, Math.min(maximum, Long.parseLong(String.valueOf(value))));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
