package com.fuck.bili.ads.core;

import java.util.function.LongSupplier;

/** Separates startup wall time, calling-thread CPU time and asynchronous wait time. */
public final class StartupTiming {

    private final LongSupplier wallClock;
    private final LongSupplier cpuClock;
    private final long started;

    public StartupTiming(LongSupplier wallClock, LongSupplier cpuClock) {
        this.wallClock = wallClock;
        this.cpuClock = cpuClock;
        started = wallClock.getAsLong();
    }

    public Span begin(String name) {
        return new Span(name, wallClock.getAsLong(), cpuClock.getAsLong(),
                Thread.currentThread().getId());
    }

    public String end(Span span) {
        long now = wallClock.getAsLong();
        String cpu = span.threadId == Thread.currentThread().getId()
                ? (cpuClock.getAsLong() - span.cpuStarted) + "ms" : "other-thread";
        return "[PERF] startup stage=" + span.name + " wall=" + (now - span.wallStarted)
                + "ms cpu=" + cpu + " t=" + (now - started)
                + "ms thread=" + Thread.currentThread().getName();
    }

    public String mark(String event) {
        return "[PERF] startup event=" + event + " t=" + (wallClock.getAsLong() - started)
                + "ms thread=" + Thread.currentThread().getName();
    }

    public static final class Span {
        private final String name;
        private final long wallStarted;
        private final long cpuStarted;
        private final long threadId;

        private Span(String name, long wallStarted, long cpuStarted, long threadId) {
            this.name = name;
            this.wallStarted = wallStarted;
            this.cpuStarted = cpuStarted;
            this.threadId = threadId;
        }
    }
}
