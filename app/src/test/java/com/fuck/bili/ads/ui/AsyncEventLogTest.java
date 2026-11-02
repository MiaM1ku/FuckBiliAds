package com.fuck.bili.ads.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.After;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class AsyncEventLogTest {
    private static final long RETRY_MILLIS = 500L;
    private final ScheduledExecutorService io = Executors.newSingleThreadScheduledExecutor();
    private final ExecutorService callers = Executors.newCachedThreadPool();

    @After
    public void stopWorkers() throws Exception {
        callers.shutdownNow();
        io.shutdownNow();
        assertTrue(callers.awaitTermination(2, TimeUnit.SECONDS));
        assertTrue(io.awaitTermination(2, TimeUnit.SECONDS));
    }

    @Test(timeout = 10000)
    public void producerReturnsWhileAppendIsBlocked() throws Exception {
        AppendGate sink = new AppendGate();
        AsyncEventLog log = threaded(() -> sink, 500, 2000);
        try {
            log.write("first");
            await(sink.entered);
            callers.submit(() -> {
                log.write("second");
                log.write("third");
            }).get(1, TimeUnit.SECONDS);
            assertEquals(1L, sink.release.getCount());
            assertEquals(3, log.pendingCount());
            assertEquals("first\nsecond\nthird\n", log.recent());
        } finally {
            sink.release.countDown();
        }
        assertTrue(log.awaitIdle(2000L));
        assertEquals("first\nsecond\nthird\n", log.read());
        assertEquals(0, log.pendingCount());
    }

    @Test(timeout = 10000)
    public void producerReturnsWhileContextResolutionIsBlocked() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        RecordingSink sink = new RecordingSink();
        AsyncEventLog log = threaded(() -> {
            entered.countDown();
            await(release);
            return sink;
        }, 500, 2000);
        try {
            log.write("early");
            await(entered);
            callers.submit(() -> log.write("later")).get(1, TimeUnit.SECONDS);
            assertEquals(1L, release.getCount());
            assertEquals(2, log.pendingCount());
        } finally {
            release.countDown();
        }
        assertTrue(log.awaitIdle(2000L));
        assertEquals("early\nlater\n", log.read());
    }

    @Test
    public void missingContextRetainsBoundedQueueAndOneRateLimitedRetry() throws Exception {
        ManualScheduler scheduler = new ManualScheduler();
        AtomicReference<AsyncEventLog.Sink> ready = new AtomicReference<>();
        AtomicInteger resolutions = new AtomicInteger();
        AsyncEventLog log = new AsyncEventLog(() -> {
            resolutions.incrementAndGet();
            return ready.get();
        }, scheduler, 2, 3, RETRY_MILLIS);
        log.write("boot-1");
        assertEquals(0L, scheduler.nextDelay());
        scheduler.runNext();
        assertEquals(1, resolutions.get());
        assertEquals(1, log.pendingCount());
        assertEquals(RETRY_MILLIS, scheduler.nextDelay());
        log.write("boot-2");
        log.write("boot-3");
        log.write("boot-4");
        log.kick();
        assertEquals(1, resolutions.get());
        assertEquals(1, scheduler.size());
        assertEquals(RETRY_MILLIS, scheduler.nextDelay());
        assertEquals(3, log.pendingCount());
        assertEquals("boot-3\nboot-4\n", log.recent());
        scheduler.runNext();
        assertEquals(2, resolutions.get());
        assertEquals(1, scheduler.size());
        assertEquals(RETRY_MILLIS, scheduler.nextDelay());
        assertEquals("boot-2\nboot-3\nboot-4\n", log.read());
        assertEquals(3, log.pendingCount());
        RecordingSink sink = new RecordingSink();
        ready.set(sink);
        scheduler.runNext();
        assertEquals("boot-2\nboot-3\nboot-4\n", sink.read());
        assertEquals(0, log.pendingCount());
        assertEquals(0, scheduler.size());
        assertTrue(log.fileOk());
        assertEquals("", log.lastError());
    }

    @Test(timeout = 10000)
    public void overflowDuringAppendKeepsNewestPendingEntriesInOrder() throws Exception {
        AppendGate sink = new AppendGate();
        AsyncEventLog log = threaded(() -> sink, 2, 3);
        try {
            log.write("in-flight");
            await(sink.entered);
            callers.submit(() -> {
                log.write("evicted");
                log.write("new-1");
                log.write("new-2");
                log.write("new-3");
            }).get(1, TimeUnit.SECONDS);
            assertEquals(3, log.pendingCount());
            assertEquals("new-2\nnew-3\n", log.recent());
        } finally {
            sink.release.countDown();
        }
        assertTrue(log.awaitIdle(2000L));
        assertEquals("in-flight\nnew-1\nnew-2\nnew-3\n", log.read());
    }

    @Test(timeout = 10000)
    public void readOrdersInFlightAndQueuedLinesExactlyOnce() throws Exception {
        AppendGate sink = new AppendGate();
        AsyncEventLog log = threaded(() -> sink, 500, 2000);
        Future<String> snapshot;
        try {
            log.write("in-flight");
            await(sink.entered);
            CountDownLatch reading = new CountDownLatch(1);
            snapshot = callers.submit(() -> {
                reading.countDown();
                return log.read();
            });
            await(reading);
            assertWaiting(snapshot);
            callers.submit(() -> log.write("queued")).get(1, TimeUnit.SECONDS);
        } finally {
            sink.release.countDown();
        }
        assertEquals("in-flight\nqueued\n", snapshot.get(2, TimeUnit.SECONDS));
        assertTrue(log.awaitIdle(2000L));
        assertEquals("in-flight\nqueued\n", log.read());
    }

    @Test(timeout = 10000)
    public void producerReturnsDuringReadAndSnapshotIncludesQueuedTail() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        RecordingSink sink = new RecordingSink() {
            @Override
            public String read() throws Exception {
                String snapshot = super.read();
                entered.countDown();
                await(release);
                return snapshot;
            }
        };
        ManualScheduler scheduler = new ManualScheduler();
        AsyncEventLog log = new AsyncEventLog(() -> sink, scheduler, 500, 2000, RETRY_MILLIS);
        log.write("persisted");
        scheduler.runNext();
        Future<String> snapshot = callers.submit(log::read);
        try {
            await(entered);
            callers.submit(() -> log.write("queued")).get(1, TimeUnit.SECONDS);
            assertEquals(1, log.pendingCount());
        } finally {
            release.countDown();
        }
        assertEquals("persisted\nqueued\n", snapshot.get(2, TimeUnit.SECONDS));
        scheduler.runNext();
        assertEquals("persisted\nqueued\n", log.read());
    }

    @Test(timeout = 10000)
    public void clearOrdersAfterInFlightAppendAndRemovesEarlierEntries() throws Exception {
        AppendGate sink = new AppendGate();
        AsyncEventLog log = threaded(() -> sink, 500, 2000);
        Future<?> cleared;
        try {
            log.write("in-flight");
            await(sink.entered);
            CountDownLatch clearing = new CountDownLatch(1);
            cleared = callers.submit(() -> {
                clearing.countDown();
                log.clear();
            });
            await(clearing);
            assertWaiting(cleared);
            callers.submit(() -> log.write("before-clear")).get(1, TimeUnit.SECONDS);
        } finally {
            sink.release.countDown();
        }
        cleared.get(2, TimeUnit.SECONDS);
        assertTrue(log.awaitIdle(2000L));
        assertEquals("", log.read());
        assertEquals("", log.recent());
        log.write("after-clear");
        assertTrue(log.awaitIdle(2000L));
        assertEquals("after-clear\n", log.read());
    }

    @Test(timeout = 10000)
    public void producerDuringDiskClearSurvivesTheClearBoundary() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        RecordingSink sink = new RecordingSink() {
            @Override
            public void clear() throws Exception {
                entered.countDown();
                await(release);
                super.clear();
            }
        };
        AsyncEventLog log = threaded(() -> sink, 500, 2000);
        log.write("before");
        assertTrue(log.awaitIdle(2000L));
        Future<?> cleared = callers.submit(log::clear);
        try {
            await(entered);
            callers.submit(() -> log.write("after")).get(1, TimeUnit.SECONDS);
            assertEquals("after\n", log.recent());
        } finally {
            release.countDown();
        }
        cleared.get(2, TimeUnit.SECONDS);
        assertTrue(log.awaitIdle(2000L));
        assertEquals("after\n", log.read());
    }

    @Test
    public void delayedContextClearPrecedesReadAndLaterAppends() throws Exception {
        RecordingSink sink = new RecordingSink();
        sink.append("old-file");
        AtomicReference<AsyncEventLog.Sink> ready = new AtomicReference<>();
        ManualScheduler scheduler = new ManualScheduler();
        AsyncEventLog log = new AsyncEventLog(ready::get, scheduler, 500, 2000, RETRY_MILLIS);
        log.write("old-pending");
        scheduler.runNext();
        log.clear();
        assertEquals(0, log.pendingCount());
        assertEquals("", log.recent());
        log.write("after-clear");
        assertEquals("after-clear\n", log.read());
        ready.set(sink);
        assertEquals("after-clear\n", log.read());
        assertEquals("", sink.read());
        assertEquals(1, sink.clears);
        scheduler.runNext();
        assertEquals("after-clear\n", sink.read());
        assertEquals("after-clear\n", log.read());
        assertEquals(0, scheduler.size());
    }

    @Test
    public void delayedContextClearIsRetriedWithAnEmptyQueue() throws Exception {
        RecordingSink sink = new RecordingSink();
        sink.append("old-file");
        AtomicReference<AsyncEventLog.Sink> ready = new AtomicReference<>();
        ManualScheduler scheduler = new ManualScheduler();
        AsyncEventLog log = new AsyncEventLog(ready::get, scheduler, 500, 2000, RETRY_MILLIS);
        log.clear();
        assertEquals(1, scheduler.size());
        assertEquals(RETRY_MILLIS, scheduler.nextDelay());
        ready.set(sink);
        scheduler.runNext();
        assertEquals("", sink.read());
        assertEquals(1, sink.clears);
        assertEquals(0, scheduler.size());
    }

    @Test
    public void failedAppendRetainsEntryAndRetriesAtTheConfiguredRate() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        RecordingSink sink = new RecordingSink() {
            @Override
            public void append(String line) throws Exception {
                if (attempts.getAndIncrement() == 0) {
                    throw new IOException("temporary sink failure");
                }
                super.append(line);
            }
        };
        ManualScheduler scheduler = new ManualScheduler();
        AsyncEventLog log = new AsyncEventLog(() -> sink, scheduler, 500, 2000, RETRY_MILLIS);
        log.write("retry-me");
        scheduler.runNext();
        assertEquals(1, log.pendingCount());
        assertFalse(log.fileOk());
        assertTrue(log.lastError().contains("temporary sink failure"));
        assertEquals(RETRY_MILLIS, scheduler.nextDelay());
        assertEquals("retry-me\n", log.read());
        scheduler.runNext();
        assertEquals("retry-me\n", sink.read());
        assertEquals(0, log.pendingCount());
        assertEquals(0, scheduler.size());
        assertTrue(log.fileOk());
        assertEquals("", log.lastError());
    }

    @Test
    public void failedClearIsRetriedBeforeAppendingLaterEntries() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        RecordingSink sink = new RecordingSink() {
            @Override
            public void clear() throws Exception {
                if (attempts.getAndIncrement() == 0) {
                    throw new IOException("temporary clear failure");
                }
                super.clear();
            }
        };
        sink.append("old-file");
        ManualScheduler scheduler = new ManualScheduler();
        AsyncEventLog log = new AsyncEventLog(() -> sink, scheduler, 500, 2000, RETRY_MILLIS);
        log.clear();
        log.write("after-clear");
        assertEquals(RETRY_MILLIS, scheduler.nextDelay());
        scheduler.runNext();
        assertEquals("after-clear\n", sink.read());
        assertEquals(1, sink.clears);
        assertEquals(0, scheduler.size());
    }

    @Test(timeout = 10000)
    public void concurrentBatchesReachIdleAndPersistEachAcceptedLineOnce() throws Exception {
        RecordingSink sink = new RecordingSink();
        AsyncEventLog log = threaded(() -> sink, 500, 2000);
        for (int round = 0; round < 25; round++) {
            List<Future<?>> producers = new ArrayList<>();
            for (int producer = 0; producer < 4; producer++) {
                String prefix = round + ":" + producer + ":";
                producers.add(callers.submit(() -> {
                    for (int line = 0; line < 20; line++) {
                        log.write(prefix + line);
                    }
                }));
            }
            for (Future<?> producer : producers) {
                producer.get(2, TimeUnit.SECONDS);
            }
            assertTrue(log.awaitIdle(2000L));
        }
        String[] lines = log.read().split("\n");
        assertEquals(2000, lines.length);
        assertEquals(2000, new HashSet<>(java.util.Arrays.asList(lines)).size());
        assertEquals(0, log.pendingCount());
        assertEquals(500, log.recent().split("\n").length);
    }

    private AsyncEventLog threaded(AsyncEventLog.SinkProvider provider,
            int memoryLimit, int pendingLimit) {
        return new AsyncEventLog(provider,
                (task, delay) -> io.schedule(task, delay, TimeUnit.MILLISECONDS),
                memoryLimit, pendingLimit, RETRY_MILLIS);
    }

    private static void await(CountDownLatch latch) throws InterruptedException {
        assertTrue("Gate reached within the test deadline", latch.await(2, TimeUnit.SECONDS));
    }

    private static void assertWaiting(Future<?> operation) throws Exception {
        try {
            operation.get(100, TimeUnit.MILLISECONDS);
            fail("Operation waits for the in-flight append");
        } catch (TimeoutException expected) {
            // The gate controls completion; the timeout bounds the assertion wait.
        }
    }

    private static class RecordingSink implements AsyncEventLog.Sink {
        private final StringBuilder text = new StringBuilder();
        int clears;

        @Override
        public synchronized void append(String line) throws Exception {
            text.append(line).append('\n');
        }

        @Override
        public synchronized String read() throws Exception {
            return text.toString();
        }

        @Override
        public synchronized void clear() throws Exception {
            clears++;
            text.setLength(0);
        }
    }

    private static final class AppendGate extends RecordingSink {
        final CountDownLatch entered = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);

        @Override
        public void append(String line) throws Exception {
            entered.countDown();
            await(release);
            super.append(line);
        }
    }

    private static final class ManualScheduler implements AsyncEventLog.Scheduler {
        private final Deque<Runnable> tasks = new ArrayDeque<>();
        private final Deque<Long> delays = new ArrayDeque<>();

        @Override
        public void schedule(Runnable task, long delayMillis) {
            tasks.addLast(task);
            delays.addLast(delayMillis);
        }

        int size() {
            return tasks.size();
        }

        long nextDelay() {
            return delays.getFirst();
        }

        void runNext() {
            Runnable task = tasks.removeFirst();
            delays.removeFirst();
            task.run();
        }
    }
}
