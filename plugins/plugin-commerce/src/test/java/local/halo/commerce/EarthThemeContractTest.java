package local.halo.commerce;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class EarthThemeContractTest {
    @Test void earthDeclaresBothCommerceDecorationGroupsAndTheRequiredLayout() throws Exception {
        var settings = Files.readString(Path.of("../../development/theme-earth/settings.yaml"));
        var layout = Path.of("../../development/theme-earth/src/layout.html");
        assertTrue(Files.isRegularFile(layout), "Earth must ship the Halo templates/layout.html contract");
        for (var field : new String[] {
            "group: commerce_home", "name: modules", "name: columns",
            "group: commerce_detail", "name: gallery_layout", "name: sticky_purchase",
            "name: after_sales", "name: related_limit",
            "if: \"$value.type === 'banner'\"",
            "if: \"$value.type === 'promotion'\"",
            "if: \"$value.type === 'featured' || $value.type === 'products'\"",
            "if: \"$get(show_after_sales).value\"",
            "if: \"$get(show_related).value\""
        }) {
            assertTrue(settings.contains(field), "Missing Earth theme decoration setting: " + field);
        }
    }
}
