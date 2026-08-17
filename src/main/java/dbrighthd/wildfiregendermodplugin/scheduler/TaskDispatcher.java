package dbrighthd.wildfiregendermodplugin.scheduler;

import dbrighthd.wildfiregendermodplugin.GenderModPlugin;
import org.bukkit.entity.Player;

/**
 * Routes work to Paper's Folia-compatible schedulers.
 */
public class TaskDispatcher {
    private final GenderModPlugin plugin;

    public TaskDispatcher(GenderModPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean runFor(Player player, Runnable task) {
        return player.getScheduler().execute(plugin, task, null, 1L);
    }
}
