package dbrighthd.wildfiregendermodplugin.networking;

import dbrighthd.wildfiregendermodplugin.GenderModPlugin;
import dbrighthd.wildfiregendermodplugin.wildfire.ModConstants;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NetworkManagerTest {
    @Test
    void repliesToCompatibleV5Hello() throws IOException {
        GenderModPlugin plugin = mock(GenderModPlugin.class);
        Player player = mock(Player.class);
        when(player.getListeningPluginChannels()).thenReturn(Set.of(ModConstants.CLIENTBOUND_HELLO));
        NetworkManager manager = new NetworkManager(plugin);

        NetworkManager.HelloResult result = manager.handleHello(player, new byte[]{1});

        assertTrue(result.compatible());
        ArgumentCaptor<byte[]> response = ArgumentCaptor.forClass(byte[].class);
        verify(player).sendPluginMessage(eq(plugin), eq(ModConstants.CLIENTBOUND_HELLO), response.capture());
        assertArrayEquals(new byte[]{1}, response.getValue());
    }

    @Test
    void reportsMismatchedHelloAndRejectsTrailingData() throws IOException {
        GenderModPlugin plugin = mock(GenderModPlugin.class);
        Player player = mock(Player.class);
        when(player.getListeningPluginChannels()).thenReturn(Set.of());
        NetworkManager manager = new NetworkManager(plugin);

        assertFalse(manager.handleHello(player, new byte[]{2}).compatible());
        verify(player, never()).sendPluginMessage(any(), any(), any());
        assertThrows(IOException.class, () -> manager.handleHello(player, new byte[]{1, 0}));
    }
}
