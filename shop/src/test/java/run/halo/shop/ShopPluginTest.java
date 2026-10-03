package run.halo.shop;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import run.halo.app.extension.SchemeManager;
import run.halo.app.plugin.PluginContext;
import run.halo.shop.extension.Product;
import run.halo.shop.extension.ShopOrder;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class ShopPluginTest {

    @Mock
    PluginContext context;

    @Mock
    SchemeManager schemes;

    @InjectMocks
    ShopPlugin plugin;

    @Test
    void contextLoads() {
        plugin.start();
        verify(schemes).register(Product.class);
        verify(schemes).register(ShopOrder.class);
        plugin.stop();
        verify(schemes, times(2)).unregister(any());
    }
}
