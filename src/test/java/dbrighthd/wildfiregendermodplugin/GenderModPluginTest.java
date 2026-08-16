package dbrighthd.wildfiregendermodplugin;

import dbrighthd.wildfiregendermodplugin.networking.NetworkManager;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GenderModPluginTest {
    @Test
    void invalidProtocolDisablesPluginWithoutRegisteringAnything() {
        NetworkManager networkManager = mock(NetworkManager.class);
        Runnable disablePlugin = mock(Runnable.class);
        Runnable registerPlugin = mock(Runnable.class);
        when(networkManager.init()).thenReturn(false);

        GenderModPlugin.runStartup(networkManager, disablePlugin, registerPlugin);

        verify(disablePlugin).run();
        verify(registerPlugin, never()).run();
    }
}
