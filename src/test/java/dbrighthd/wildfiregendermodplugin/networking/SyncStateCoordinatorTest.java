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
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SyncStateCoordinatorTest {
    @Test
    void catchesUpWhenTrackingPrecedesChannelRegistration() {
        Fixture fixture = fixture(false);
        Player viewer = player();
        Player tracked = trackedPlayer(fixture);

        fixture.coordinator().onTrack(viewer, tracked);
        verify(fixture.manager(), never()).syncUserTo(viewer, fixture.user());

        fixture.coordinator().onChannelRegistered(viewer, ModConstants.SYNC);
        verify(fixture.manager()).syncUserTo(viewer, fixture.user());

        fixture.coordinator().onChannelRegistered(viewer, ModConstants.SYNC);
        verify(fixture.manager(), times(1)).syncUserTo(viewer, fixture.user());
    }

    @Test
    void sendsImmediatelyWhenChannelPrecedesTracking() {
        Fixture fixture = fixture(false);
        Player viewer = player();
        Player tracked = trackedPlayer(fixture);

        fixture.coordinator().onChannelRegistered(viewer, ModConstants.SYNC);
        fixture.coordinator().onTrack(viewer, tracked);
        fixture.coordinator().onTrack(viewer, tracked);

        verify(fixture.manager(), times(1)).syncUserTo(viewer, fixture.user());
    }

    @Test
    void v5FabricWaitsForCompatibleHelloInBothEventOrders() {
        Fixture fixture = fixture(true);
        Player firstViewer = player();
        Player firstTracked = trackedPlayer(fixture);

        fixture.coordinator().onTrack(firstViewer, firstTracked);
        fixture.coordinator().onChannelRegistered(firstViewer, ModConstants.SYNC);
        verify(fixture.manager(), never()).syncUserTo(firstViewer, fixture.user());
        fixture.coordinator().onHelloAccepted(firstViewer);
        verify(fixture.manager()).syncUserTo(firstViewer, fixture.user());

        Player secondViewer = player();
        Player secondTracked = trackedPlayer(fixture);
        reset(fixture.manager());
        when(fixture.manager().supportsHello()).thenReturn(true);
        fixture.coordinator().onTrack(secondViewer, secondTracked);
        fixture.coordinator().onHelloAccepted(secondViewer);
        verify(fixture.manager(), never()).syncUserTo(secondViewer, fixture.user());
        fixture.coordinator().onChannelRegistered(secondViewer, ModConstants.SYNC);
        verify(fixture.manager()).syncUserTo(secondViewer, fixture.user());
    }

    @Test
    void retrackingAndReregisteringResendCurrentProfile() {
        Fixture fixture = fixture(false);
        Player viewer = player();
        Player tracked = trackedPlayer(fixture);
        fixture.coordinator().onChannelRegistered(viewer, ModConstants.FORGE);

        fixture.coordinator().onTrack(viewer, tracked);
        fixture.coordinator().onUntrack(viewer, tracked);
        fixture.coordinator().onTrack(viewer, tracked);
        verify(fixture.manager(), times(2)).syncUserTo(viewer, fixture.user());

        fixture.coordinator().onChannelUnregistered(viewer, ModConstants.FORGE);
        fixture.coordinator().onChannelRegistered(viewer, ModConstants.FORGE);
        verify(fixture.manager(), times(3)).syncUserTo(viewer, fixture.user());
    }

    @Test
    void quitAndClearRemoveTrackedAndNegotiationState() {
        Fixture fixture = fixture(true);
        Player viewer = player();
        Player tracked = trackedPlayer(fixture);
        fixture.coordinator().onTrack(viewer, tracked);
        fixture.coordinator().onChannelRegistered(viewer, ModConstants.SYNC);
        fixture.coordinator().onHelloAccepted(viewer);
        verify(fixture.manager()).syncUserTo(viewer, fixture.user());

        fixture.coordinator().remove(tracked);
        fixture.coordinator().onChannelUnregistered(viewer, ModConstants.SYNC);
        fixture.coordinator().onChannelRegistered(viewer, ModConstants.SYNC);
        fixture.coordinator().onHelloAccepted(viewer);
        verify(fixture.manager(), times(1)).syncUserTo(viewer, fixture.user());

        fixture.coordinator().remove(viewer);
        assertNull(fixture.coordinator().preferredChannel(viewer));

        fixture.coordinator().clear();
        assertNull(fixture.coordinator().preferredChannel(viewer));
    }

    @Test
    void selectsOneTransportWithReadyFabricPreferredOverForge() {
        Fixture fixture = fixture(true);
        Player viewer = player();

        fixture.coordinator().onChannelRegistered(viewer, ModConstants.SYNC);
        fixture.coordinator().onChannelRegistered(viewer, ModConstants.FORGE);
        assertEquals(ModConstants.FORGE, fixture.coordinator().preferredChannel(viewer));

        fixture.coordinator().onHelloAccepted(viewer);
        assertEquals(ModConstants.SYNC, fixture.coordinator().preferredChannel(viewer));
    }

    private static Player trackedPlayer(Fixture fixture) {
        Player tracked = mock(Player.class);
        when(tracked.getUniqueId()).thenReturn(fixture.user().userId());
        fixture.userManager().put(fixture.user());
        return tracked;
    }

    private static Fixture fixture(boolean supportsHello) {
        GenderModPlugin plugin = mock(GenderModPlugin.class);
        NetworkManager manager = mock(NetworkManager.class);
        UserManager userManager = new UserManager();
        ModUser user = ProtocolTest.testUser(UUID.randomUUID());
        when(plugin.getNetworkManager()).thenReturn(manager);
        when(plugin.getUserManager()).thenReturn(userManager);
        when(manager.supportsHello()).thenReturn(supportsHello);
        return new Fixture(new SyncStateCoordinator(plugin), manager, userManager, user);
    }

    private static Player player() {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        return player;
    }

    private record Fixture(
            SyncStateCoordinator coordinator,
            NetworkManager manager,
            UserManager userManager,
            ModUser user) {
    }
}
