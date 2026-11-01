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
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Serializes mod data and routes it through each recipient's registered transport.
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
    private boolean beta5;

    public NetworkManager(GenderModPlugin plugin) {
        this.plugin = plugin;
        this.taskDispatcher = plugin.getTaskDispatcher();
    }

    public boolean init() {
        int protocolVersion = plugin.getConfig().getInt("mod.protocol", -1);
        beta5 = protocolVersion == -1 || protocolVersion == 6;
        if (beta5) {
            packetFormat = null;
            plugin.getCustomLogger().info("Using protocol 6 for mod version 5.0.0-Beta.5");
            return true;
        }
        packetFormat = PACKET_FORMATS.get(protocolVersion);
        if (packetFormat == null) return false;

        plugin.getCustomLogger().info("Using protocol %d for mod version(s) %s",
                packetFormat.getVersion(), packetFormat.getModRange());
        return true;
    }

    public boolean supportsHello() {
        return packetFormat != null && packetFormat.getVersion() >= 5;
    }

    public boolean supportsBeta5() {
        return beta5;
    }

    /** Sends a validated Beta.5 compact profile with its owner UUID prepended. */
    public void syncBeta5UserTo(Player target, UUID userId) {
        byte[] config = plugin.getUserManager().getBeta5(userId);
        if (config == null) return;
        byte[] encoded = encodeBeta5(userId, config);
        taskDispatcher.runFor(target, () -> sendBeta5Encoded(target, userId, encoded));
    }

    public void broadcastBeta5(Player source, byte[] config) {
        UUID userId = source.getUniqueId();
        byte[] encoded = encodeBeta5(userId, config);
        for (Player recipient : Set.copyOf(source.getTrackedBy())) {
            if (!recipient.getUniqueId().equals(userId)) {
                taskDispatcher.runFor(recipient, () -> sendBeta5Encoded(recipient, userId, encoded));
            }
        }
    }

    private byte[] encodeBeta5(UUID userId, byte[] config) {
        try {
            return Beta5Packet.clientbound(userId, config);
        } catch (IOException ex) {
            // ByteArrayOutputStream cannot fail, but keep failure isolated from the tick thread.
            throw new IllegalStateException("Could not encode Beta.5 profile", ex);
        }
    }

    private void sendBeta5Encoded(Player target, UUID userId, byte[] data) {
        if (!target.isOnline() || target.getUniqueId().equals(userId)) return;
        if (ModConstants.BETA5_CLIENTBOUND_SYNC.equals(plugin.getSyncStateCoordinator().preferredChannel(target))) {
            target.sendPluginMessage(plugin, ModConstants.BETA5_CLIENTBOUND_SYNC, data);
        }
    }

    /**
     * Sends one stored profile to one viewer on the viewer's owning thread.
     */
    public void syncUserTo(Player target, ModUser user) {
        EncodedUser encoded = encodeUser(user);
        taskDispatcher.runFor(target, () -> sendEncoded(target, encoded));
    }

    /**
     * Broadcasts one changed profile only to players currently tracking its owner.
     * This method must be invoked from the source player's owning thread.
     */
    public void broadcast(Player source, ModUser user) {
        EncodedUser encoded = encodeUser(user);
        Set<Player> recipients = Set.copyOf(source.getTrackedBy());
        for (Player recipient : recipients) {
            if (!recipient.getUniqueId().equals(source.getUniqueId())) {
                taskDispatcher.runFor(recipient, () -> sendEncoded(recipient, encoded));
            }
        }
    }

    public ModUser deserializeUser(byte[] data, boolean forge) throws IOException {
        try (CraftInputStream input = CraftInputStream.ofBytes(data)) {
            if (forge && input.readUnsignedByte() != 1) {
                throw new IOException("Invalid Forge packet discriminator");
            }

            ModUser user = packetFormat.read(input);
            if (input.available() != 0) {
                throw new IOException("Unexpected trailing data in sync payload");
            }
            ModUserValidator.validate(user);
            return user;
        } catch (RuntimeException ex) {
            throw new IOException("Could not deserialize user (forge=" + forge + ")", ex);
        }
    }

    public HelloResult handleHello(Player target, byte[] data) throws IOException {
        try (CraftInputStream input = CraftInputStream.ofBytes(data)) {
            int clientVersion = input.readVarInt();
            if (input.available() != 0) {
                throw new IOException("Unexpected trailing data in sync hello");
            }

            try (ByteArrayOutputStream payload = new ByteArrayOutputStream();
                 CraftOutputStream output = new CraftOutputStream(payload)) {
                output.writeVarInt(SYNC_HELLO_VERSION);
                sendIfListening(target, ModConstants.CLIENTBOUND_HELLO, payload.toByteArray());
            }
            return new HelloResult(clientVersion, clientVersion == SYNC_HELLO_VERSION);
        } catch (RuntimeException ex) {
            throw new IOException("Could not handle sync hello", ex);
        }
    }

    private EncodedUser encodeUser(ModUser user) {
        return new EncodedUser(user.userId(), serializeUser(user, false), serializeUser(user, true));
    }

    private byte[] serializeUser(ModUser user, boolean forge) {
        try (ByteArrayOutputStream payload = new ByteArrayOutputStream();
             CraftOutputStream output = new CraftOutputStream(payload)) {
            if (forge) output.writeByte(1);

            packetFormat.write(user, output);
            return payload.toByteArray();
        } catch (IOException | RuntimeException ex) {
            plugin.getCustomLogger().warning(ex, "Could not serialize user (forge=%s)", forge);
            return new byte[0];
        }
    }

    private void sendEncoded(Player target, EncodedUser user) {
        if (!target.isOnline() || target.getUniqueId().equals(user.userId())) return;

        String channel = plugin.getSyncStateCoordinator().preferredChannel(target);
        if (ModConstants.SYNC.equals(channel) && user.fabricData().length > 0) {
            target.sendPluginMessage(plugin, channel, user.fabricData());
        } else if (ModConstants.FORGE.equals(channel) && user.forgeData().length > 0) {
            target.sendPluginMessage(plugin, channel, user.forgeData());
        }
    }

    private void sendIfListening(Player target, String channel, byte[] data) {
        if (target.getListeningPluginChannels().contains(channel)) {
            target.sendPluginMessage(plugin, channel, data);
        }
    }

    private record EncodedUser(UUID userId, byte[] fabricData, byte[] forgeData) {
    }

    public record HelloResult(int clientVersion, boolean compatible) {
    }
}
