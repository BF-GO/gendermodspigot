package dbrighthd.wildfiregendermodplugin.networking;

import dbrighthd.wildfiregendermodplugin.GenderModPlugin;
import dbrighthd.wildfiregendermodplugin.wildfire.ModConstants;
import dbrighthd.wildfiregendermodplugin.wildfire.ModUser;
import dbrighthd.wildfiregendermodplugin.wildfire.UserManager;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SyncStateCoordinatorTest {
    @Test
    void catchesUpTrackedPlayersWhenChannelAndHelloBecomeReady() {
        Fixture fixture = fixture(true);
        Player viewer = player();
        Player tracked = trackedPlayer(fixture);

        fixture.coordinator().onTrack(viewer, tracked);
        fixture.coordinator().onChannelRegistered(viewer, ModConstants.SYNC);
        verify(fixture.networkManager(), never()).syncUserTo(viewer, fixture.user());

        fixture.coordinator().onHelloAccepted(viewer);
        verify(fixture.networkManager()).syncUserTo(viewer, fixture.user());
        assertEquals(ModConstants.SYNC, fixture.coordinator().preferredChannel(viewer));
    }

    @Test
    void trackingAfterNegotiationSynchronizesOnlyThatPlayer() {
        Fixture fixture = fixture(false);
        Player viewer = player();
        Player tracked = trackedPlayer(fixture);

        fixture.coordinator().onChannelRegistered(viewer, ModConstants.SYNC);
        fixture.coordinator().onTrack(viewer, tracked);

        verify(fixture.networkManager()).syncUserTo(viewer, fixture.user());
    }

    @Test
    void untrackedPlayersAreNotIncludedInCatchUp() {
        Fixture fixture = fixture(false);
        Player viewer = player();
        Player tracked = trackedPlayer(fixture);

        fixture.coordinator().onTrack(viewer, tracked);
        fixture.coordinator().onUntrack(viewer, tracked);
        fixture.coordinator().onChannelRegistered(viewer, ModConstants.SYNC);

        verify(fixture.networkManager(), never()).syncUserTo(viewer, fixture.user());
    }

    @Test
    void forgeDoesNotRequireHelloAndFabricTakesPriorityWithoutDuplicateCatchUp() {
        Fixture fixture = fixture(true);
        Player viewer = player();
        Player tracked = trackedPlayer(fixture);
        fixture.coordinator().onTrack(viewer, tracked);

        fixture.coordinator().onChannelRegistered(viewer, ModConstants.FORGE);
        fixture.coordinator().onChannelRegistered(viewer, ModConstants.SYNC);
        fixture.coordinator().onHelloAccepted(viewer);

        verify(fixture.networkManager(), times(1)).syncUserTo(viewer, fixture.user());
        assertEquals(ModConstants.SYNC, fixture.coordinator().preferredChannel(viewer));
    }

    @Test
    void unregisteringFabricClearsNegotiationState() {
        Fixture fixture = fixture(true);
        Player viewer = player();

        fixture.coordinator().onChannelRegistered(viewer, ModConstants.SYNC);
        fixture.coordinator().onHelloAccepted(viewer);
        fixture.coordinator().onChannelUnregistered(viewer, ModConstants.SYNC);

        assertNull(fixture.coordinator().preferredChannel(viewer));
        fixture.coordinator().onChannelRegistered(viewer, ModConstants.SYNC);
        assertNull(fixture.coordinator().preferredChannel(viewer));
    }

    @Test
    void beta5CatchUpNeedsConfigurationHelloAndPlayChannel() {
        Fixture fixture = fixture(false);
        when(fixture.networkManager().supportsBeta5()).thenReturn(true);
        Player viewer = player();
        Player tracked = trackedPlayer(fixture);

        fixture.coordinator().onTrack(viewer, tracked);
        fixture.coordinator().onBeta5HelloAccepted(viewer.getUniqueId());
        verify(fixture.networkManager(), never()).syncBeta5UserTo(viewer, tracked.getUniqueId());

        fixture.coordinator().onChannelRegistered(viewer, ModConstants.BETA5_CLIENTBOUND_SYNC);
        verify(fixture.networkManager()).syncBeta5UserTo(viewer, tracked.getUniqueId());
        assertEquals(ModConstants.BETA5_CLIENTBOUND_SYNC, fixture.coordinator().preferredChannel(viewer));
    }

    private static Fixture fixture(boolean supportsHello) {
        GenderModPlugin plugin = mock(GenderModPlugin.class);
        NetworkManager networkManager = mock(NetworkManager.class);
        UserManager userManager = new UserManager();
        ModUser user = ProtocolTest.testUser(UUID.randomUUID());
        userManager.put(user);
        when(plugin.getNetworkManager()).thenReturn(networkManager);
        when(plugin.getUserManager()).thenReturn(userManager);
        when(networkManager.supportsHello()).thenReturn(supportsHello);
        return new Fixture(new SyncStateCoordinator(plugin), networkManager, userManager, user);
    }

    private static Player trackedPlayer(Fixture fixture) {
        return player(fixture.user().userId());
    }

    private static Player player() {
        return player(UUID.randomUUID());
    }

    private static Player player(UUID id) {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(id);
        return player;
    }

    private record Fixture(
            SyncStateCoordinator coordinator,
            NetworkManager networkManager,
            UserManager userManager,
            ModUser user) {
    }
}
