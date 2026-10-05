package local.halo.commerce;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.thymeleaf.context.Context;
import org.thymeleaf.context.IExpressionContext;
import org.thymeleaf.linkbuilder.StandardLinkBuilder;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.StringTemplateResolver;
import run.halo.app.extension.Metadata;

class CommerceTemplateTest {
    private final CatalogProjection.Item product = new CatalogProjection.Item("sample", "Sample product", "sample", Product.Type.DIGITAL,
        "Description <script>alert(1)</script>", List.of("https://example.com/front.jpg", "https://example.com/back.jpg"), List.of("books"),
        List.of(new CatalogProjection.PublicSku("sku", "Standard", 1999, 2)), true);
    private String render(String name, Object home, Object detail) throws Exception {
        return render(name, home, detail, Map.of());
    }
    private String render(String name, Object home, Object detail, Map<String, Object> extra) throws Exception {
        var source = Files.readString(Path.of("../../development/theme-earth/src/" + name));
        var main = source.substring(source.indexOf("  <main"), source.lastIndexOf("  </main>") + "  </main>".length());
        var engine = new SpringTemplateEngine();
        var resolver = new StringTemplateResolver(); resolver.setTemplateMode("HTML");
        engine.setTemplateResolver(resolver);
        engine.setLinkBuilder(new StandardLinkBuilder() {
            @Override protected String computeContextPath(IExpressionContext context, String base, Map<String, Object> parameters) {
                return "";
            }
        });
        var context = new Context();
        var variables = new java.util.HashMap<String, Object>(Map.of("theme", Map.of("config", Map.of("commerce_home", home, "commerce_detail", detail)),
            "products", List.of(product), "product", product, "productCategories", List.of(),
            "orders", List.of(), "digitalOrderNames", java.util.Set.of(), "paymentConfigured", false,
            "catalogPresentation", new CatalogPresentation(), "quote", new CheckoutPricing.Quote(
                List.of(new CheckoutPricing.Line("sample", "Sample product", Product.Type.DIGITAL,
                    "sku", "Standard", 1999, 2, 3998)), 3998, false)));
        variables.put("descriptionHtml", new ProductDescription().render(product.description(), "text"));
        variables.putAll(extra);
        context.setVariables(variables);

        return engine.process(main, context);
    }
    @Test void homepageUsesModuleTitleAndFiltersFeaturedProducts() throws Exception {
        var output = render("shop.html", Map.of("title", "Custom shop", "modules", List.of(Map.of("enabled", true, "type", "featured", "title", "Featured", "limit", 1))), Map.of());
        assertFalse(output.contains("Custom shop")); assertTrue(output.contains("Featured")); assertTrue(output.contains("Sample product"));
        assertTrue(output.contains("19.99"));
    }
    @Test void homepageReadsHaloJsonSettingsAsPlainValues() throws Exception {
        var settings = new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(Map.of(
            "title", "Halo 商城", "description", "首页说明", "columns", "4",
            "modules", List.of(Map.of("enabled", true, "type", "products", "title", "四列商品"))));
        var output = render("shop.html", settings, Map.of());
        assertFalse(output.contains(">Halo 商城</h1>"));
        assertFalse(output.contains("四列商品"));
        assertTrue(output.contains("store-product-cover"));
        assertTrue(output.contains("store-product-copy"));
        assertFalse(output.contains("&quot;Halo 商城&quot;"));
        assertTrue(output.contains("lg:grid-cols-4"));
    }
    @Test void homepagePreservesModuleOrderAndOmitsDisabledModules() throws Exception {
        var output = render("shop.html", Map.of("modules", List.of(
            Map.of("type", "promotion", "title", "First module"),
            Map.of("type", "products", "title", "Hidden module", "enabled", false),
            Map.of("type", "banner", "title", "Last module", "slides", List.of(Map.of("image", "https://example.com/banner.jpg")))
        )), Map.of());
        assertTrue(output.indexOf("First module") < output.indexOf("store-hero"));
        assertFalse(output.contains("Hidden module"));
        assertTrue(output.contains("https://example.com/banner.jpg"));
    }
    @Test void detailSettingsControlSectionsAndButtonLabel() throws Exception {
        var output = render("shop-product.html", Map.of(), Map.of("purchase_label", "选择规格", "show_description", false,
            "show_after_sales", false, "show_related", false, "show_stock", false, "sticky_purchase", false));
        assertTrue(output.contains("选择规格")); assertFalse(output.contains("商品说明"));
        assertFalse(output.contains("售后说明")); assertFalse(output.contains("相关推荐"));
        assertFalse(output.contains("库存 2")); assertFalse(output.contains("lg:sticky"));
    }
    @Test void detailReadsHaloJsonSettingsForLayoutAndVisibility() throws Exception {
        var settings = new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(Map.of(
            "gallery_layout", "top", "purchase_label", "选择规格", "show_description", false,
            "show_after_sales", false, "show_related", false, "show_stock", false, "sticky_purchase", false));
        var output = render("shop-product.html", Map.of(), settings);
        assertTrue(output.contains("选择规格"));
        assertFalse(output.contains("&quot;选择规格&quot;"));
        assertFalse(output.contains("商品说明"));
        assertFalse(output.contains("售后说明"));
        assertFalse(output.contains("相关推荐"));
        assertFalse(output.contains("库存 2"));
        assertFalse(output.contains("lg:sticky"));
        assertFalse(output.contains("lg:grid-cols-2"));
    }
    @Test void checkoutShowsServerQuoteWithoutClaimingOrderCreation() throws Exception {
        var output = render("shop-checkout.html", Map.of(), Map.of());
        assertTrue(output.contains("39.98"));
        assertTrue(output.contains("Standard × 2"));
        assertTrue(output.contains("data-create-order"));
        assertTrue(output.contains("创建订单后可在订单页查看付款入口"));
    }
    @Test void detailPostsSelectedSkuAndQuantityToPreview() throws Exception {
        var output = render("shop-product.html", Map.of(), Map.of());
        assertTrue(output.contains("action=\"/shop/checkout\""));
        assertTrue(output.contains("name=\"product\" value=\"sample\""));
        assertTrue(output.contains("name=\"sku\" value=\"sku\""));
        assertTrue(output.contains("name=\"quantity\""));
    }
    @Test void mobilePurchaseBarFollowsStickyPurchaseSetting() throws Exception {
        var enabled = render("shop-product.html", Map.of(), Map.of("sticky_purchase", true));
        assertTrue(enabled.contains("data-mobile-purchase-bar"));
        assertTrue(enabled.contains("data-scroll-purchase"));
        assertTrue(enabled.contains("lg:hidden"));
        assertTrue(enabled.contains("data-purchase-options"));
        var disabled = render("shop-product.html", Map.of(), Map.of("sticky_purchase", false));
        assertFalse(disabled.contains("data-mobile-purchase-bar"));
        assertFalse(disabled.contains("pb-24"));
    }
    @Test void productGalleryRendersKeyboardAccessibleThumbnailControls() throws Exception {
        var output = render("shop-product.html", Map.of(), Map.of());
        assertTrue(output.contains("https://example.com/front.jpg"));
        assertTrue(output.contains("https://example.com/back.jpg"));
        assertTrue(output.contains("role=\"group\" aria-label=\"商品图片\""));
        assertTrue(output.contains("aria-label=\"查看第 1 张图片\" aria-pressed=\"true\""));
        assertTrue(output.contains("aria-label=\"查看第 2 张图片\" aria-pressed=\"false\""));
        assertEquals(2, output.split("data-gallery-image", -1).length - 1);
        assertEquals(2, output.split("data-gallery-thumbnail", -1).length - 1);
    }
    @Test void descriptionIsEscapedAndDefaultsRender() throws Exception {
        var output = render("shop-product.html", Map.of(), Map.of());
        assertTrue(output.contains("&lt;script&gt;")); assertFalse(output.contains("<script>alert"));
        assertTrue(output.contains("立即购买")); assertTrue(output.contains("库存 2"));
    }
    @Test void orderPaymentButtonsFollowGatewayConfiguration() throws Exception {
        var metadata = new Metadata(); metadata.setName("order-123");
        var order = new CommerceOrder(); order.setMetadata(metadata);
        order.setSpec(new CommerceOrder.Spec("buyer", List.of(new CheckoutPricing.Line("sample", "Sample product",
            Product.Type.DIGITAL, "sku", "Standard", 1999, 2, 3998)), 3998, "CNY", null,
            CommerceOrder.State.AWAITING_PAYMENT, Instant.parse("2026-10-02T00:00:00Z"),
            Instant.parse("2026-10-02T00:15:00Z"), null));
        var extra = Map.<String, Object>of("orders", List.of(order));
        var disabled = render("shop-orders.html", Map.of(), Map.of(), extra);
        assertTrue(disabled.contains("支付渠道尚未配置"));
        assertFalse(disabled.contains("type=alipay"));
        assertFalse(disabled.contains("type=wxpay"));
        var enabled = render("shop-orders.html", Map.of(), Map.of(), Map.of("orders", List.of(order), "paymentConfigured", true, "alipayEnabled", true, "wxpayEnabled", true));
        assertTrue(enabled.contains("type=alipay"));
        assertTrue(enabled.contains("type=wxpay"));
        assertFalse(enabled.contains("支付渠道尚未配置"));
        var single = render("shop-orders.html", Map.of(), Map.of(), Map.of("orders", List.of(order), "paymentConfigured", true, "alipayEnabled", false, "wxpayEnabled", true, "usdtEnabled", true));
        assertFalse(single.contains("type=alipay"));
        assertTrue(single.contains("type=wxpay"));
        assertTrue(single.contains("type=usdt"));
    }
}
