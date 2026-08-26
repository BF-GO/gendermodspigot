package dbrighthd.wildfiregendermodplugin.networking;

import org.bukkit.entity.Player;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class SyncStateCoordinator {
    private final Set<UUID> helloAccepted = ConcurrentHashMap.newKeySet();

    public void onHelloAccepted(Player player) {
        helloAccepted.add(player.getUniqueId());
    }

    public boolean isHelloAccepted(Player player) {
        return helloAccepted.contains(player.getUniqueId());
    }

    public void remove(Player player) {
        helloAccepted.remove(player.getUniqueId());
    }

    public void clear() {
        helloAccepted.clear();
    }
}
