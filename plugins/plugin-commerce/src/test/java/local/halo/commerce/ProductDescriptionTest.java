package local.halo.commerce;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class ProductDescriptionTest {
    @Test void htmlKeepsFormattingAndRemovesExecutableElements() {
        var html = new ProductDescription().render("<h2>Title</h2><ul><li>One</li></ul><script>alert(1)</script><img src='/x.gif' onerror='alert(1)'><a href='javascript:alert(1)'>Link</a>", "html");
        assertTrue(html.contains("<h2>Title</h2>"));
        assertTrue(html.contains("<li>One</li>"));
        assertTrue(html.contains("/x.gif"));
        assertFalse(html.contains("script"));
        assertFalse(html.contains("onerror"));
        assertFalse(html.contains("javascript:"));
    }
    @Test void existingPlainDescriptionsRemainLiteralText() {
        var html = new ProductDescription().render("<b>literal</b>\nnext", "text");
        assertTrue(html.contains("&lt;b&gt;literal&lt;/b&gt;"));
        assertTrue(html.contains("whitespace-pre-wrap"));
    }
}
