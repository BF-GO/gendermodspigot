package dbrighthd.wildfiregendermodplugin.scheduler;

import dbrighthd.wildfiregendermodplugin.GenderModPlugin;
import org.bukkit.entity.Player;

/**
 * Routes player-bound work to the thread that currently owns that player.
 */
public final class TaskDispatcher {
    private final GenderModPlugin plugin;

    public TaskDispatcher(GenderModPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean runFor(Player player, Runnable task) {
        return player.getScheduler().run(plugin, ignored -> task.run(), null) != null;
    }
}
