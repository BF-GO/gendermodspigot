package dbrighthd.wildfiregendermodplugin.listeners;

import dbrighthd.wildfiregendermodplugin.GenderModPlugin;
import dbrighthd.wildfiregendermodplugin.logging.CustomPluginLogger;
import dbrighthd.wildfiregendermodplugin.networking.NetworkManager;
import dbrighthd.wildfiregendermodplugin.networking.ProtocolTest;
import dbrighthd.wildfiregendermodplugin.scheduler.TaskDispatcher;
import dbrighthd.wildfiregendermodplugin.wildfire.ModConstants;
import dbrighthd.wildfiregendermodplugin.wildfire.ModUser;
import dbrighthd.wildfiregendermodplugin.wildfire.UserManager;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ModPayloadListenerTest {
    @Test
    void copiesPayloadBeforeProcessingItOnTheSenderScheduler() {
        Fixture fixture = fixture();
        UUID playerId = UUID.randomUUID();
        Player player = player(playerId, true);
        ModUser user = ProtocolTest.testUser(playerId);
        when(fixture.networkManager().deserializeUser(any(byte[].class), eq(false))).thenReturn(user);

        byte[] message = {1, 2, 3};
        fixture.listener().onPluginMessageReceived(ModConstants.SEND_GENDER_INFO, player, message);

        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
        verify(fixture.dispatcher()).runFor(eq(player), task.capture());
        message[0] = 99;
        task.getValue().run();

        ArgumentCaptor<byte[]> copiedPayload = ArgumentCaptor.forClass(byte[].class);
        verify(fixture.networkManager()).deserializeUser(copiedPayload.capture(), eq(false));
        assertArrayEquals(new byte[]{1, 2, 3}, copiedPayload.getValue());
        assertEquals(user, fixture.userManager().snapshot().getFirst());
        verify(fixture.networkManager()).broadcast(user);
    }

    @Test
    void rejectsPayloadForAnotherUuidWithoutChangingState() {
        Fixture fixture = fixture();
        Player player = player(UUID.randomUUID(), true);
        ModUser forgedUser = ProtocolTest.testUser(UUID.randomUUID());
        when(fixture.networkManager().deserializeUser(any(byte[].class), eq(false))).thenReturn(forgedUser);

        runScheduled(fixture, player, ModConstants.SEND_GENDER_INFO, new byte[]{1});

        assertTrue(fixture.userManager().snapshot().isEmpty());
        verify(fixture.networkManager(), never()).broadcast(any(ModUser.class));
    }

    @Test
    void ignoresMalformedPayloadWithoutChangingState() {
        Fixture fixture = fixture();
        Player player = player(UUID.randomUUID(), true);
        when(fixture.networkManager().deserializeUser(any(byte[].class), eq(false))).thenReturn(null);

        runScheduled(fixture, player, ModConstants.SEND_GENDER_INFO, new byte[]{1});

        assertTrue(fixture.userManager().snapshot().isEmpty());
        verify(fixture.networkManager(), never()).broadcast(any(ModUser.class));
    }

    @Test
    void skipsPayloadWhenSenderWentOfflineBeforeItsTaskRan() {
        Fixture fixture = fixture();
        Player player = player(UUID.randomUUID(), false);

        runScheduled(fixture, player, ModConstants.SEND_GENDER_INFO, new byte[]{1});

        verify(fixture.networkManager(), never()).deserializeUser(any(byte[].class), eq(false));
    }

    private static void runScheduled(Fixture fixture, Player player, String channel, byte[] payload) {
        fixture.listener().onPluginMessageReceived(channel, player, payload);
        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
        verify(fixture.dispatcher()).runFor(eq(player), task.capture());
        task.getValue().run();
    }

    private static Fixture fixture() {
        GenderModPlugin plugin = mock(GenderModPlugin.class);
        TaskDispatcher dispatcher = mock(TaskDispatcher.class);
        NetworkManager networkManager = mock(NetworkManager.class);
        CustomPluginLogger logger = mock(CustomPluginLogger.class);
        UserManager userManager = new UserManager();
        when(plugin.getTaskDispatcher()).thenReturn(dispatcher);
        when(plugin.getNetworkManager()).thenReturn(networkManager);
        when(plugin.getCustomLogger()).thenReturn(logger);
        when(plugin.getUserManager()).thenReturn(userManager);
        return new Fixture(new ModPayloadListener(plugin), dispatcher, networkManager, userManager);
    }

    private static Player player(UUID userId, boolean online) {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(userId);
        when(player.isOnline()).thenReturn(online);
        when(player.getName()).thenReturn("Tester");
        return player;
    }

    private record Fixture(
            ModPayloadListener listener,
            TaskDispatcher dispatcher,
            NetworkManager networkManager,
            UserManager userManager) {
    }
}
