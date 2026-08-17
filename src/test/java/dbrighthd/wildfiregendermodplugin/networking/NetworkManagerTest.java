package dbrighthd.wildfiregendermodplugin.networking;

import dbrighthd.wildfiregendermodplugin.GenderModPlugin;
import dbrighthd.wildfiregendermodplugin.logging.CustomPluginLogger;
import dbrighthd.wildfiregendermodplugin.networking.minecraft.CraftOutputStream;
import dbrighthd.wildfiregendermodplugin.networking.wildfire.ModSyncPacket;
import dbrighthd.wildfiregendermodplugin.networking.wildfire.ModSyncPacketV4;
import dbrighthd.wildfiregendermodplugin.networking.wildfire.ModSyncPacketV5;
import dbrighthd.wildfiregendermodplugin.scheduler.TaskDispatcher;
import dbrighthd.wildfiregendermodplugin.wildfire.ModConstants;
import dbrighthd.wildfiregendermodplugin.wildfire.ModUser;
import dbrighthd.wildfiregendermodplugin.wildfire.UserManager;
import dbrighthd.wildfiregendermodplugin.wildfire.setup.BreastOptions;
import dbrighthd.wildfiregendermodplugin.wildfire.setup.GenderIdentities;
import dbrighthd.wildfiregendermodplugin.wildfire.setup.GeneralOptions;
import dbrighthd.wildfiregendermodplugin.wildfire.setup.ModConfiguration;
import dbrighthd.wildfiregendermodplugin.wildfire.setup.PhysicsOptions;
import org.bukkit.Server;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
    void broadcastsOnlyToTrackingAudienceOnRecipientSchedulers() {
        Fixture fixture = fixture(5);
        assertTrue(fixture.manager().init());

        UUID sourceId = UUID.randomUUID();
        Player source = player(sourceId, true);
        Player tracking = player(UUID.randomUUID(), true);
        Player notTracking = player(UUID.randomUUID(), true);
        when(source.getTrackedBy()).thenReturn(Set.of(source, tracking));
        when(fixture.coordinator().preferredChannel(tracking)).thenReturn(ModConstants.SYNC);

        fixture.manager().broadcast(source, ProtocolTest.testUser(sourceId));

        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
        verify(fixture.dispatcher()).runFor(eq(tracking), task.capture());
        verify(fixture.dispatcher(), never()).runFor(eq(source), any(Runnable.class));
        verify(fixture.dispatcher(), never()).runFor(eq(notTracking), any(Runnable.class));
        verify(fixture.server(), never()).getOnlinePlayers();

        task.getValue().run();
        verify(tracking).sendPluginMessage(eq(fixture.plugin()), eq(ModConstants.SYNC), any(byte[].class));
        verify(tracking, never()).sendPluginMessage(eq(fixture.plugin()), eq(ModConstants.FORGE), any(byte[].class));
    }

    @Test
    void skipsRecipientThatWentOfflineBeforeItsTaskRan() {
        Fixture fixture = fixture(5);
        assertTrue(fixture.manager().init());

        Player source = player(UUID.randomUUID(), true);
        Player recipient = player(UUID.randomUUID(), false);
        when(source.getTrackedBy()).thenReturn(Set.of(recipient));
        when(fixture.coordinator().preferredChannel(recipient)).thenReturn(ModConstants.SYNC);
        fixture.manager().broadcast(source, ProtocolTest.testUser(source.getUniqueId()));

        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
        verify(fixture.dispatcher()).runFor(eq(recipient), task.capture());
        task.getValue().run();

        verify(recipient, never()).sendPluginMessage(any(), anyString(), any(byte[].class));
    }

    @Test
    void safelyIgnoresRetiredRecipientScheduler() {
        Fixture fixture = fixture(5);
        assertTrue(fixture.manager().init());

        Player source = player(UUID.randomUUID(), true);
        Player recipient = player(UUID.randomUUID(), true);
        when(source.getTrackedBy()).thenReturn(Set.of(recipient));
        when(fixture.dispatcher().runFor(eq(recipient), any(Runnable.class))).thenReturn(false);

        fixture.manager().broadcast(source, ProtocolTest.testUser(source.getUniqueId()));

        verify(fixture.dispatcher()).runFor(eq(recipient), any(Runnable.class));
        verify(recipient, never()).sendPluginMessage(any(), anyString(), any(byte[].class));
    }

    @Test
    void sendsExactlyOneSelectedTransport() {
        Fixture fixture = fixture(5);
        assertTrue(fixture.manager().init());
        Player target = player(UUID.randomUUID(), true);
        ModUser user = ProtocolTest.testUser(UUID.randomUUID());

        when(fixture.coordinator().preferredChannel(target)).thenReturn(ModConstants.SYNC);
        fixture.manager().syncUserTo(target, user);
        runOnlyScheduledTask(fixture, target);
        verify(target).sendPluginMessage(eq(fixture.plugin()), eq(ModConstants.SYNC), any(byte[].class));
        verify(target, never()).sendPluginMessage(eq(fixture.plugin()), eq(ModConstants.FORGE), any(byte[].class));

        Player forgeTarget = player(UUID.randomUUID(), true);
        when(fixture.coordinator().preferredChannel(forgeTarget)).thenReturn(ModConstants.FORGE);
        fixture.manager().syncUserTo(forgeTarget, user);
        runOnlyScheduledTask(fixture, forgeTarget);
        verify(forgeTarget).sendPluginMessage(eq(fixture.plugin()), eq(ModConstants.FORGE), any(byte[].class));
        verify(forgeTarget, never()).sendPluginMessage(eq(fixture.plugin()), eq(ModConstants.SYNC), any(byte[].class));
    }

    @Test
    void respondsToValidAndMismatchedV5HelloButRejectsTrailingData() throws IOException {
        Fixture fixture = fixture(5);
        assertTrue(fixture.manager().init());
        Player target = player(UUID.randomUUID(), true);
        when(target.getListeningPluginChannels()).thenReturn(Set.of(ModConstants.CLIENTBOUND_HELLO));

        NetworkManager.HelloResult accepted = fixture.manager().handleHello(target, new byte[]{1});
        NetworkManager.HelloResult mismatch = fixture.manager().handleHello(target, new byte[]{2});

        assertTrue(accepted.compatible());
        assertFalse(mismatch.compatible());
        ArgumentCaptor<byte[]> response = ArgumentCaptor.forClass(byte[].class);
        verify(target, times(2)).sendPluginMessage(
                eq(fixture.plugin()), eq(ModConstants.CLIENTBOUND_HELLO), response.capture());
        assertArrayEquals(new byte[]{1}, response.getAllValues().getFirst());
        assertArrayEquals(new byte[]{1}, response.getAllValues().getLast());
        assertThrows(IOException.class, () -> fixture.manager().handleHello(target, new byte[]{1, 0}));
    }

    @Test
    void enforcesAllV5NumericBoundaries() throws IOException {
        Fixture fixture = fixture(5);
        assertTrue(fixture.manager().init());

        ModUser minimums = user(0.0f, 0.8f, 0.0f, 0.25f, -1.0f, -1.0f, -1.0f, 0.0f);
        ModUser maximums = user(0.8f, 1.2f, 0.5f, 1.0f, 1.0f, 1.0f, 0.0f, 0.1f);
        assertDoesNotThrow(() -> fixture.manager().deserializeUser(payload(new ModSyncPacketV5(), minimums), false));
        assertDoesNotThrow(() -> fixture.manager().deserializeUser(payload(new ModSyncPacketV5(), maximums), false));

        List<ModUser> invalid = List.of(
                user(-0.01f, 1.0f, 0.2f, 0.5f, 0.0f, 0.0f, -0.5f, 0.05f),
                user(0.81f, 1.0f, 0.2f, 0.5f, 0.0f, 0.0f, -0.5f, 0.05f),
                user(0.4f, 0.79f, 0.2f, 0.5f, 0.0f, 0.0f, -0.5f, 0.05f),
                user(0.4f, 1.21f, 0.2f, 0.5f, 0.0f, 0.0f, -0.5f, 0.05f),
                user(0.4f, 1.0f, -0.01f, 0.5f, 0.0f, 0.0f, -0.5f, 0.05f),
                user(0.4f, 1.0f, 0.51f, 0.5f, 0.0f, 0.0f, -0.5f, 0.05f),
                user(0.4f, 1.0f, 0.2f, 0.24f, 0.0f, 0.0f, -0.5f, 0.05f),
                user(0.4f, 1.0f, 0.2f, 1.01f, 0.0f, 0.0f, -0.5f, 0.05f),
                user(0.4f, 1.0f, 0.2f, 0.5f, -1.01f, 0.0f, -0.5f, 0.05f),
                user(0.4f, 1.0f, 0.2f, 0.5f, 0.0f, 1.01f, -0.5f, 0.05f),
                user(0.4f, 1.0f, 0.2f, 0.5f, 0.0f, 0.0f, -1.01f, 0.05f),
                user(0.4f, 1.0f, 0.2f, 0.5f, 0.0f, 0.0f, 0.01f, 0.05f),
                user(0.4f, 1.0f, 0.2f, 0.5f, 0.0f, 0.0f, -0.5f, -0.01f),
                user(0.4f, 1.0f, 0.2f, 0.5f, 0.0f, 0.0f, -0.5f, 0.11f));

        for (ModUser user : invalid) {
            byte[] payload = payload(new ModSyncPacketV5(), user);
            assertThrows(IOException.class, () -> fixture.manager().deserializeUser(payload, false));
        }
    }

    @Test
    void rejectsNonFiniteNumbersButKeepsFiniteLegacyValuesCompatible() throws IOException {
        Fixture v5 = fixture(5);
        assertTrue(v5.manager().init());
        for (float nonFinite : List.of(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            List<ModUser> invalidUsers = List.of(
                    user(nonFinite, 1.0f, 0.2f, 0.5f, 0.0f, 0.0f, -0.5f, 0.05f),
                    user(0.4f, nonFinite, 0.2f, 0.5f, 0.0f, 0.0f, -0.5f, 0.05f),
                    user(0.4f, 1.0f, nonFinite, 0.5f, 0.0f, 0.0f, -0.5f, 0.05f),
                    user(0.4f, 1.0f, 0.2f, nonFinite, 0.0f, 0.0f, -0.5f, 0.05f),
                    user(0.4f, 1.0f, 0.2f, 0.5f, nonFinite, 0.0f, -0.5f, 0.05f),
                    user(0.4f, 1.0f, 0.2f, 0.5f, 0.0f, nonFinite, -0.5f, 0.05f),
                    user(0.4f, 1.0f, 0.2f, 0.5f, 0.0f, 0.0f, nonFinite, 0.05f),
                    user(0.4f, 1.0f, 0.2f, 0.5f, 0.0f, 0.0f, -0.5f, nonFinite));
            for (ModUser invalid : invalidUsers) {
                assertThrows(IOException.class,
                        () -> v5.manager().deserializeUser(payload(new ModSyncPacketV5(), invalid), false));
            }
        }

        Fixture legacy = fixture(4);
        assertTrue(legacy.manager().init());
        ModUser historical = user(10.0f, 5.0f, -4.0f, 8.0f, -3.0f, 6.0f, 2.0f, 7.0f);
        assertDoesNotThrow(
                () -> legacy.manager().deserializeUser(payload(new ModSyncPacketV4(), historical), false));
        ModUser nonFiniteLegacy = user(0.5f, Float.NaN, 0.2f, 0.5f, 0.0f, 0.0f, -0.5f, 0.05f);
        assertThrows(IOException.class,
                () -> legacy.manager().deserializeUser(payload(new ModSyncPacketV4(), nonFiniteLegacy), false));
    }

    @Test
    void rejectsMalformedPayloadAndInvalidForgeDiscriminator() {
        Fixture fixture = fixture(5);
        assertTrue(fixture.manager().init());
        byte[] invalid = ProtocolTest.beta4Fixture();
        invalid[16] = 3;

        assertThrows(IOException.class, () -> fixture.manager().deserializeUser(invalid, false));
        assertThrows(IOException.class, () -> fixture.manager().deserializeUser(new byte[]{2}, true));
    }

    @Test
    void rejectsUnimplementedAndUnknownProtocols() {
        Fixture v1 = fixture(1);
        assertFalse(v1.manager().init());
        assertFalse(v1.manager().supportsHello());

        assertFalse(fixture(99).manager().init());
    }

    private static void runOnlyScheduledTask(Fixture fixture, Player target) {
        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
        verify(fixture.dispatcher()).runFor(eq(target), task.capture());
        task.getValue().run();
    }

    private static byte[] payload(ModSyncPacket packet, ModUser user) throws IOException {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             CraftOutputStream output = new CraftOutputStream(bytes)) {
            packet.write(user, output);
            return bytes.toByteArray();
        }
    }

    private static ModUser user(float bust, float pitch, float bounce, float floppiness,
                                float xOffset, float yOffset, float zOffset, float cleavage) {
        return new ModUser(UUID.randomUUID(), new ModConfiguration(
                new GeneralOptions(GenderIdentities.OTHER, true, pitch, true),
                new PhysicsOptions(true, true, bounce, floppiness),
                new BreastOptions(bust, xOffset, yOffset, zOffset, false, cleavage),
                null));
    }

    private static Fixture fixture(int protocol) {
        GenderModPlugin plugin = mock(GenderModPlugin.class);
        TaskDispatcher dispatcher = mock(TaskDispatcher.class);
        UserManager userManager = new UserManager();
        CustomPluginLogger logger = mock(CustomPluginLogger.class);
        FileConfiguration configuration = mock(FileConfiguration.class);
        Server server = mock(Server.class);
        SyncStateCoordinator coordinator = mock(SyncStateCoordinator.class);

        when(configuration.getInt("mod.protocol", -1)).thenReturn(protocol);
        when(plugin.getConfig()).thenReturn(configuration);
        when(plugin.getCustomLogger()).thenReturn(logger);
        when(plugin.getUserManager()).thenReturn(userManager);
        when(plugin.getServer()).thenReturn(server);
        when(plugin.getSyncStateCoordinator()).thenReturn(coordinator);
        when(dispatcher.runFor(any(Player.class), any(Runnable.class))).thenReturn(true);

        return new Fixture(new NetworkManager(plugin, dispatcher), plugin, dispatcher,
                userManager, logger, server, coordinator);
    }

    private static Player player(UUID userId, boolean online) {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(userId);
        when(player.isOnline()).thenReturn(online);
        doReturn(Set.of()).when(player).getListeningPluginChannels();
        return player;
    }

    private record Fixture(
            NetworkManager manager,
            GenderModPlugin plugin,
            TaskDispatcher dispatcher,
            UserManager userManager,
            CustomPluginLogger logger,
            Server server,
            SyncStateCoordinator coordinator) {
    }
}
