package dbrighthd.wildfiregendermodplugin.networking;

import dbrighthd.wildfiregendermodplugin.wildfire.ModUser;
import dbrighthd.wildfiregendermodplugin.wildfire.setup.BreastOptions;
import dbrighthd.wildfiregendermodplugin.wildfire.setup.GenderIdentities;
import dbrighthd.wildfiregendermodplugin.wildfire.setup.GeneralOptions;
import dbrighthd.wildfiregendermodplugin.wildfire.setup.ModConfiguration;
import dbrighthd.wildfiregendermodplugin.wildfire.setup.PhysicsOptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ModUserValidatorTest {
    @Test
    void enforcesV5NumericBoundaries() {
        assertDoesNotThrow(() -> ModUserValidator.validate(user(0.8f, 1.2f, 0.5f, 1.0f,
                -1.0f, 1.0f, -1.0f, 0.1f), 5));

        assertThrows(IOException.class, () -> ModUserValidator.validate(user(0.81f, 1.0f, 0.2f, 0.5f,
                0.0f, 0.0f, 0.0f, 0.0f), 5));
        assertThrows(IOException.class, () -> ModUserValidator.validate(user(0.5f, 1.21f, 0.2f, 0.5f,
                0.0f, 0.0f, 0.0f, 0.0f), 5));
        assertThrows(IOException.class, () -> ModUserValidator.validate(user(0.5f, 1.0f, 0.51f, 0.5f,
                0.0f, 0.0f, 0.0f, 0.0f), 5));
        assertThrows(IOException.class, () -> ModUserValidator.validate(user(0.5f, 1.0f, 0.2f, 0.24f,
                0.0f, 0.0f, 0.0f, 0.0f), 5));
        assertThrows(IOException.class, () -> ModUserValidator.validate(user(0.5f, 1.0f, 0.2f, 0.5f,
                1.01f, 0.0f, 0.0f, 0.0f), 5));
        assertThrows(IOException.class, () -> ModUserValidator.validate(user(0.5f, 1.0f, 0.2f, 0.5f,
                0.0f, -1.01f, 0.0f, 0.0f), 5));
        assertThrows(IOException.class, () -> ModUserValidator.validate(user(0.5f, 1.0f, 0.2f, 0.5f,
                0.0f, 0.0f, 0.01f, 0.0f), 5));
        assertThrows(IOException.class, () -> ModUserValidator.validate(user(0.5f, 1.0f, 0.2f, 0.5f,
                0.0f, 0.0f, 0.0f, 0.11f), 5));
    }

    @Test
    void rejectsNonFiniteValuesForLegacyProtocols() {
        assertThrows(IOException.class, () -> ModUserValidator.validate(user(Float.NaN, 1.0f, 0.2f, 0.5f,
                0.0f, 0.0f, 0.0f, 0.0f), 2));
        assertThrows(IOException.class, () -> ModUserValidator.validate(user(0.5f, 1.0f,
                Float.POSITIVE_INFINITY, 0.5f, 0.0f, 0.0f, 0.0f, 0.0f), 4));

        assertDoesNotThrow(() -> ModUserValidator.validate(user(3.0f, -2.0f, 8.0f, -4.0f,
                9.0f, -9.0f, 4.0f, 5.0f), 4));
    }

    private static ModUser user(float bust, float voice, float buoyancy, float floppiness,
                                float x, float y, float z, float cleavage) {
        return new ModUser(UUID.randomUUID(), new ModConfiguration(
                new GeneralOptions(GenderIdentities.OTHER, true, voice, true),
                new PhysicsOptions(true, false, buoyancy, floppiness),
                new BreastOptions(bust, x, y, z, true, cleavage),
                null));
    }
}
