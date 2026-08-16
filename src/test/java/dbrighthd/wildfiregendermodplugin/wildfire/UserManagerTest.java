package dbrighthd.wildfiregendermodplugin.wildfire;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserManagerTest {
    @Test
    void supportsConcurrentUpdatesRemovalsAndSnapshots() throws Exception {
        UserManager manager = new UserManager();
        int workerCount = 8;
        int usersPerWorker = 250;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(workerCount);

        try {
            List<Future<?>> writers = new ArrayList<>();
            Set<UUID> expectedIds = new HashSet<>();
            for (int worker = 0; worker < workerCount; worker++) {
                int workerId = worker;
                for (int index = 0; index < usersPerWorker; index++) {
                    expectedIds.add(new UUID(workerId, index));
                }
                writers.add(executor.submit(() -> {
                    start.await();
                    for (int index = 0; index < usersPerWorker; index++) {
                        manager.put(new ModUser(new UUID(workerId, index), null));
                        if (index % 10 == 0) {
                            manager.snapshot();
                        }
                    }
                    return null;
                }));
            }

            start.countDown();
            for (Future<?> writer : writers) {
                writer.get();
            }

            List<ModUser> completedSnapshot = manager.snapshot();
            assertEquals(workerCount * usersPerWorker, completedSnapshot.size());
            assertThrows(UnsupportedOperationException.class, completedSnapshot::clear);
            assertEquals(expectedIds,
                    completedSnapshot.stream().map(ModUser::userId).collect(java.util.stream.Collectors.toSet()));

            List<Future<?>> removers = new ArrayList<>();
            for (int worker = 0; worker < workerCount; worker++) {
                int workerId = worker;
                removers.add(executor.submit(() -> {
                    for (int index = 0; index < usersPerWorker; index += 2) {
                        manager.remove(new UUID(workerId, index));
                        manager.snapshot();
                    }
                }));
            }
            for (Future<?> remover : removers) {
                remover.get();
            }

            Set<UUID> expectedRemainingIds = expectedIds.stream()
                    .filter(userId -> userId.getLeastSignificantBits() % 2 != 0)
                    .collect(java.util.stream.Collectors.toSet());
            Set<UUID> remainingIds = manager.snapshot().stream()
                    .map(ModUser::userId)
                    .collect(java.util.stream.Collectors.toSet());
            assertEquals(expectedRemainingIds, remainingIds);
            manager.clear();
            assertTrue(manager.snapshot().isEmpty());
        } finally {
            executor.shutdownNow();
        }
    }
}
