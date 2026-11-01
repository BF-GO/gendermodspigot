package dbrighthd.wildfiregendermodplugin.networking;

import dbrighthd.wildfiregendermodplugin.GenderModPlugin;
import dbrighthd.wildfiregendermodplugin.wildfire.ModConstants;
import io.papermc.paper.connection.PlayerConfigurationConnection;
import io.papermc.paper.connection.PlayerConnection;
import io.papermc.paper.event.connection.configuration.AsyncPlayerConnectionConfigureEvent;
import io.papermc.paper.event.connection.configuration.PlayerConnectionInitialConfigureEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Completes Beta.5's server-initiated configuration-phase negotiation. */
public final class Beta5Handshake implements Listener, PluginMessageListener {
    private final GenderModPlugin plugin;
    private final PaperConfigurationPayloadSender rawSender;
    private final ConcurrentMap<UUID, PendingHello> pending = new ConcurrentHashMap<>();

    public Beta5Handshake(GenderModPlugin plugin) {
        this(plugin, new PaperConfigurationPayloadSender());
    }

    Beta5Handshake(GenderModPlugin plugin, PaperConfigurationPayloadSender rawSender) {
        this.plugin = plugin;
        this.rawSender = rawSender;
    }

    @EventHandler
    public void onInitialConfigure(PlayerConnectionInitialConfigureEvent event) {
        // Fabric waits for a server REGISTER packet before advertising its
        // configuration receivers. Paper only sends REGISTER in the play phase.
        try {
            rawSender.send(event.getConnection(), "minecraft:register",
                    ModConstants.BETA5_SERVERBOUND_HELLO.getBytes(StandardCharsets.US_ASCII));
        } catch (ReflectiveOperationException ex) {
            plugin.getCustomLogger().warning(ex, "Could not advertise Beta.5 configuration channel to %s",
                    event.getConnection().getProfile().getName());
        }
    }

    @EventHandler
    public void onConfigure(AsyncPlayerConnectionConfigureEvent event) {
        PlayerConfigurationConnection connection = event.getConnection();
        UUID id = connection.getProfile().getId();
        if (id == null) return;
        String brand = connection.getClientBrandName();
        boolean advertised = connection.getListeningPluginChannels().contains(ModConstants.BETA5_CLIENTBOUND_HELLO);
        if (!advertised && brand != null && !brand.toLowerCase(Locale.ROOT).contains("fabric")) {
            return;
        }

        CompletableFuture<Boolean> response = new CompletableFuture<>();
        PendingHello attempt = new PendingHello(connection, response);
        pending.put(id, attempt);
        try {
            // Paper suppresses ordinary plugin messages until the client has sent
            // minecraft:register. Fabric can decode this typed hello without it.
            rawSender.send(connection, ModConstants.BETA5_CLIENTBOUND_HELLO,
                    Beta5Packet.CLIENTBOUND_HELLO.clone());
            if (response.get(2, TimeUnit.SECONDS) && pending.get(id) == attempt) {
                plugin.getSyncStateCoordinator().onBeta5HelloAccepted(id);
                plugin.getCustomLogger().debug("Negotiated Beta.5 sync with %s", connection.getProfile().getName());
            } else {
                plugin.getCustomLogger().warning("Unsupported Beta.5 sync hello from %s", connection.getProfile().getName());
            }
        } catch (TimeoutException ex) {
            plugin.getCustomLogger().debug("No Beta.5 sync hello response from %s (brand=%s, advertised=%s)",
                    connection.getProfile().getName(), brand, advertised);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } catch (Exception ex) {
            plugin.getCustomLogger().warning(ex, "Beta.5 sync hello failed for %s", connection.getProfile().getName());
        } finally {
            pending.remove(id, attempt);
        }
    }

    @Override
    public void onPluginMessageReceived(@NotNull String channel, @NotNull Player player, byte @NotNull [] message) {
        // The Beta.5 hello exists only during the configuration phase.
    }

    @Override
    public void onPluginMessageReceived(@NotNull String channel, @NotNull PlayerConnection source, byte @NotNull [] message) {
        if (!ModConstants.BETA5_SERVERBOUND_HELLO.equals(channel)
                || !(source instanceof PlayerConfigurationConnection connection)) return;
        UUID id = connection.getProfile().getId();
        PendingHello attempt = pending.get(id);
        if (attempt == null || attempt.connection() != connection) return;
        try {
            if (message.length > InboundPacketGuard.MAX_HELLO_BYTES) throw new IOException("Oversized Beta.5 hello");
            attempt.response().complete(Beta5Packet.isCompatibleHello(message));
        } catch (IOException ex) {
            attempt.response().completeExceptionally(ex);
        }
    }

    public void clear() {
        pending.clear();
    }

    private record PendingHello(PlayerConfigurationConnection connection,
                                CompletableFuture<Boolean> response) {
    }
}
