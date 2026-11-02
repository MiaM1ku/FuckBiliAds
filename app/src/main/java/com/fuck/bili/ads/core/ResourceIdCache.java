package com.fuck.bili.ads.core;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.IntSupplier;

/** Memoizes stable resource IDs, including missing IDs, per Resources instance. */
public final class ResourceIdCache {

    private final Map<Object, Integer> ids = new WeakHashMap<>();
    private volatile Entry last;

    public int get(Object resources, IntSupplier lookup) {
        if (resources == null) {
            return 0;
        }
        Entry recent = last;
        if (recent != null && recent.resources.get() == resources) {
            return recent.id;
        }
        synchronized (ids) {
            Integer cached = ids.get(resources);
            int id = cached == null ? lookup.getAsInt() : cached;
            if (cached == null) {
                ids.put(resources, id);
            }
            last = new Entry(resources, id);
            return id;
        }
    }

    private static final class Entry {
        final WeakReference<Object> resources;
        final int id;

        Entry(Object resources, int id) {
            this.resources = new WeakReference<>(resources);
            this.id = id;
        }
    }
}
