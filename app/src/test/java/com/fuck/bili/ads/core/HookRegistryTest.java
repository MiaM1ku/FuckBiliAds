package com.fuck.bili.ads.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;

public class HookRegistryTest {

    @Rule
    public TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void snapshotPreservesHandleCountAndWritesUniqueMembers() throws Exception {
        File file = new File(temporary.getRoot(), "audit/current-hooks.txt");
        HookRegistry.writeSnapshot(file, Arrays.asList("Owner#one()", "Owner#one()", "Owner#two()"));
        assertEquals(Arrays.asList("# libxposed API 102 hook registry, handles=3, unique=2",
                "Owner#one()", "Owner#two()"), Files.readAllLines(file.toPath(), StandardCharsets.UTF_8));
        assertFalse(new File(file.getParentFile(), "current-hooks.txt.tmp").exists());
    }

    @Test
    public void laterSnapshotAtomicallyReplacesEarlierContent() throws Exception {
        File file = temporary.newFile("current-hooks.txt");
        HookRegistry.writeSnapshot(file, Collections.singletonList("Owner#old()"));
        HookRegistry.writeSnapshot(file, Collections.singletonList("Owner#current()"));
        assertEquals(Arrays.asList("# libxposed API 102 hook registry, handles=1, unique=1",
                "Owner#current()"), Files.readAllLines(file.toPath(), StandardCharsets.UTF_8));
    }
}
