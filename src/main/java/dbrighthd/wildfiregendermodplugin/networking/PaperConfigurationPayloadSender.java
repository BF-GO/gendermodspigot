package dbrighthd.wildfiregendermodplugin.networking;

import io.papermc.paper.connection.PlayerConfigurationConnection;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Sends the initial minecraft:register payload before Paper knows client channels. */
final class PaperConfigurationPayloadSender {
    void send(PlayerConfigurationConnection connection, String channel, byte[] data) throws ReflectiveOperationException {
        // Paper's public configuration sendPluginMessage requires a previously registered
        // client channel. The initial REGISTER packet necessarily precedes that registration.
        Class<?> commonConnection = Class.forName("io.papermc.paper.connection.PaperCommonConnection");
        Field listenerField = commonConnection.getDeclaredField("packetListener");
        listenerField.setAccessible(true);
        Object listener = listenerField.get(connection);

        Class<?> identifierClass = Class.forName("net.minecraft.resources.Identifier");
        Object identifier = identifierClass.getMethod("parse", String.class).invoke(null, channel);
        Class<?> payloadType = Class.forName("net.minecraft.network.protocol.common.custom.CustomPacketPayload");
        Class<?> discardedType = Class.forName("net.minecraft.network.protocol.common.custom.DiscardedPayload");
        Constructor<?> discardedConstructor = discardedType.getConstructor(identifierClass, byte[].class);
        Object payload = discardedConstructor.newInstance(identifier, data);
        Class<?> packetType = Class.forName("net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket");
        Object packet = packetType.getConstructor(payloadType).newInstance(payload);

        for (Method method : listener.getClass().getMethods()) {
            if (method.getName().equals("send") && method.getParameterCount() == 1
                    && method.getParameterTypes()[0].isInstance(packet)) {
                method.invoke(listener, packet);
                return;
            }
        }
        throw new NoSuchMethodException("Paper configuration packet send(Packet)");
    }
}
