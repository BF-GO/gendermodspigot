package dbrighthd.wildfiregendermodplugin.networking;

import dbrighthd.wildfiregendermodplugin.GenderModPlugin;
import dbrighthd.wildfiregendermodplugin.wildfire.ModConstants;
import dbrighthd.wildfiregendermodplugin.wildfire.ModUser;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Coordinates transient channel, handshake and entity-tracking state.
 */
public final class SyncStateCoordinator {
    private final GenderModPlugin plugin;
    private final ConcurrentMap<UUID, ViewerState> viewers = new ConcurrentHashMap<>();

    public SyncStateCoordinator(GenderModPlugin plugin) {
        this.plugin = plugin;
    }

    public void onTrack(Player viewer, Player tracked) {
        if (viewer.getUniqueId().equals(tracked.getUniqueId())) return;

        ViewerState state = state(viewer);
        boolean shouldSync;
        synchronized (state) {
            shouldSync = state.trackedPlayers.add(tracked.getUniqueId()) && isReady(state);
        }
        if (shouldSync) syncOne(viewer, tracked.getUniqueId());
    }

    public void onUntrack(Player viewer, Player tracked) {
        ViewerState state = viewers.get(viewer.getUniqueId());
        if (state == null) return;

        synchronized (state) {
            state.trackedPlayers.remove(tracked.getUniqueId());
        }
    }

    public void onChannelRegistered(Player viewer, String channel) {
        if (!isSyncChannel(channel)) return;

        ViewerState state = state(viewer);
        boolean catchUp;
        synchronized (state) {
            boolean wasReady = isReady(state);
            if (!state.channels.add(channel)) return;
            catchUp = !wasReady && isReady(state);
        }
        if (catchUp) syncTrackedTo(viewer, state);
    }

    public void onChannelUnregistered(Player viewer, String channel) {
        if (!isSyncChannel(channel)) return;

        ViewerState state = viewers.get(viewer.getUniqueId());
        if (state == null) return;

        synchronized (state) {
            state.channels.remove(channel);
            if (channel.equals(ModConstants.SYNC)) state.v5HelloAccepted = false;
        }
    }

    public void onHelloAccepted(Player viewer) {
        if (!plugin.getNetworkManager().supportsHello()) return;

        ViewerState state = state(viewer);
        boolean catchUp;
        synchronized (state) {
            boolean wasReady = isReady(state);
            state.v5HelloAccepted = true;
            catchUp = !wasReady && isReady(state);
        }
        if (catchUp) syncTrackedTo(viewer, state);
    }

    public String preferredChannel(Player viewer) {
        ViewerState state = viewers.get(viewer.getUniqueId());
        if (state == null) return null;

        synchronized (state) {
            if (state.channels.contains(ModConstants.SYNC)
                    && (!plugin.getNetworkManager().supportsHello() || state.v5HelloAccepted)) {
                return ModConstants.SYNC;
            }
            if (state.channels.contains(ModConstants.FORGE)) return ModConstants.FORGE;
            return null;
        }
    }

    public void remove(Player player) {
        UUID playerId = player.getUniqueId();
        viewers.remove(playerId);
        for (ViewerState state : viewers.values()) {
            synchronized (state) {
                state.trackedPlayers.remove(playerId);
            }
        }
    }

    public void clear() {
        viewers.clear();
    }

    private ViewerState state(Player viewer) {
        return viewers.computeIfAbsent(viewer.getUniqueId(), ignored -> new ViewerState());
    }

    private boolean isReady(ViewerState state) {
        boolean fabricReady = state.channels.contains(ModConstants.SYNC)
                && (!plugin.getNetworkManager().supportsHello() || state.v5HelloAccepted);
        return fabricReady || state.channels.contains(ModConstants.FORGE);
    }

    private void syncTrackedTo(Player viewer, ViewerState state) {
        Set<UUID> trackedPlayers;
        synchronized (state) {
            trackedPlayers = Set.copyOf(state.trackedPlayers);
        }
        for (UUID trackedId : trackedPlayers) syncOne(viewer, trackedId);
    }

    private void syncOne(Player viewer, UUID trackedId) {
        ModUser user = plugin.getUserManager().get(trackedId);
        if (user != null) plugin.getNetworkManager().syncUserTo(viewer, user);
    }

    private static boolean isSyncChannel(String channel) {
        return channel.equals(ModConstants.SYNC) || channel.equals(ModConstants.FORGE);
    }

    private static final class ViewerState {
        private final Set<UUID> trackedPlayers = new HashSet<>();
        private final Set<String> channels = new HashSet<>();
        private boolean v5HelloAccepted;
    }
}
