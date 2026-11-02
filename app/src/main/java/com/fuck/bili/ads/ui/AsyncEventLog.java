package com.fuck.bili.ads.ui;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.TimeUnit;

/** Bounded event queues with independent producer and file coordination. */
final class AsyncEventLog {

    interface Sink {
        void append(String line) throws Exception;
        String read() throws Exception;
        void clear() throws Exception;
    }

    interface SinkProvider {
        /** Returns the persistent sink once the application context is ready. */
        Sink get() throws Exception;
    }

    interface Scheduler {
        void schedule(Runnable task, long delayMillis);
    }

    private final Object stateLock = new Object();
    /** File operations acquire this lock before briefly inspecting queue state. */
    private final Object fileLock = new Object();
    private final Deque<String> memory = new ArrayDeque<>();
    private final Deque<Entry> pending = new ArrayDeque<>();
    private final SinkProvider provider;
    private final Scheduler scheduler;
    private final int memoryLimit;
    private final int pendingLimit;
    private final long retryMillis;
    /** Covers both a scheduled retry and a running drain. Guarded by stateLock. */
    private boolean scheduled;
    private boolean clearPending;
    private volatile boolean fileOk;
    private volatile String lastError = "";

    AsyncEventLog(SinkProvider provider, Scheduler scheduler, int memoryLimit,
            int pendingLimit, long retryMillis) {
        this.provider = provider;
        this.scheduler = scheduler;
        this.memoryLimit = memoryLimit;
        this.pendingLimit = pendingLimit;
        this.retryMillis = retryMillis;
    }

    /** Producers hold only the bounded queue lock, including during a slow sink. */
    void write(String line) {
        synchronized (stateLock) {
            memory.addLast(line);
            while (memory.size() > memoryLimit) {
                memory.removeFirst();
            }
            pending.addLast(new Entry(line));
            while (pending.size() > pendingLimit) {
                pending.removeFirst();
            }
            scheduleLocked(0L);
        }
    }

    void kick() {
        synchronized (stateLock) {
            scheduleLocked(0L);
        }
    }

    /** The existing retry also covers new writes while context resolution waits. */
    private void scheduleLocked(long delayMillis) {
        if (!scheduled && hasWorkLocked()) {
            scheduled = true;
            scheduler.schedule(this::drain, delayMillis);
        }
    }

    private boolean hasWorkLocked() {
        return clearPending || !pending.isEmpty();
    }

    private void drain() {
        boolean retry = false;
        try {
            while (true) {
                synchronized (fileLock) {
                    synchronized (stateLock) {
                        if (!hasWorkLocked()) {
                            return;
                        }
                    }
                    Sink sink = provider.get();
                    if (sink == null) {
                        unavailable();
                        retry = true;
                        return;
                    }
                    applyClear(sink);
                    Entry entry;
                    synchronized (stateLock) {
                        entry = pending.peekFirst();
                    }
                    if (entry == null) {
                        return;
                    }
                    // Keep the entry pending until append completes, including in-flight reads.
                    sink.append(entry.line);
                    synchronized (stateLock) {
                        // Overflow may have already evicted this in-flight entry.
                        if (pending.peekFirst() == entry) {
                            pending.removeFirst();
                        }
                    }
                    fileOk = true;
                    lastError = "";
                }
            }
        } catch (Throwable e) {
            failed(e);
            retry = true;
        } finally {
            synchronized (stateLock) {
                scheduled = false;
                scheduleLocked(retry ? retryMillis : 0L);
                stateLock.notifyAll();
            }
        }
    }

    /** A snapshot contains each persisted or queued line once, newest last. */
    String read() {
        synchronized (fileLock) {
            String persisted = "";
            try {
                Sink sink = provider.get();
                if (sink != null) {
                    applyClear(sink);
                    persisted = sink.read();
                } else {
                    unavailable();
                }
            } catch (Throwable e) {
                failed(e);
            }
            Entry[] snapshot;
            synchronized (stateLock) {
                snapshot = pending.toArray(new Entry[0]);
            }
            StringBuilder text = new StringBuilder(persisted);
            for (Entry entry : snapshot) {
                text.append(entry.line).append('\n');
            }
            return text.toString();
        }
    }

    String recent() {
        String[] snapshot;
        synchronized (stateLock) {
            snapshot = memory.toArray(new String[0]);
        }
        StringBuilder text = new StringBuilder();
        for (String line : snapshot) {
            text.append(line).append('\n');
        }
        return text.toString();
    }

    /** Clear linearizes after in-flight append and before later queued appends. */
    void clear() {
        synchronized (fileLock) {
            synchronized (stateLock) {
                memory.clear();
                pending.clear();
                clearPending = true;
            }
            try {
                Sink sink = provider.get();
                if (sink != null) {
                    applyClear(sink);
                } else {
                    unavailable();
                }
            } catch (Throwable e) {
                failed(e);
            }
            synchronized (stateLock) {
                scheduleLocked(retryMillis);
            }
        }
    }

    /** The file lock orders a deferred clear ahead of both reads and appends. */
    private void applyClear(Sink sink) throws Exception {
        synchronized (stateLock) {
            if (!clearPending) {
                return;
            }
        }
        sink.clear();
        synchronized (stateLock) {
            clearPending = false;
        }
    }

    private void unavailable() {
        fileOk = false;
        lastError = "no application context";
    }

    private void failed(Throwable e) {
        fileOk = false;
        lastError = String.valueOf(e);
    }

    boolean fileOk() {
        return fileOk;
    }

    String lastError() {
        return lastError;
    }

    int pendingCount() {
        synchronized (stateLock) {
            return pending.size();
        }
    }

    /** Waits for append completion as well as queue removal. */
    boolean awaitIdle(long timeoutMillis) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        synchronized (stateLock) {
            while (scheduled || hasWorkLocked()) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) {
                    return false;
                }
                TimeUnit.NANOSECONDS.timedWait(stateLock, remaining);
            }
            return true;
        }
    }

    private static final class Entry {
        final String line;

        Entry(String line) {
            this.line = line;
        }
    }
}
