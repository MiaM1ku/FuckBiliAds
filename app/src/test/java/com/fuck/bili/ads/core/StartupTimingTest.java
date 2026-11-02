package com.fuck.bili.ads.core;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public class StartupTimingTest {

    @Test
    public void separatesSlowWallTimeFromCallingThreadCpuTime() {
        AtomicLong wall = new AtomicLong(1000L);
        AtomicLong cpu = new AtomicLong(10L);
        StartupTiming timing = new StartupTiming(wall::get, cpu::get);
        wall.set(1100L);
        StartupTiming.Span span = timing.begin("installer");
        wall.set(2600L);
        cpu.set(13L);
        String line = timing.end(span);
        assertTrue(line.contains("stage=installer wall=1500ms cpu=3ms t=1600ms"));
        assertTrue(line.contains("thread=" + Thread.currentThread().getName()));
    }

    @Test
    public void asyncMilestoneMeasuresElapsedTimeSinceModuleEntry() {
        AtomicLong wall = new AtomicLong(20L);
        StartupTiming timing = new StartupTiming(wall::get, () -> 0L);
        wall.set(3020L);
        assertTrue(timing.mark("dex-resolved").contains("event=dex-resolved t=3000ms"));
    }

    @Test
    public void cpuComparisonUsesTheOriginalThreadOnly() throws Exception {
        StartupTiming timing = new StartupTiming(() -> 100L, () -> 10L);
        StartupTiming.Span span = timing.begin("asynchronous");
        AtomicReference<String> line = new AtomicReference<>();
        Thread worker = new Thread(() -> line.set(timing.end(span)), "timing-test-worker");
        worker.start();
        worker.join(2000L);
        assertTrue(line.get().contains("cpu=other-thread"));
        assertTrue(line.get().contains("thread=timing-test-worker"));
    }
}
