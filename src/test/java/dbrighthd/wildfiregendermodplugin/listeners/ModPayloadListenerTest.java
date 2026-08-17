package dbrighthd.wildfiregendermodplugin.listeners;

import dbrighthd.wildfiregendermodplugin.GenderModPlugin;
import dbrighthd.wildfiregendermodplugin.logging.CustomPluginLogger;
import dbrighthd.wildfiregendermodplugin.networking.InboundPacketGuard;
import dbrighthd.wildfiregendermodplugin.networking.NetworkManager;
import dbrighthd.wildfiregendermodplugin.networking.ProtocolTest;
import dbrighthd.wildfiregendermodplugin.networking.SyncStateCoordinator;
import dbrighthd.wildfiregendermodplugin.scheduler.TaskDispatcher;
import dbrighthd.wildfiregendermodplugin.wildfire.ModConstants;
import dbrighthd.wildfiregendermodplugin.wildfire.ModUser;
import dbrighthd.wildfiregendermodplugin.wildfire.UserManager;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ModPayloadListenerTest {
    @Test
    void copiesPayloadBeforeProcessingItOnTheSenderScheduler() throws IOException {
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
        verify(fixture.networkManager()).broadcast(player, user);
    }

    @Test
    void rejectsPayloadForAnotherUuidWithoutChangingState() throws IOException {
        Fixture fixture = fixture();
        Player player = player(UUID.randomUUID(), true);
        ModUser forgedUser = ProtocolTest.testUser(UUID.randomUUID());
        when(fixture.networkManager().deserializeUser(any(byte[].class), eq(false))).thenReturn(forgedUser);

        runScheduled(fixture, player, ModConstants.SEND_GENDER_INFO, new byte[]{1});

        assertTrue(fixture.userManager().snapshot().isEmpty());
        verify(fixture.networkManager(), never()).broadcast(any(Player.class), any(ModUser.class));
    }

    @Test
    void ignoresMalformedPayloadWithoutChangingState() throws IOException {
        Fixture fixture = fixture();
        Player player = player(UUID.randomUUID(), true);
        when(fixture.networkManager().deserializeUser(any(byte[].class), eq(false)))
                .thenThrow(new IOException("malformed"));

        runScheduled(fixture, player, ModConstants.SEND_GENDER_INFO, new byte[]{1});

        assertTrue(fixture.userManager().snapshot().isEmpty());
        verify(fixture.networkManager(), never()).broadcast(any(Player.class), any(ModUser.class));
    }

    @Test
    void skipsPayloadWhenSenderWentOfflineBeforeItsTaskRan() throws IOException {
        Fixture fixture = fixture();
        Player player = player(UUID.randomUUID(), false);

        runScheduled(fixture, player, ModConstants.SEND_GENDER_INFO, new byte[]{1});

        verify(fixture.networkManager(), never()).deserializeUser(any(byte[].class), eq(false));
    }

    @Test
    void rejectsOversizedPayloadsBeforeCopyingOrScheduling() {
        Fixture fixture = fixture();
        Player player = player(UUID.randomUUID(), true);

        fixture.listener().onPluginMessageReceived(
                ModConstants.SEND_GENDER_INFO, player, new byte[InboundPacketGuard.MAX_SYNC_BYTES + 1]);
        fixture.listener().onPluginMessageReceived(
                ModConstants.SERVERBOUND_HELLO, player, new byte[InboundPacketGuard.MAX_HELLO_BYTES + 1]);

        verify(fixture.dispatcher(), never()).runFor(any(Player.class), any(Runnable.class));
        verify(fixture.guard(), never()).tryAcquire(any(UUID.class));
    }

    @Test
    void acceptsPayloadsAtExactSizeLimits() {
        Fixture fixture = fixture();
        Player player = player(UUID.randomUUID(), true);

        fixture.listener().onPluginMessageReceived(
                ModConstants.SEND_GENDER_INFO, player, new byte[InboundPacketGuard.MAX_SYNC_BYTES]);
        fixture.listener().onPluginMessageReceived(
                ModConstants.SERVERBOUND_HELLO, player, new byte[InboundPacketGuard.MAX_HELLO_BYTES]);

        verify(fixture.dispatcher(), times(2)).runFor(eq(player), any(Runnable.class));
        verify(fixture.guard(), times(2)).tryAcquire(player.getUniqueId());
    }

    @Test
    void dropsRateLimitedPayloadBeforeScheduling() {
        Fixture fixture = fixture();
        Player player = player(UUID.randomUUID(), true);
        when(fixture.guard().tryAcquire(player.getUniqueId())).thenReturn(false);

        fixture.listener().onPluginMessageReceived(ModConstants.SEND_GENDER_INFO, player, new byte[]{1});

        verify(fixture.dispatcher(), never()).runFor(any(Player.class), any(Runnable.class));
        verify(fixture.logger(), times(1)).warning(any(String.class), eq("Tester"));
    }

    @Test
    void validHelloEnablesCoordinatorButMismatchDoesNot() throws IOException {
        Fixture fixture = fixture();
        Player accepted = player(UUID.randomUUID(), true);
        Player mismatched = player(UUID.randomUUID(), true);
        when(fixture.networkManager().handleHello(accepted, new byte[]{1}))
                .thenReturn(new NetworkManager.HelloResult(1, true));
        when(fixture.networkManager().handleHello(mismatched, new byte[]{2}))
                .thenReturn(new NetworkManager.HelloResult(2, false));

        runScheduled(fixture, accepted, ModConstants.SERVERBOUND_HELLO, new byte[]{1});
        runScheduled(fixture, mismatched, ModConstants.SERVERBOUND_HELLO, new byte[]{2});

        verify(fixture.coordinator()).onHelloAccepted(accepted);
        verify(fixture.coordinator(), never()).onHelloAccepted(mismatched);
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
        SyncStateCoordinator coordinator = mock(SyncStateCoordinator.class);
        InboundPacketGuard guard = mock(InboundPacketGuard.class);
        CustomPluginLogger logger = mock(CustomPluginLogger.class);
        UserManager userManager = new UserManager();
        when(plugin.getTaskDispatcher()).thenReturn(dispatcher);
        when(plugin.getNetworkManager()).thenReturn(networkManager);
        when(plugin.getSyncStateCoordinator()).thenReturn(coordinator);
        when(plugin.getInboundPacketGuard()).thenReturn(guard);
        when(plugin.getCustomLogger()).thenReturn(logger);
        when(plugin.getUserManager()).thenReturn(userManager);
        when(guard.tryAcquire(any(UUID.class))).thenReturn(true);
        when(guard.shouldWarn(any(UUID.class))).thenReturn(true);
        return new Fixture(new ModPayloadListener(plugin), dispatcher, networkManager,
                coordinator, guard, logger, userManager);
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
            SyncStateCoordinator coordinator,
            InboundPacketGuard guard,
            CustomPluginLogger logger,
            UserManager userManager) {
    }
}
