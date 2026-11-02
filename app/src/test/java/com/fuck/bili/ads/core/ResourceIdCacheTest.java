package com.fuck.bili.ads.core;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

public class ResourceIdCacheTest {

    @Test
    public void repeatedViewBindingLookupsResolveTheIdOnce() {
        ResourceIdCache cache = new ResourceIdCache();
        Object resources = new Object();
        AtomicInteger calls = new AtomicInteger();
        for (int i = 0; i < 10000; i++) {
            assertEquals(123, cache.get(resources, () -> {
                calls.incrementAndGet();
                return 123;
            }));
        }
        assertEquals(1, calls.get());
    }

    @Test
    public void missingIdIsCached() {
        ResourceIdCache cache = new ResourceIdCache();
        Object resources = new Object();
        AtomicInteger calls = new AtomicInteger();
        for (int i = 0; i < 10000; i++) {
            assertEquals(0, cache.get(resources, () -> {
                calls.incrementAndGet();
                return 0;
            }));
        }
        assertEquals(1, calls.get());
    }

    @Test
    public void resourceEnvironmentsKeepIndependentIdsAndSurviveLastEntryReplacement() {
        ResourceIdCache cache = new ResourceIdCache();
        Object phone = new Object();
        Object hd = new Object();
        assertEquals(10, cache.get(phone, () -> 10));
        assertEquals(20, cache.get(hd, () -> 20));
        assertEquals(10, cache.get(phone, () -> 30));
        assertEquals(20, cache.get(hd, () -> 40));
    }

    @Test
    public void absentResourcesPreserveTheLookupForAReadyContext() {
        ResourceIdCache cache = new ResourceIdCache();
        AtomicInteger calls = new AtomicInteger();
        assertEquals(0, cache.get(null, calls::incrementAndGet));
        assertEquals(0, calls.get());
        assertEquals(1, cache.get(new Object(), calls::incrementAndGet));
    }
}
