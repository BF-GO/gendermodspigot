package dbrighthd.wildfiregendermodplugin.networking;

import dbrighthd.wildfiregendermodplugin.GenderModPlugin;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;

import java.io.IOException;

/** Carries the player's compact profile across a proxy's backend server switch. */
public final class Beta5CookieBridge {
    static final NamespacedKey KEY = new NamespacedKey("female_gender_mod", "proxy_profile");
    private static final int MAX_COOKIE_BYTES = 5120;

    private final GenderModPlugin plugin;

    public Beta5CookieBridge(GenderModPlugin plugin) {
        this.plugin = plugin;
    }

    public void store(Player player, byte[] config) {
        if (config.length > MAX_COOKIE_BYTES) {
            plugin.getCustomLogger().debug("Beta.5 profile for %s is too large for a proxy cookie", player.getName());
            return;
        }
        try {
            player.storeCookie(KEY, config.clone());
            plugin.getCustomLogger().debug("Stored Beta.5 proxy cookie for %s", player.getName());
        } catch (IllegalArgumentException ex) {
            plugin.getCustomLogger().warning(ex, "Could not store Beta.5 proxy cookie for %s", player.getName());
        }
    }

    public void restore(Player player) {
        player.retrieveCookie(KEY).thenAccept(config -> {
            if (config == null) return;
            try {
                Beta5Packet.validateConfig(config);
            } catch (IOException ex) {
                plugin.getCustomLogger().debug(ex, "Ignoring invalid Beta.5 proxy cookie for %s", player.getName());
                return;
            }
            plugin.getTaskDispatcher().runFor(player, () -> {
                if (!player.isOnline() || !plugin.getSyncStateCoordinator().isBeta5HelloAccepted(player)
                        || plugin.getUserManager().getBeta5(player.getUniqueId()) != null) return;
                plugin.getUserManager().putBeta5(player.getUniqueId(), config);
                plugin.getCustomLogger().debug("Restored Beta.5 profile for %s from proxy cookie", player.getName());
                plugin.getNetworkManager().broadcastBeta5(player, config);
            });
        }).exceptionally(ex -> {
            plugin.getCustomLogger().debug("Could not retrieve Beta.5 proxy cookie for %s: %s", player.getName(), ex);
            return null;
        });
    }
}
