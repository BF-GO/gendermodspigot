package dbrighthd.wildfiregendermodplugin.networking;

import dbrighthd.wildfiregendermodplugin.networking.minecraft.CraftOutputStream;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Beta5PacketTest {
    @Test
    void configurationHelloUsesVersionListAndAcceptsOnlyExactReply() throws IOException {
        assertArrayEquals(new byte[]{1, 2}, Beta5Packet.CLIENTBOUND_HELLO);
        assertTrue(Beta5Packet.isCompatibleHello(new byte[]{2}));
        assertFalse(Beta5Packet.isCompatibleHello(new byte[]{1}));
        assertThrows(IOException.class, () -> Beta5Packet.isCompatibleHello(new byte[]{2, 0}));
    }

    @Test
    void validatesCompactMaleAndClientboundUuidPrefix() throws IOException {
        UUID id = UUID.fromString("12345678-1234-5678-9abc-def012345678");
        assertEquals("MALE", Beta5Packet.validateConfig(new byte[]{0}));
        assertArrayEquals(new byte[]{
                0x12, 0x34, 0x56, 0x78, 0x12, 0x34, 0x56, 0x78,
                (byte) 0x9a, (byte) 0xbc, (byte) 0xde, (byte) 0xf0, 0x12, 0x34, 0x56, 0x78, 0
        }, Beta5Packet.clientbound(id, new byte[]{0}));
        assertThrows(IOException.class, () -> Beta5Packet.validateConfig(new byte[]{0, 1}));
    }

    @Test
    void validatesFullBeta5ProfileAndRejectsMalformedValues() throws IOException {
        byte[] profile = femaleProfile(0.6f);
        assertEquals("FEMALE", Beta5Packet.validateConfig(profile));
        assertThrows(IOException.class, () -> Beta5Packet.validateConfig(new byte[]{1, 0}));
        assertThrows(IOException.class, () -> Beta5Packet.validateConfig(femaleProfile(Float.NaN)));
    }

    private static byte[] femaleProfile(float bustSize) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (CraftOutputStream output = new CraftOutputStream(bytes)) {
            output.writeBoolean(true); // compact config present
            output.writeVarInt(0); // female
            output.writeFloat(0); // x offset
            output.writeFloat(0); // y offset
            output.writeFloat(0); // z offset
            output.writeFloat(bustSize);
            output.writeFloat(0); // cleavage
            output.writeBoolean(true); // physics enabled
            output.writeBoolean(true); // uniboob
            output.writeFloat(0.333f); // bounce
            output.writeFloat(0.75f); // floppiness
            for (int i = 0; i < 4; i++) output.writeVarInt(0); // four empty UV layouts
            output.writeBoolean(true); // hurt sounds
            output.writeFloat(1); // pitch
            output.writeBoolean(true); // in armor
        }
        return bytes.toByteArray();
    }
}
