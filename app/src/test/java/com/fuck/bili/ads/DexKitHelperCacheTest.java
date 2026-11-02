package com.fuck.bili.ads;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.Properties;

public class DexKitHelperCacheTest {

    @Rule
    public TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void mobileCacheHitLoadsResolvedNamesThroughCacheOnlyEntryPoint() throws Exception {
        Properties props = cache();
        props.setProperty("CHRONOS_RPC_CLASS_NAME", "resolved.Chronos");
        props.setProperty("PEGASUS_MODEL_CLASS_NAME", "resolved.Pegasus");
        DexKitHelper helper = new DexKitHelper();

        assertTrue(helper.loadCache(store(props), "current", false));
        assertEquals("resolved.Chronos", helper.CHRONOS_RPC_CLASS_NAME);
        assertEquals("resolved.Pegasus", helper.PEGASUS_MODEL_CLASS_NAME);
    }

    @Test
    public void legacyChronosPropertyStillLoads() throws Exception {
        Properties props = cache();
        props.setProperty("chronosRpcClassName", "legacy.Chronos");
        DexKitHelper helper = new DexKitHelper();

        assertTrue(helper.loadCache(store(props), "current", false));
        assertEquals("legacy.Chronos", helper.CHRONOS_RPC_CLASS_NAME);
    }

    @Test
    public void currentChronosPropertyTakesPrecedence() throws Exception {
        Properties props = cache();
        props.setProperty("chronosRpcClassName", "legacy.Chronos");
        props.setProperty("CHRONOS_RPC_CLASS_NAME", "current.Chronos");
        DexKitHelper helper = new DexKitHelper();

        assertTrue(helper.loadCache(store(props), "current", false));
        assertEquals("current.Chronos", helper.CHRONOS_RPC_CLASS_NAME);
    }

    @Test
    public void staleCacheReturnsMissAndPreservesDefaults() throws Exception {
        Properties props = cache();
        props.setProperty("PEGASUS_MODEL_CLASS_NAME", "stale.Pegasus");
        DexKitHelper helper = new DexKitHelper();
        String original = helper.PEGASUS_MODEL_CLASS_NAME;

        assertFalse(helper.loadCache(store(props), "new-host", false));
        assertEquals(original, helper.PEGASUS_MODEL_CLASS_NAME);
    }

    @Test
    public void missingAndMalformedCachesReturnMiss() throws Exception {
        DexKitHelper helper = new DexKitHelper();
        assertFalse(helper.loadCache(null, "current", false));
        assertFalse(helper.loadCache(new File(temporary.getRoot(), "missing"), "current", false));
        File malformed = temporary.newFile();
        Files.write(malformed.toPath(), "cache_key=\\uZZZZ".getBytes(StandardCharsets.UTF_8));
        assertFalse(helper.loadCache(malformed, "current", false));
    }

    @Test
    public void hdCacheRestoresBothImplementationGroupsAndParser() throws Exception {
        Properties props = cache();
        props.setProperty("hdUnderPlayerClasses", "resolved.UnderOne\nresolved.UnderTwo");
        props.setProperty("hdRelateClasses", "resolved.Relate");
        props.setProperty("PEGASUS_SUS_GSON_PARSER_PARSER_METHOD_NAME", "parse");
        DexKitHelper helper = new DexKitHelper();

        assertTrue(helper.loadCache(store(props), "current", true));
        assertEquals(Arrays.asList("resolved.UnderOne", "resolved.UnderTwo"), helper.hdUnderPlayerClasses);
        assertEquals(Collections.singletonList("resolved.Relate"), helper.hdRelateClasses);
        assertEquals("parse", helper.PEGASUS_SUS_GSON_PARSER_PARSER_METHOD_NAME);
    }

    @Test
    public void oldAndPartialHdCachesRequestBackgroundRefresh() throws Exception {
        Properties props = cache();
        props.setProperty("PEGASUS_SUS_GSON_PARSER_PARSER_METHOD_NAME", "parse");
        DexKitHelper helper = new DexKitHelper();
        assertFalse(helper.loadCache(store(props), "current", true));
        props.setProperty("hdUnderPlayerClasses", "resolved.Under");
        assertFalse(helper.loadCache(store(props), "current", true));
        assertTrue(helper.hdUnderPlayerClasses.isEmpty());
    }

    @Test
    public void hdCacheCanRepresentAbsentInterfaces() throws Exception {
        Properties props = cache();
        props.setProperty("hdUnderPlayerClasses", "");
        props.setProperty("hdRelateClasses", "");
        DexKitHelper helper = new DexKitHelper();

        assertTrue(helper.loadCache(store(props), "current", true));
        assertTrue(helper.hdUnderPlayerClasses.isEmpty());
        assertTrue(helper.hdRelateClasses.isEmpty());
        assertEquals("l", helper.PEGASUS_SUS_GSON_PARSER_PARSER_METHOD_NAME);
    }

    @Test
    public void hdFingerprintTracksModuleAndEveryApkIncludingNestedOriginal() throws Exception {
        File shell = temporary.newFile("shell.apk");
        File original = temporary.newFile("original.apk");
        java.util.List<String> sources = Arrays.asList(shell.getPath(), original.getPath());
        String key = DexKitHelper.hdCacheKey(1, sources);
        assertEquals(key, DexKitHelper.hdCacheKey(1, sources));
        assertNotEquals(key, DexKitHelper.hdCacheKey(2, sources));
        assertNotEquals(key, DexKitHelper.hdCacheKey(1, Collections.singletonList(shell.getPath())));
        Files.write(original.toPath(), new byte[]{1, 2, 3});
        assertNotEquals(key, DexKitHelper.hdCacheKey(1, sources));
        String resized = DexKitHelper.hdCacheKey(1, sources);
        assertTrue(original.setLastModified(original.lastModified() + 2000L));
        assertNotEquals(resized, DexKitHelper.hdCacheKey(1, sources));
    }

    @Test
    public void optionalMissBetweenResolvedResultsStillProducesReusableMobileCache() throws Exception {
        DexKitHelper scanned = new DexKitHelper();
        Properties props;
        try (DexKitHelper.MobileScan scan = scanned.beginMobileScan()) {
            scanned.CHRONOS_RPC_CLASS_NAME = DexKitHelper.firstName(
                    Collections.singletonList("resolved.Chronos"), value -> value);
            scanned.SEARCH_RESULT_ADAPTER_CLASS_NAME = DexKitHelper.firstName(
                    Collections.<String>emptyList(), value -> {
                        throw new AssertionError("Empty matches must produce the absent marker");
                    });
            scanned.VIP_PREDICT_REQUEST_CLASS_NAME = DexKitHelper.firstName(
                    Collections.singletonList("resolved.VipRequest"), value -> value);
            scanned.VIDEO_MENTIONED_COMPONENT_CLASS_NAME = "resolved.VideoMentioned";
            scanned.NETWORK_SHOW_TOAST_SERVICE_METHOD_NAME = DexKitHelper.firstName(
                    Collections.<String>emptyList(), value -> value);
            scanned.REQUEST_INTERCEPTOR = "resolved.Interceptor";
            props = scan.cacheProperties("current");
        }
        assertEquals("", props.getProperty("SEARCH_RESULT_ADAPTER_CLASS_NAME"));
        assertEquals("", props.getProperty("NETWORK_SHOW_TOAST_SERVICE_METHOD_NAME"));
        DexKitHelper reloaded = new DexKitHelper();
        File file = store(props);
        assertTrue(reloaded.loadCache(file, "current", false));
        assertTrue(reloaded.loadCache(file, "current", false));
        assertEquals("resolved.Chronos", reloaded.CHRONOS_RPC_CLASS_NAME);
        assertEquals("", reloaded.SEARCH_RESULT_ADAPTER_CLASS_NAME);
        assertEquals("", reloaded.NETWORK_SHOW_TOAST_SERVICE_METHOD_NAME);
        assertEquals("resolved.VipRequest", reloaded.VIP_PREDICT_REQUEST_CLASS_NAME);
        assertEquals("resolved.VideoMentioned", reloaded.VIDEO_MENTIONED_COMPONENT_CLASS_NAME);
        assertEquals("resolved.Interceptor", reloaded.REQUEST_INTERCEPTOR);
    }

    @Test
    public void allAbsentMobileScanRestoresPreviousNamesAndKeepsCacheMiss() throws Exception {
        DexKitHelper scanned = new DexKitHelper();
        Properties previous = scanned.mobileCacheProperties("old-host");
        File file = store(previous);
        for (int attempt = 0; attempt < 2; attempt++) {
            try (DexKitHelper.MobileScan scan = scanned.beginMobileScan()) {
                Properties candidates = scanned.mobileCacheProperties("current");
                for (String key : candidates.stringPropertyNames()) {
                    if (!key.equals("cache_key") && !key.equals("mobile_schema")) {
                        assertEquals(key, "", candidates.getProperty(key));
                    }
                }
                assertNull(scan.cacheProperties("current"));
            }
            assertEquals(previous, scanned.mobileCacheProperties("old-host"));
            assertFalse(scanned.loadCache(file, "current", false));
        }
    }

    @Test
    public void genericLibraryMatchesKeepPreviousNamesAndRequestRetry() throws Exception {
        DexKitHelper scanned = new DexKitHelper();
        scanned.CHRONOS_RPC_CLASS_NAME = "previous.Chronos";
        Properties previous = scanned.mobileCacheProperties("old-host");
        File file = store(previous);
        try (DexKitHelper.MobileScan scan = scanned.beginMobileScan()) {
            scanned.albumRecycleViewHolderClassName = "library.Adapter";
            scanned.publishArchiveCollectionFieldName = "items";
            scanned.albumSelectPageViewClassName = "library.Binding";
            scanned.RECYCLER_VIEW_FIELD_NAME = "recycler";
            scanned.VIP_PREDICT_REQUEST_CLASS_NAME = "retrofit2.LibraryCallback";
            assertNull(scan.cacheProperties("current"));
        }
        assertEquals(previous, scanned.mobileCacheProperties("old-host"));
        assertFalse(scanned.loadCache(file, "current", false));
    }

    @Test
    public void queryAndNativeFailuresRestoreNamesEvenAfterHostMatch() {
        DexKitHelper scanned = new DexKitHelper();
        Properties previous = scanned.mobileCacheProperties("current");
        for (boolean nativeFailure : new boolean[]{false, true}) {
            try (DexKitHelper.MobileScan scan = scanned.beginMobileScan()) {
                scanned.CHRONOS_RPC_CLASS_NAME = "partial.Chronos";
                if (nativeFailure) {
                    throw new UnsatisfiedLinkError("simulated native failure");
                }
                throw new IllegalStateException("simulated query failure");
            } catch (RuntimeException | LinkageError expected) {
                assertTrue(expected.getMessage().startsWith("simulated"));
            }
            assertEquals(previous, scanned.mobileCacheProperties("current"));
        }
    }

    @Test
    public void ioFailureKeepsVerifiedNamesAndLeavesCacheMissForRetry() throws Exception {
        DexKitHelper scanned = new DexKitHelper();
        File directory = temporary.newFolder();
        try (DexKitHelper.MobileScan scan = scanned.beginMobileScan()) {
            scanned.CHRONOS_RPC_CLASS_NAME = "resolved.Chronos";
            Properties props = scan.cacheProperties("current");
            try (FileOutputStream output = new FileOutputStream(directory)) {
                props.store(output, "simulated failed cache save");
            }
            fail("Opening a directory as a cache file must fail");
        } catch (java.io.IOException expected) {
            assertTrue(directory.isDirectory());
        }
        assertEquals("resolved.Chronos", scanned.CHRONOS_RPC_CLASS_NAME);
        assertEquals("", scanned.SEARCH_RESULT_ADAPTER_CLASS_NAME);
        assertFalse(scanned.loadCache(new File(directory, "missing"), "current", false));
    }

    @Test
    public void emptyClassScopeSkipsQueryAndPopulatedScopeStaysRestricted() {
        assertTrue(DexKitHelper.queryInClasses(Collections.emptyList(), scope -> {
            throw new AssertionError("Empty class scope must remain an absent result");
        }).isEmpty());
        java.util.List<String> classes = Collections.singletonList("resolved.Scope");
        assertEquals(Collections.singletonList("resolved.Method"),
                DexKitHelper.queryInClasses(classes, scope -> {
                    assertEquals(classes, scope);
                    return Collections.singletonList("resolved.Method");
                }));
    }

    @Test
    public void preFixMobileSchemaRequestsRefreshBeforeApplyingCorruptedVideoName() throws Exception {
        Properties props = cache();
        props.setProperty("VIDEO_MENTIONED_COMPONENT_CLASS_NAME", "retrofit2.WrongVipRequest");
        DexKitHelper helper = new DexKitHelper();
        String original = helper.VIDEO_MENTIONED_COMPONENT_CLASS_NAME;

        props.remove("mobile_schema");
        assertFalse(helper.loadCache(store(props), "current", false));
        assertEquals(original, helper.VIDEO_MENTIONED_COMPONENT_CLASS_NAME);
        props.setProperty("mobile_schema", "1");
        assertFalse(helper.loadCache(store(props), "current", false));
        assertEquals(original, helper.VIDEO_MENTIONED_COMPONENT_CLASS_NAME);
    }

    private static Properties cache() {
        Properties props = new Properties();
        props.setProperty("cache_key", "current");
        props.setProperty("mobile_schema", DexKitHelper.MOBILE_CACHE_SCHEMA);
        return props;
    }

    private File store(Properties props) throws Exception {
        File file = temporary.newFile();
        try (FileOutputStream output = new FileOutputStream(file)) {
            props.store(output, "test cache");
        }
        return file;
    }
}
