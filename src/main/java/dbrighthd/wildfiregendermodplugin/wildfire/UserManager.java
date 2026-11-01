package dbrighthd.wildfiregendermodplugin.wildfire;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class UserManager {
    private final ConcurrentMap<UUID, ModUser> users = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, byte[]> beta5Users = new ConcurrentHashMap<>();

    public void put(ModUser user) {
        users.put(user.userId(), user);
    }

    public void remove(UUID userId) {
        users.remove(userId);
        beta5Users.remove(userId);
    }

    public void putBeta5(UUID userId, byte[] config) {
        beta5Users.put(userId, config.clone());
    }

    public byte[] getBeta5(UUID userId) {
        byte[] config = beta5Users.get(userId);
        return config == null ? null : config.clone();
    }

    public ModUser get(UUID userId) {
        return users.get(userId);
    }

    public List<ModUser> snapshot() {
        return List.copyOf(users.values());
    }

    public void clear() {
        users.clear();
        beta5Users.clear();
    }
}
