package dbrighthd.wildfiregendermodplugin.networking;

import dbrighthd.wildfiregendermodplugin.GenderModPlugin;
import dbrighthd.wildfiregendermodplugin.networking.minecraft.CraftInputStream;
import dbrighthd.wildfiregendermodplugin.networking.minecraft.CraftOutputStream;
import dbrighthd.wildfiregendermodplugin.networking.wildfire.ModSyncPacket;
import dbrighthd.wildfiregendermodplugin.networking.wildfire.ModSyncPacketV2;
import dbrighthd.wildfiregendermodplugin.networking.wildfire.ModSyncPacketV3;
import dbrighthd.wildfiregendermodplugin.networking.wildfire.ModSyncPacketV4;
import dbrighthd.wildfiregendermodplugin.networking.wildfire.ModSyncPacketV5;
import dbrighthd.wildfiregendermodplugin.scheduler.TaskDispatcher;
import dbrighthd.wildfiregendermodplugin.wildfire.ModConstants;
import dbrighthd.wildfiregendermodplugin.wildfire.ModUser;
import org.bukkit.entity.Player;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Serializes mod data and routes outgoing messages to the thread that owns each recipient.
 *
 * @author winnpixie
 */
public class NetworkManager {
    public static final int SYNC_HELLO_VERSION = 1;

    private static final Map<Integer, ModSyncPacket> PACKET_FORMATS = Map.of(
            2, new ModSyncPacketV2(),
            3, new ModSyncPacketV3(),
            4, new ModSyncPacketV4(),
            5, new ModSyncPacketV5()
    );

    private final GenderModPlugin plugin;
    private final TaskDispatcher taskDispatcher;

    private ModSyncPacket packetFormat;

    public NetworkManager(GenderModPlugin plugin, TaskDispatcher taskDispatcher) {
        this.plugin = plugin;
        this.taskDispatcher = taskDispatcher;
    }

    public boolean init() {
        int protocolVersion = plugin.getConfig().getInt("mod.protocol", -1);
        packetFormat = protocolVersion == -1 ? PACKET_FORMATS.get(5)
                : PACKET_FORMATS.get(protocolVersion);
        if (packetFormat == null) return false;

        plugin.getCustomLogger().info("Using protocol %d for mod version(s) %s",
                packetFormat.getVersion(), packetFormat.getModRange());

        return true;
    }

    public boolean supportsHello() {
        return packetFormat != null && packetFormat.getVersion() == 5;
    }

    /**
     * Sends every stored user to one player on that player's owning thread.
     */
    public void syncAllTo(Player target) {
        List<EncodedUser> users = plugin.getUserManager().snapshot().stream()
                .map(this::encodeUser)
                .toList();

        taskDispatcher.runFor(target, () -> {
            for (EncodedUser user : users) {
                sendEncoded(target, user);
            }
        });
    }

    /**
     * Broadcasts one changed user without touching player state outside its owner thread.
     */
    public void broadcast(ModUser user) {
        EncodedUser encoded = encodeUser(user);

        taskDispatcher.runGlobal(() -> {
            Player[] recipients = plugin.getServer().getOnlinePlayers().toArray(Player[]::new);
            for (Player recipient : recipients) {
                taskDispatcher.runFor(recipient, () -> sendEncoded(recipient, encoded));
            }
        });
    }

    public ModUser deserializeUser(byte[] data, boolean forge) {
        try (CraftInputStream input = CraftInputStream.ofBytes(data)) {
            if (forge) input.readByte();

            ModUser user = packetFormat.read(input);
            if (input.available() != 0) {
                throw new IOException("Unexpected trailing data in sync payload");
            }
            return user;
        } catch (IOException | RuntimeException ex) {
            plugin.getCustomLogger().warning(ex, "Could not deserialize user (forge=%s)", forge);
        }

        return null;
    }

    public void handleHello(Player target, byte[] data) {
        try (CraftInputStream input = CraftInputStream.ofBytes(data)) {
            int clientVersion = input.readVarInt();
            if (input.available() != 0) {
                throw new IOException("Unexpected trailing data in sync hello");
            }
            if (clientVersion != SYNC_HELLO_VERSION) {
                plugin.getCustomLogger().warning(
                        "Sync protocol mismatch for %s: client=%d, server=%d",
                        target.getName(), clientVersion, SYNC_HELLO_VERSION);
            }

            try (ByteArrayOutputStream payload = new ByteArrayOutputStream();
                 CraftOutputStream output = new CraftOutputStream(payload)) {
                output.writeVarInt(SYNC_HELLO_VERSION);
                sendIfListening(target, ModConstants.CLIENTBOUND_HELLO, payload.toByteArray());
            }
        } catch (IOException | RuntimeException ex) {
            plugin.getCustomLogger().warning(ex, "Could not handle sync hello from %s", target.getName());
        }
    }

    private EncodedUser encodeUser(ModUser user) {
        return new EncodedUser(
                user.userId(),
                serializeUser(user, false),
                serializeUser(user, true));
    }

    private byte[] serializeUser(ModUser user, boolean forge) {
        try (ByteArrayOutputStream payload = new ByteArrayOutputStream();
             CraftOutputStream output = new CraftOutputStream(payload)) {
            if (forge) output.writeByte(1);

            packetFormat.write(user, output);
            return payload.toByteArray();
        } catch (IOException | RuntimeException ex) {
            plugin.getCustomLogger().warning(ex, "Could not serialize user (forge=%s)", forge);
        }

        return new byte[0];
    }

    private void sendEncoded(Player target, EncodedUser user) {
        if (!target.isOnline() || target.getUniqueId().equals(user.userId())) return;

        if (user.fabricData().length > 0) {
            sendIfListening(target, ModConstants.SYNC, user.fabricData());
        }
        if (user.forgeData().length > 0) {
            sendIfListening(target, ModConstants.FORGE, user.forgeData());
        }
    }

    private void sendIfListening(Player target, String channel, byte[] data) {
        Set<String> listeningChannels = target.getListeningPluginChannels();
        if (listeningChannels.contains(channel)) {
            target.sendPluginMessage(plugin, channel, data);
        }
    }

    private record EncodedUser(UUID userId, byte[] fabricData, byte[] forgeData) {
    }
}
