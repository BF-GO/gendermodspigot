package dbrighthd.wildfiregendermodplugin.listeners;

import dbrighthd.wildfiregendermodplugin.GenderModPlugin;
import dbrighthd.wildfiregendermodplugin.logging.CustomPluginLogger;
import dbrighthd.wildfiregendermodplugin.networking.InboundPacketGuard;
import dbrighthd.wildfiregendermodplugin.networking.SyncStateCoordinator;
import dbrighthd.wildfiregendermodplugin.wildfire.UserManager;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerQuitEvent;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConnectionListenerTest {
    @Test
    @SuppressWarnings("deprecation")
    void quitClearsProfileNegotiationTrackingAndRateLimitState() {
        GenderModPlugin plugin = mock(GenderModPlugin.class);
        SyncStateCoordinator coordinator = mock(SyncStateCoordinator.class);
        InboundPacketGuard guard = mock(InboundPacketGuard.class);
        UserManager userManager = mock(UserManager.class);
        CustomPluginLogger logger = mock(CustomPluginLogger.class);
        Player player = mock(Player.class);
        UUID playerId = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.getName()).thenReturn("Tester");
        when(plugin.getSyncStateCoordinator()).thenReturn(coordinator);
        when(plugin.getInboundPacketGuard()).thenReturn(guard);
        when(plugin.getUserManager()).thenReturn(userManager);
        when(plugin.getCustomLogger()).thenReturn(logger);

        new ConnectionListener(plugin).onPlayerQuit(new PlayerQuitEvent(player, Component.empty()));

        verify(coordinator).remove(player);
        verify(guard).remove(playerId);
        verify(userManager).remove(playerId);
    }
}
