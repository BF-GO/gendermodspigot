package dbrighthd.wildfiregendermodplugin.scheduler;

import dbrighthd.wildfiregendermodplugin.GenderModPlugin;
import io.papermc.paper.threadedregions.scheduler.EntityScheduler;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TaskDispatcherTest {
    @Test
    void delegatesPlayerWorkToItsEntitySchedulerWithoutRetiredRetry() {
        GenderModPlugin plugin = mock(GenderModPlugin.class);
        Player player = mock(Player.class);
        EntityScheduler scheduler = mock(EntityScheduler.class);
        Runnable task = mock(Runnable.class);
        when(player.getScheduler()).thenReturn(scheduler);
        when(scheduler.execute(eq(plugin), eq(task), isNull(), eq(1L))).thenReturn(true);

        boolean scheduled = new TaskDispatcher(plugin).runFor(player, task);

        assertTrue(scheduled);
        verify(scheduler).execute(plugin, task, null, 1L);
    }
}
