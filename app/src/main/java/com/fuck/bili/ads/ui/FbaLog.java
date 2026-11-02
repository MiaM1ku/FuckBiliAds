package com.fuck.bili.ads.ui;

import android.content.Context;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Event log for the whole module.
 *
 * <p>Two sinks are kept on purpose: a small in-memory ring (always available,
 * survives a broken/unavailable app context and is what "本次运行" shows) and a
 * file under the host's private data dir (persistent, exported from the
 * settings page). Both are mirrored to logcat with tag {@code FuckBiliAds}.
 */
public final class FbaLog {

    private static final String TAG = FbaRuntime.TAG;
    private static final String DIR = "fuckbiliads";
    private static final String FILE = "events.log";
    private static final String ROTATED = "events.log.1";

    /**
     * Single bounded file: once full, replace its beginning with the newest tail.
     * No rotated file is used, so historical entries cannot exceed this budget.
     */
    static final long MAX_BYTES = 100 * 1024L;
    /** Trims back to this much below {@link #MAX_BYTES} so one rewrite covers
     * many appends instead of re-trimming on every line. */
    static final long TRIM_TARGET_BYTES = MAX_BYTES * 4 / 5;
    private static final int MAX_READ_BYTES = 256 * 1024;
    private static final int MEMORY_LINES = 500;
    /** Maximum lines kept in the pending queue before the oldest are dropped. */
    private static final int PENDING_LIMIT = 2000;

    private static final Charset UTF8 = Charset.forName("UTF-8");

    /** One retry covers the entire bounded queue while Application is starting. */
    private static final long RETRY_MILLIS = 500L;
    private static final ScheduledExecutorService IO =
            Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "FbaLogIO");
                thread.setDaemon(true);
                return thread;
            });
    private static final AsyncEventLog EVENTS = new AsyncEventLog(() -> {
        Context ctx = FbaRuntime.context();
        return ctx == null ? null : new FileSink(ctx);
    }, (task, delay) -> IO.schedule(task, delay, TimeUnit.MILLISECONDS),
            MEMORY_LINES, PENDING_LIMIT, RETRY_MILLIS);

    private FbaLog() {
    }

    public static void init(Context ctx) {
        FbaRuntime.init(ctx);
        EVENTS.kick();
    }

    public static void d(String message) {
        write("D", message);
    }

    public static void i(String message) {
        write("I", message);
    }

    public static void w(String message) {
        write("W", message);
    }

    public static void e(String message, Throwable t) {
        write("E", t == null ? message : message + " :: " + t);
    }

    /**
     * Logs one line. File I/O is always off the calling thread: hook callbacks
     * and the story scanner run on the UI thread, where a trim + fsync per line
     * would drop frames once the file is full.
     */
    public static void write(String level, String message) {
        String line = stamp() + " [" + level + "] " + message;
        Log.d(TAG, message);
        EVENTS.write(line);
    }

    /** File operations are coordinated by AsyncEventLog, separately from producers. */
    static final class FileSink implements AsyncEventLog.Sink {
        private final File dir;
        private final File file;

        FileSink(Context ctx) {
            this(new File(ctx.getFilesDir(), DIR));
        }

        FileSink(File dir) {
            this.dir = dir;
            file = new File(dir, FILE);
        }

        @Override
        public void append(String line) throws Exception {
            if (!dir.exists() && !dir.mkdirs()) {
                throw new java.io.IOException("mkdir failed: " + dir);
            }
            try (FileOutputStream out = new FileOutputStream(file, true)) {
                out.write((line + "\n").getBytes(UTF8));
            }
            trimToBudget(file, MAX_BYTES, TRIM_TARGET_BYTES);
        }

        @Override
        public String read() {
            return readFile(file, true);
        }

        @Override
        public void clear() throws Exception {
            delete(file);
            delete(new File(dir, ROTATED));
        }

        private static void delete(File target) throws java.io.IOException {
            if (target.exists() && !target.delete()) {
                throw new java.io.IOException("log clear failed: " + target);
            }
        }
    }

    /** Rewrites one file, retaining only the newest bytes within the byte budget. */
    static void trimToBudget(File file, long maxBytes) throws Exception {
        trimToBudget(file, maxBytes, TRIM_TARGET_BYTES);
    }

    /** Rewrites one file so it ends at or below {@code targetBytes}. */
    static void trimToBudget(File file, long maxBytes, long targetBytes) throws Exception {
        if (file == null || !file.exists() || file.length() <= maxBytes) {
            return;
        }
        long target = Math.max(1L, Math.min(targetBytes, maxBytes));
        FileInputStream input = null;
        FileOutputStream output = null;
        File tmp = new File(file.getParentFile(), file.getName() + ".tmp");
        try {
            input = new FileInputStream(file);
            long skip = file.length() - target;
            long remaining = skip;
            while (remaining > 0) {
                long stepped = input.skip(remaining);
                if (stepped <= 0) {
                    if (input.read() < 0) {
                        break;
                    }
                    stepped = 1;
                }
                remaining -= stepped;
            }
            output = new FileOutputStream(tmp, false);
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) > 0) {
                output.write(buffer, 0, read);
            }
            output.getFD().sync();
            close(input);
            input = null;
            close(output);
            output = null;
            if (!tmp.renameTo(file)) {
                //noinspection ResultOfMethodCallIgnored
                file.delete();
                if (!tmp.renameTo(file)) {
                    throw new java.io.IOException("log trim rename failed");
                }
            }
        } finally {
            close(input);
            close(output);
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
        }
    }

    /** Persistent log file (tail-truncated), newest last. */
    public static String read() {
        String all = EVENTS.read();
        if (all.length() > MAX_READ_BYTES) {
            return all.substring(all.length() - MAX_READ_BYTES);
        }
        return all;
    }

    /** Test hook: waits for completed writes, including the in-flight entry. */
    static void flushForTest() {
        try {
            if (!EVENTS.awaitIdle(5000L)) {
                throw new IllegalStateException("log flush timed out");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Test hook: number of lines awaiting successful append. */
    static int pendingCountForTest() {
        return EVENTS.pendingCount();
    }

    /** Bounded recent history for the current process. */
    public static String recent() {
        return EVENTS.recent();
    }

    public static boolean fileOk() {
        return EVENTS.fileOk();
    }

    public static String lastError() {
        return EVENTS.lastError();
    }

    public static void clear() {
        EVENTS.clear();
    }

    public static File file() {
        Context ctx = FbaRuntime.context();
        if (ctx == null) {
            return null;
        }
        return new File(new File(ctx.getFilesDir(), DIR), FILE);
    }

    /** Timestamped file name used for the "save to Download" action. */
    public static String exportFileName() {
        return "FuckBiliAds-" + new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date()) + ".log";
    }

    private static String readFile(File file, boolean tail) {
        if (file == null || !file.exists()) {
            return "";
        }
        FileInputStream in = null;
        try {
            in = new FileInputStream(file);
            long length = file.length();
            if (tail && length > MAX_READ_BYTES) {
                //noinspection ResultOfMethodCallIgnored
                in.skip(length - MAX_READ_BYTES);
            }
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int read;
            while ((read = in.read(chunk)) > 0) {
                buffer.write(chunk, 0, read);
            }
            return new String(buffer.toByteArray(), UTF8);
        } catch (Throwable t) {
            return "";
        } finally {
            close(in);
        }
    }

    private static String stamp() {
        return new SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US).format(new Date());
    }

    private static void close(java.io.Closeable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (Throwable ignore) {
            // ignore
        }
    }
}
