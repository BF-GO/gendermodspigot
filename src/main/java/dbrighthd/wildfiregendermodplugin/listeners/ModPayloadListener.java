package dbrighthd.wildfiregendermodplugin.listeners;

import dbrighthd.wildfiregendermodplugin.GenderModPlugin;
import dbrighthd.wildfiregendermodplugin.networking.InboundPacketGuard;
import dbrighthd.wildfiregendermodplugin.networking.NetworkManager;
import dbrighthd.wildfiregendermodplugin.wildfire.ModConstants;
import dbrighthd.wildfiregendermodplugin.wildfire.ModUser;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/**
 * Handles payload packets from mod users.
 *
 * @author winnpixie
 */
public class ModPayloadListener implements PluginMessageListener {
    private final GenderModPlugin plugin;

    public ModPayloadListener(GenderModPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onPluginMessageReceived(@NotNull String channel, @NotNull Player player, byte[] message) {
        if (!channel.equals(ModConstants.SEND_GENDER_INFO)
                && !channel.equals(ModConstants.FORGE)
                && !channel.equals(ModConstants.SERVERBOUND_HELLO)) return;

        boolean hello = channel.equals(ModConstants.SERVERBOUND_HELLO);
        int maximumSize = hello ? InboundPacketGuard.MAX_HELLO_BYTES : InboundPacketGuard.MAX_SYNC_BYTES;
        if (message.length > maximumSize) {
            warn(player, "Rejected oversized %s payload from %%s (%d bytes)"
                    .formatted(hello ? "hello" : "sync", message.length));
            return;
        }

        if (!plugin.getInboundPacketGuard().tryAcquire(player.getUniqueId())) {
            warn(player, "Rate limit exceeded by %s; dropping client payloads");
            return;
        }

        byte[] payload = message.clone();
        plugin.getTaskDispatcher().runFor(player, () -> handlePayload(channel, player, payload));
    }

    private void handlePayload(String channel, Player player, byte[] message) {
        if (!player.isOnline()) return;

        if (channel.equals(ModConstants.SERVERBOUND_HELLO)) {
            handleHello(player, message);
            return;
        }

        ModUser user;
        try {
            user = plugin.getNetworkManager().deserializeUser(message, channel.equals(ModConstants.FORGE));
        } catch (IOException ex) {
            warn(player, ex, "Rejected malformed sync payload from %s");
            return;
        }

        if (!player.getUniqueId().equals(user.userId())) {
            warn(player, "Unauthorized configuration update by %s for " + user.userId());
            return;
        }

        plugin.getUserManager().put(user);
        plugin.getCustomLogger().debug("Stored %s as %s",
                player.getName(), user.configuration().generalOptions().genderIdentity().name());

        plugin.getNetworkManager().broadcast(player, user);
    }

    private void handleHello(Player player, byte[] message) {
        try {
            NetworkManager.HelloResult result = plugin.getNetworkManager().handleHello(player, message);
            if (!result.compatible()) {
                warn(player, "Sync protocol mismatch for %s: client=" + result.clientVersion()
                        + ", server=" + NetworkManager.SYNC_HELLO_VERSION);
                return;
            }
            plugin.getSyncStateCoordinator().onHelloAccepted(player);
        } catch (IOException ex) {
            warn(player, ex, "Rejected malformed sync hello from %s");
        }
    }

    private void warn(Player player, String message) {
        if (plugin.getInboundPacketGuard().shouldWarn(player.getUniqueId())) {
            plugin.getCustomLogger().warning(message, player.getName());
        }
    }

    private void warn(Player player, Throwable throwable, String message) {
        if (plugin.getInboundPacketGuard().shouldWarn(player.getUniqueId())) {
            plugin.getCustomLogger().warning(throwable, message, player.getName());
        }
    }
}
