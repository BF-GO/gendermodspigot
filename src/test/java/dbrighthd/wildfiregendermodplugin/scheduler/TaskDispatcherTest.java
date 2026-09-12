package dbrighthd.wildfiregendermodplugin.scheduler;

import dbrighthd.wildfiregendermodplugin.GenderModPlugin;
import io.papermc.paper.threadedregions.scheduler.EntityScheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TaskDispatcherTest {
    @Test
    void delegatesPlayerWorkToItsEntityScheduler() {
        GenderModPlugin plugin = mock(GenderModPlugin.class);
        Player player = mock(Player.class);
        EntityScheduler scheduler = mock(EntityScheduler.class);
        AtomicBoolean executed = new AtomicBoolean();
        when(player.getScheduler()).thenReturn(scheduler);
        when(scheduler.run(eq(plugin), any(), isNull())).thenAnswer(invocation -> {
            invocation.<java.util.function.Consumer<ScheduledTask>>getArgument(1)
                    .accept(mock(ScheduledTask.class));
            return mock(ScheduledTask.class);
        });

        assertTrue(new TaskDispatcher(plugin).runFor(player, () -> executed.set(true)));
        assertTrue(executed.get());
    }

    @Test
    void ignoresWorkWhenThePlayerSchedulerIsRetired() {
        GenderModPlugin plugin = mock(GenderModPlugin.class);
        Player player = mock(Player.class);
        EntityScheduler scheduler = mock(EntityScheduler.class);
        AtomicBoolean executed = new AtomicBoolean();
        when(player.getScheduler()).thenReturn(scheduler);
        when(scheduler.run(eq(plugin), any(), isNull())).thenReturn(null);

        assertFalse(new TaskDispatcher(plugin).runFor(player, () -> executed.set(true)));
        assertFalse(executed.get());
    }
}
