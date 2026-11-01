package dbrighthd.wildfiregendermodplugin.networking;

import dbrighthd.wildfiregendermodplugin.networking.minecraft.CraftInputStream;
import dbrighthd.wildfiregendermodplugin.networking.minecraft.CraftOutputStream;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Wire format of Female Gender Mod 5.0.0-Beta.5's compact AvatarConfig. */
public final class Beta5Packet {
    public static final int HELLO_VERSION = 2;
    public static final byte[] CLIENTBOUND_HELLO = {1, HELLO_VERSION};

    private Beta5Packet() {
    }

    public static boolean isCompatibleHello(byte[] data) throws IOException {
        try (CraftInputStream input = CraftInputStream.ofBytes(data)) {
            int version = input.readVarInt();
            if (input.available() != 0) throw new IOException("Trailing data in Beta.5 hello");
            return version == HELLO_VERSION;
        }
    }

    public static String validateConfig(byte[] data) throws IOException {
        try (CraftInputStream input = CraftInputStream.ofBytes(data)) {
            if (!input.readBoolean()) {
                if (input.available() != 0) throw new IOException("Trailing data in male profile");
                return "MALE";
            }

            int gender = input.readVarInt();
            if (gender < 0 || gender > 2) throw new IOException("Invalid Beta.5 gender: " + gender);
            // Breasts: offsets, size, cleavage, then the compact physics record.
            for (int i = 0; i < 5; i++) requireFinite(input.readFloat());
            if (input.readBoolean()) {
                input.readBoolean(); // uniboob
                requireFinite(input.readFloat()); // bounce multiplier
                requireFinite(input.readFloat()); // floppiness
            }

            // Skin left/right and overlay left/right. Each map has up to five faces.
            for (int layout = 0; layout < 4; layout++) {
                int count = input.readVarInt();
                if (count < 0 || count > 5) throw new IOException("Invalid Beta.5 UV face count: " + count);
                Set<Integer> directions = new HashSet<>();
                for (int i = 0; i < count; i++) {
                    int direction = input.readVarInt();
                    if (direction < 0 || direction > 4 || !directions.add(direction)) {
                        throw new IOException("Invalid or duplicate Beta.5 UV direction: " + direction);
                    }
                    for (int coordinate = 0; coordinate < 4; coordinate++) input.readVarInt();
                }
            }

            input.readBoolean(); // hurt sounds
            requireFinite(input.readFloat()); // voice pitch
            input.readBoolean(); // show breasts in armor
            if (input.available() != 0) throw new IOException("Trailing data in Beta.5 profile");
            return switch (gender) {
                case 0 -> "FEMALE";
                case 1 -> "MALE";
                default -> "OTHER";
            };
        }
    }

    public static byte[] clientbound(UUID userId, byte[] config) throws IOException {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             CraftOutputStream output = new CraftOutputStream(bytes)) {
            output.writeUUID(userId);
            output.write(config);
            return bytes.toByteArray();
        }
    }

    private static void requireFinite(float value) throws IOException {
        if (!Float.isFinite(value)) throw new IOException("Non-finite Beta.5 profile value");
    }
}
