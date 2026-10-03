package run.halo.shop;

import org.springframework.stereotype.Component;
import run.halo.app.extension.Scheme;
import run.halo.app.extension.SchemeManager;
import run.halo.app.plugin.BasePlugin;
import run.halo.app.plugin.PluginContext;
import run.halo.shop.extension.Product;
import run.halo.shop.extension.ShopOrder;

/**
 * <p>Plugin main class to manage the lifecycle of the plugin.</p>
 * <p>This class must be public and have a public constructor.</p>
 * <p>Only one main class extending {@link BasePlugin} is allowed per plugin.</p>
 *
 * @author HF
 * @since 1.0.0
 */
@Component
public class ShopPlugin extends BasePlugin {

    private final SchemeManager schemes;

    public ShopPlugin(PluginContext context, SchemeManager schemes) {
        super(context);
        this.schemes = schemes;
    }

    @Override
    public void start() {
        schemes.register(Product.class);
        schemes.register(ShopOrder.class);
    }

    @Override
    public void stop() {
        schemes.unregister(Scheme.buildFromType(ShopOrder.class));
        schemes.unregister(Scheme.buildFromType(Product.class));
    }
}
