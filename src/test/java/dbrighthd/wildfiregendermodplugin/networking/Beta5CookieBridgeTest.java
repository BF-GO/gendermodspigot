package dbrighthd.wildfiregendermodplugin.networking;

import dbrighthd.wildfiregendermodplugin.GenderModPlugin;
import dbrighthd.wildfiregendermodplugin.logging.CustomPluginLogger;
import dbrighthd.wildfiregendermodplugin.scheduler.TaskDispatcher;
import dbrighthd.wildfiregendermodplugin.wildfire.UserManager;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class Beta5CookieBridgeTest {
    @Test
    void storesProfileForBackendTransfer() {
        GenderModPlugin plugin = mock(GenderModPlugin.class);
        when(plugin.getCustomLogger()).thenReturn(mock(CustomPluginLogger.class));
        Player player = mock(Player.class);

        new Beta5CookieBridge(plugin).store(player, new byte[]{0});

        verify(player).storeCookie(eq(Beta5CookieBridge.KEY), aryEq(new byte[]{0}));
    }

    @Test
    void restoresProfileOnlyForNegotiatedClientWithoutFreshProfile() {
        GenderModPlugin plugin = mock(GenderModPlugin.class);
        when(plugin.getCustomLogger()).thenReturn(mock(CustomPluginLogger.class));
        SyncStateCoordinator coordinator = mock(SyncStateCoordinator.class);
        when(plugin.getSyncStateCoordinator()).thenReturn(coordinator);
        UserManager users = new UserManager();
        when(plugin.getUserManager()).thenReturn(users);
        NetworkManager network = mock(NetworkManager.class);
        when(plugin.getNetworkManager()).thenReturn(network);
        TaskDispatcher dispatcher = mock(TaskDispatcher.class);
        when(plugin.getTaskDispatcher()).thenReturn(dispatcher);
        doAnswer(invocation -> {
            invocation.getArgument(1, Runnable.class).run();
            return true;
        }).when(dispatcher).runFor(any(Player.class), any(Runnable.class));
        Player player = mock(Player.class);
        UUID id = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(id);
        when(player.isOnline()).thenReturn(true);
        when(player.retrieveCookie(Beta5CookieBridge.KEY)).thenReturn(CompletableFuture.completedFuture(new byte[]{0}));
        when(coordinator.isBeta5HelloAccepted(player)).thenReturn(true);

        new Beta5CookieBridge(plugin).restore(player);

        assertArrayEquals(new byte[]{0}, users.getBeta5(id));
        verify(network).broadcastBeta5(player, new byte[]{0});

        new Beta5CookieBridge(plugin).restore(player);
        verify(network).broadcastBeta5(player, new byte[]{0});
    }

    @Test
    void ignoresInvalidCookie() {
        GenderModPlugin plugin = mock(GenderModPlugin.class);
        when(plugin.getCustomLogger()).thenReturn(mock(CustomPluginLogger.class));
        Player player = mock(Player.class);
        when(player.retrieveCookie(Beta5CookieBridge.KEY)).thenReturn(CompletableFuture.completedFuture(new byte[]{1}));

        new Beta5CookieBridge(plugin).restore(player);

        verify(plugin, never()).getTaskDispatcher();
    }
}
