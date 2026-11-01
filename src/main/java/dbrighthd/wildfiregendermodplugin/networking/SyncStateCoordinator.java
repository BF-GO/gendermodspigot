package dbrighthd.wildfiregendermodplugin.networking;

import dbrighthd.wildfiregendermodplugin.GenderModPlugin;
import dbrighthd.wildfiregendermodplugin.wildfire.ModConstants;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class SyncStateCoordinator {
    private final GenderModPlugin plugin;
    private final ConcurrentMap<UUID, ViewerState> viewers = new ConcurrentHashMap<>();

    public SyncStateCoordinator(GenderModPlugin plugin) {
        this.plugin = plugin;
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
            if (channel.equals(ModConstants.BETA5_CLIENTBOUND_SYNC)) state.beta5HelloAccepted = false;
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

    public void onBeta5HelloAccepted(UUID viewerId) {
        if (!plugin.getNetworkManager().supportsBeta5()) return;
        ViewerState state = viewers.computeIfAbsent(viewerId, ignored -> new ViewerState());
        synchronized (state) {
            state.beta5HelloAccepted = true;
        }
    }

    public boolean isBeta5HelloAccepted(Player viewer) {
        ViewerState state = viewers.get(viewer.getUniqueId());
        if (state == null) return false;
        synchronized (state) {
            return state.beta5HelloAccepted;
        }
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

    public boolean isHelloAccepted(Player viewer) {
        ViewerState state = viewers.get(viewer.getUniqueId());
        if (state == null) return false;

        synchronized (state) {
            return state.v5HelloAccepted;
        }
    }

    public String preferredChannel(Player viewer) {
        ViewerState state = viewers.get(viewer.getUniqueId());
        if (state == null) return null;

        synchronized (state) {
            return preferredChannel(state);
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

    private String preferredChannel(ViewerState state) {
        if (plugin.getNetworkManager().supportsBeta5()) {
            return state.beta5HelloAccepted && state.channels.contains(ModConstants.BETA5_CLIENTBOUND_SYNC)
                    ? ModConstants.BETA5_CLIENTBOUND_SYNC : null;
        }
        if (state.channels.contains(ModConstants.SYNC)
                && (!plugin.getNetworkManager().supportsHello() || state.v5HelloAccepted)) {
            return ModConstants.SYNC;
        }
        if (state.channels.contains(ModConstants.FORGE)) return ModConstants.FORGE;
        return null;
    }

    private boolean isReady(ViewerState state) {
        return preferredChannel(state) != null;
    }

    private void syncTrackedTo(Player viewer, ViewerState state) {
        Set<UUID> trackedPlayers;
        synchronized (state) {
            trackedPlayers = Set.copyOf(state.trackedPlayers);
        }
        for (UUID trackedId : trackedPlayers) syncOne(viewer, trackedId);
    }

    private void syncOne(Player viewer, UUID trackedId) {
        if (plugin.getNetworkManager().supportsBeta5()) {
            plugin.getNetworkManager().syncBeta5UserTo(viewer, trackedId);
            return;
        }
        var user = plugin.getUserManager().get(trackedId);
        if (user != null) plugin.getNetworkManager().syncUserTo(viewer, user);
    }

    private static boolean isSyncChannel(String channel) {
        return channel.equals(ModConstants.SYNC) || channel.equals(ModConstants.FORGE)
                || channel.equals(ModConstants.BETA5_CLIENTBOUND_SYNC);
    }

    private static final class ViewerState {
        private final Set<String> channels = new HashSet<>();
        private final Set<UUID> trackedPlayers = new HashSet<>();
        private boolean v5HelloAccepted;
        private boolean beta5HelloAccepted;
    }
}
