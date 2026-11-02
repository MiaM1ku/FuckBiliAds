package com.fuck.bili.ads.core;

import com.fuck.bili.ads.ui.FbaLog;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import io.github.libxposed.api.XposedInterface;

/**
 * Registry of every hook installed by the native libxposed implementation.
 * The dumped member set is the comparison source for migration gate G2.
 */
public final class HookRegistry {

    private static final String TAG = "HookRegistry";

    private static final List<Record> HOOKS = new CopyOnWriteArrayList<>();
    private static final ScheduledExecutorService AUDIT =
            Executors.newSingleThreadScheduledExecutor(action -> {
                Thread thread = new Thread(action, "FbaHookAudit");
                thread.setDaemon(true);
                return thread;
            });

    private HookRegistry() {
    }

    public static XposedInterface.HookHandle add(XposedInterface.HookHandle handle,
                                                 Executable executable, String owner) {
        HOOKS.add(new Record(handle, executable, owner));
        return handle;
    }

    public static int size() {
        return HOOKS.size();
    }

    public static List<Record> snapshot() {
        List<Record> copy = new ArrayList<>(HOOKS);
        Collections.sort(copy, new Comparator<Record>() {
            @Override
            public int compare(Record a, Record b) {
                return a.key.compareTo(b.key);
            }
        });
        return copy;
    }

    /** Stable one-line signature, used for both current and baseline dumps. */
    public static String signature(Executable executable) {
        StringBuilder sb = new StringBuilder();
        if (Modifier.isStatic(executable.getModifiers())) {
            sb.append("static ");
        }
        sb.append(executable.getDeclaringClass().getName());
        sb.append("#");
        if (executable instanceof Method) {
            sb.append(((Method) executable).getName());
        } else {
            sb.append("<init>");
        }
        sb.append('(');
        Class<?>[] types = executable.getParameterTypes();
        for (int i = 0; i < types.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(types[i].getName());
        }
        sb.append(')');
        return sb.toString();
    }

    public static List<String> dump() {
        List<String> lines = new ArrayList<>(HOOKS.size());
        for (Record record : snapshot()) {
            lines.add(record.key);
        }
        return lines;
    }

    /** Unique member set, which is the actual comparison unit for gate G2. */
    public static List<String> dumpUnique() {
        List<String> all = dump();
        List<String> unique = new ArrayList<>(all.size());
        for (String line : all) {
            if (!unique.contains(line)) {
                unique.add(line);
            }
        }
        return unique;
    }

    public static void dumpToLog() {
        FbaLog.i("[HOOK REGISTRY] size=" + HOOKS.size());
        for (String line : dump()) {
            FbaLog.i("[HOOK REGISTRY] " + line);
        }
    }

    /** Captures this startup stage, then formats, logs and writes it on the audit worker. */
    public static void dumpAsyncWhenReady() {
        List<Record> records = new ArrayList<>(HOOKS);
        AUDIT.execute(() -> {
            List<String> lines = new ArrayList<>(records.size());
            for (Record record : records) {
                lines.add(record.key);
            }
            Collections.sort(lines);
            FbaLog.i("[HOOK REGISTRY] size=" + records.size());
            for (String line : lines) {
                FbaLog.i("[HOOK REGISTRY] " + line);
            }
            dumpToFile(lines, 0);
        });
    }

    /** Writes an immutable member snapshot once a host Application is available. */
    public static void dumpToFileWhenReady() {
        List<String> lines = dump();
        AUDIT.execute(() -> dumpToFile(lines, 0));
    }

    private static void dumpToFile(List<String> lines, int attempt) {
        try {
            android.app.Application application = Host.currentApplication();
            if (application == null) {
                if (attempt >= 20) {
                    FbaLog.w("[HOOK REGISTRY] application unavailable after retries; snapshot retained in log");
                    return;
                }
                AUDIT.schedule(() -> dumpToFile(lines, attempt + 1), 500L, TimeUnit.MILLISECONDS);
                return;
            }
            File file = new File(new File(application.getCacheDir(), "fuckbiliads"), "current-hooks.txt");
            writeSnapshot(file, lines);
            FbaLog.i("[HOOK REGISTRY] written " + file.getAbsolutePath()
                    + ", handles=" + lines.size() + ", unique=" + new LinkedHashSet<>(lines).size());
        } catch (Throwable t) {
            FbaLog.e("[HOOK REGISTRY] file dump failed", t);
        }
    }

    static void writeSnapshot(File file, List<String> lines) throws Exception {
        File parent = file.getParentFile();
        if (!parent.isDirectory() && !parent.mkdirs()) {
            throw new java.io.IOException("cannot create registry directory: " + parent);
        }
        LinkedHashSet<String> unique = new LinkedHashSet<>(lines);
        File temporary = new File(parent, file.getName() + ".tmp");
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(temporary, false), StandardCharsets.UTF_8))) {
            writer.write("# libxposed API 102 hook registry, handles=" + lines.size()
                    + ", unique=" + unique.size());
            writer.newLine();
            for (String line : unique) {
                writer.write(line);
                writer.newLine();
            }
        }
        Files.move(temporary.toPath(), file.toPath(),
                StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }

    public static final class Record {
        public final XposedInterface.HookHandle handle;
        public final String key;
        public final String owner;

        Record(XposedInterface.HookHandle handle, Executable executable, String owner) {
            this.handle = handle;
            this.key = signature(executable);
            this.owner = owner;
        }
    }
}
