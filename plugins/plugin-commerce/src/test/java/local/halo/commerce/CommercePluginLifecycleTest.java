package local.halo.commerce;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import run.halo.app.extension.SchemeManager;
import run.halo.app.plugin.PluginContext;

class CommercePluginLifecycleTest {
    @Test void stopIsSafeAfterHaloHasAlreadyRemovedPluginSchemes() {
        var schemes = mock(SchemeManager.class);
        when(schemes.schemes()).thenReturn(List.of());
        var plugin = new CommercePlugin(mock(PluginContext.class), schemes);

        assertDoesNotThrow(plugin::stop);
        verify(schemes, never()).unregister(org.mockito.ArgumentMatchers.any());
    }
}
