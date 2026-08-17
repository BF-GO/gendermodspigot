package dbrighthd.wildfiregendermodplugin.networking;

import dbrighthd.wildfiregendermodplugin.wildfire.ModUser;
import dbrighthd.wildfiregendermodplugin.wildfire.setup.BreastOptions;
import dbrighthd.wildfiregendermodplugin.wildfire.setup.GeneralOptions;
import dbrighthd.wildfiregendermodplugin.wildfire.setup.PhysicsOptions;

import java.io.IOException;

/**
 * Validates values which are structurally valid on the wire but unsafe for clients.
 */
final class ModUserValidator {
    private ModUserValidator() {
    }

    static void validate(ModUser user, int protocolVersion) throws IOException {
        GeneralOptions general = user.configuration().generalOptions();
        PhysicsOptions physics = user.configuration().physicsOptions();
        BreastOptions breast = user.configuration().breastOptions();

        requireFinite("bust size", breast.bustSize());
        requireFinite("voice pitch", general.voicePitch());
        requireFinite("bounce multiplier", physics.buoyancy());
        requireFinite("floppiness", physics.floppiness());
        requireFinite("X offset", breast.xOffset());
        requireFinite("Y offset", breast.yOffset());
        requireFinite("Z offset", breast.zOffset());
        requireFinite("cleavage", breast.cleavage());

        if (protocolVersion != 5) return;

        requireRange("bust size", breast.bustSize(), 0.0f, 0.8f);
        requireRange("voice pitch", general.voicePitch(), 0.8f, 1.2f);
        requireRange("bounce multiplier", physics.buoyancy(), 0.0f, 0.5f);
        requireRange("floppiness", physics.floppiness(), 0.25f, 1.0f);
        requireRange("X offset", breast.xOffset(), -1.0f, 1.0f);
        requireRange("Y offset", breast.yOffset(), -1.0f, 1.0f);
        requireRange("Z offset", breast.zOffset(), -1.0f, 0.0f);
        requireRange("cleavage", breast.cleavage(), 0.0f, 0.1f);
    }

    private static void requireFinite(String name, float value) throws IOException {
        if (!Float.isFinite(value)) throw new IOException("Non-finite " + name + ": " + value);
    }

    private static void requireRange(String name, float value, float minimum, float maximum) throws IOException {
        if (value < minimum || value > maximum) {
            throw new IOException("Out-of-range " + name + ": " + value
                    + " (expected " + minimum + " to " + maximum + ")");
        }
    }
}
