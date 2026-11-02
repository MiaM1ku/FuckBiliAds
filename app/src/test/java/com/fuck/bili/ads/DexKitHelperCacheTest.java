package com.fuck.bili.ads;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
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

    private static Properties cache() {
        Properties props = new Properties();
        props.setProperty("cache_key", "current");
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
