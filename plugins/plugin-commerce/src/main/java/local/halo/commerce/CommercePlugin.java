package local.halo.commerce;

import org.springframework.stereotype.Component;
import run.halo.app.extension.SchemeManager;
import run.halo.app.plugin.BasePlugin;
import run.halo.app.plugin.PluginContext;

@Component
public class CommercePlugin extends BasePlugin {
    private final SchemeManager schemes;
    public CommercePlugin(PluginContext context, SchemeManager schemes) {
        super(context);
        this.schemes = schemes;
    }
    @Override
    public void start() {
        schemes.register(Product.class);
        schemes.register(ProductCategory.class);
        schemes.register(CommerceOrder.class);
    }
    @Override
    public void stop() {
        unregisterIfPresent(CommerceOrder.class);
        unregisterIfPresent(ProductCategory.class);
        unregisterIfPresent(Product.class);
    }

    private void unregisterIfPresent(Class<? extends run.halo.app.extension.Extension> type) {
        schemes.schemes().stream().filter(scheme -> scheme.type().equals(type)).findFirst()
            .ifPresent(schemes::unregister);
    }
}
