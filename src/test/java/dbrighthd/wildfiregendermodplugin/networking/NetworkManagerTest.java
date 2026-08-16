package dbrighthd.wildfiregendermodplugin.networking;

import dbrighthd.wildfiregendermodplugin.GenderModPlugin;
import dbrighthd.wildfiregendermodplugin.logging.CustomPluginLogger;
import dbrighthd.wildfiregendermodplugin.scheduler.TaskDispatcher;
import dbrighthd.wildfiregendermodplugin.wildfire.ModConstants;
import dbrighthd.wildfiregendermodplugin.wildfire.ModUser;
import dbrighthd.wildfiregendermodplugin.wildfire.UserManager;
import org.bukkit.Server;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NetworkManagerTest {
    @Test
    void broadcastSnapshotsGloballyAndSendsOnRecipientSchedulers() {
        Fixture fixture = fixture(5);
        assertTrue(fixture.manager().init());

        UUID sourceId = UUID.randomUUID();
        Player source = player(sourceId, true, Set.of(ModConstants.SYNC));
        Player recipient = player(UUID.randomUUID(), true, Set.of(ModConstants.SYNC));
        doReturn(List.of(source, recipient)).when(fixture.server()).getOnlinePlayers();

        fixture.manager().broadcast(ProtocolTest.testUser(sourceId));

        ArgumentCaptor<Runnable> globalTask = ArgumentCaptor.forClass(Runnable.class);
        verify(fixture.dispatcher()).runGlobal(globalTask.capture());
        verify(fixture.server(), never()).getOnlinePlayers();
        verify(recipient, never()).sendPluginMessage(any(), anyString(), any(byte[].class));

        globalTask.getValue().run();
        ArgumentCaptor<Runnable> playerTasks = ArgumentCaptor.forClass(Runnable.class);
        verify(fixture.dispatcher(), times(2)).runFor(any(Player.class), playerTasks.capture());
        verify(recipient, never()).sendPluginMessage(any(), anyString(), any(byte[].class));

        playerTasks.getAllValues().forEach(Runnable::run);
        verify(source, never()).sendPluginMessage(any(), anyString(), any(byte[].class));
        verify(recipient, times(1)).sendPluginMessage(eq(fixture.plugin()), eq(ModConstants.SYNC), any(byte[].class));
        verify(recipient, never()).sendPluginMessage(eq(fixture.plugin()), eq(ModConstants.FORGE), any(byte[].class));
    }

    @Test
    void skipsRecipientThatWentOfflineBeforeItsTaskRan() {
        Fixture fixture = fixture(5);
        assertTrue(fixture.manager().init());

        Player recipient = player(UUID.randomUUID(), false, Set.of(ModConstants.SYNC));
        doReturn(List.of(recipient)).when(fixture.server()).getOnlinePlayers();
        fixture.manager().broadcast(ProtocolTest.testUser(UUID.randomUUID()));

        ArgumentCaptor<Runnable> globalTask = ArgumentCaptor.forClass(Runnable.class);
        verify(fixture.dispatcher()).runGlobal(globalTask.capture());
        globalTask.getValue().run();

        ArgumentCaptor<Runnable> playerTask = ArgumentCaptor.forClass(Runnable.class);
        verify(fixture.dispatcher()).runFor(eq(recipient), playerTask.capture());
        playerTask.getValue().run();

        verify(recipient, never()).sendPluginMessage(any(), anyString(), any(byte[].class));
    }

    @Test
    void joiningPlayerReceivesOneEntityTaskAndNotItsOwnConfiguration() {
        Fixture fixture = fixture(5);
        assertTrue(fixture.manager().init());

        UUID targetId = UUID.randomUUID();
        fixture.userManager().put(ProtocolTest.testUser(targetId));
        fixture.userManager().put(ProtocolTest.testUser(UUID.randomUUID()));
        Player target = player(targetId, true, Set.of(ModConstants.SYNC));

        fixture.manager().syncAllTo(target);

        ArgumentCaptor<Runnable> playerTask = ArgumentCaptor.forClass(Runnable.class);
        verify(fixture.dispatcher(), times(1)).runFor(eq(target), playerTask.capture());
        playerTask.getValue().run();

        verify(target, times(1)).sendPluginMessage(eq(fixture.plugin()), eq(ModConstants.SYNC), any(byte[].class));
        verify(target, never()).sendPluginMessage(eq(fixture.plugin()), eq(ModConstants.FORGE), any(byte[].class));
    }

    @Test
    void respondsToV5HelloAndRejectsTrailingData() {
        Fixture fixture = fixture(5);
        assertTrue(fixture.manager().init());
        Player target = player(UUID.randomUUID(), true, Set.of(ModConstants.CLIENTBOUND_HELLO));
        when(target.getName()).thenReturn("Tester");

        fixture.manager().handleHello(target, new byte[]{1});

        ArgumentCaptor<byte[]> response = ArgumentCaptor.forClass(byte[].class);
        verify(target).sendPluginMessage(eq(fixture.plugin()), eq(ModConstants.CLIENTBOUND_HELLO), response.capture());
        assertArrayEquals(new byte[]{1}, response.getValue());

        Player malformedTarget = player(UUID.randomUUID(), true, Set.of(ModConstants.CLIENTBOUND_HELLO));
        when(malformedTarget.getName()).thenReturn("Malformed");
        fixture.manager().handleHello(malformedTarget, new byte[]{1, 0});
        verify(malformedTarget, never()).sendPluginMessage(any(), anyString(), any(byte[].class));
    }

    @Test
    void malformedPayloadReturnsNullInsteadOfEscapingListener() {
        Fixture fixture = fixture(5);
        assertTrue(fixture.manager().init());
        byte[] invalid = ProtocolTest.beta4Fixture();
        invalid[16] = 3;

        assertNull(fixture.manager().deserializeUser(invalid, false));
        verify(fixture.logger()).warning(any(Throwable.class), anyString(), any());
    }

    @Test
    void rejectsUnimplementedProtocolOne() {
        Fixture fixture = fixture(1);

        assertFalse(fixture.manager().init());
        assertFalse(fixture.manager().supportsHello());
    }

    @Test
    void rejectsUnknownProtocol() {
        Fixture fixture = fixture(99);

        assertFalse(fixture.manager().init());
    }

    private static Fixture fixture(int protocol) {
        GenderModPlugin plugin = mock(GenderModPlugin.class);
        TaskDispatcher dispatcher = mock(TaskDispatcher.class);
        UserManager userManager = new UserManager();
        CustomPluginLogger logger = mock(CustomPluginLogger.class);
        FileConfiguration configuration = mock(FileConfiguration.class);
        Server server = mock(Server.class);

        when(configuration.getInt("mod.protocol", -1)).thenReturn(protocol);
        when(plugin.getConfig()).thenReturn(configuration);
        when(plugin.getCustomLogger()).thenReturn(logger);
        when(plugin.getUserManager()).thenReturn(userManager);
        when(plugin.getServer()).thenReturn(server);
        when(dispatcher.runFor(any(Player.class), any(Runnable.class))).thenReturn(true);

        return new Fixture(new NetworkManager(plugin, dispatcher), plugin, dispatcher, userManager, logger, server);
    }

    private static Player player(UUID userId, boolean online, Set<String> channels) {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(userId);
        when(player.isOnline()).thenReturn(online);
        when(player.getListeningPluginChannels()).thenReturn(channels);
        return player;
    }

    private record Fixture(
            NetworkManager manager,
            GenderModPlugin plugin,
            TaskDispatcher dispatcher,
            UserManager userManager,
            CustomPluginLogger logger,
            Server server) {
    }
}
