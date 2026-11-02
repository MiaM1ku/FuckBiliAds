package com.fuck.bili.ads.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class FbaLogTest {

    @Test
    public void budgetIsOneHundredKilobytes() {
        assertEquals(100 * 1024L, FbaLog.MAX_BYTES);
    }

    @Test
    public void trimTargetLeavesHeadroomForLaterAppends() {
        assertTrue(FbaLog.TRIM_TARGET_BYTES < FbaLog.MAX_BYTES);
        assertTrue(FbaLog.TRIM_TARGET_BYTES >= FbaLog.MAX_BYTES / 2);
    }

    @Test
    public void trimmingOverwritesOldDataAndKeepsNewestTail() throws Exception {
        File file = File.createTempFile("fba-log", ".txt");
        try {
            byte[] bytes = new byte[5000];
            for (int i = 0; i < bytes.length; i++) {
                bytes[i] = (byte) ('a' + (i % 26));
            }
            try (FileOutputStream output = new FileOutputStream(file)) {
                output.write(bytes);
            }
            FbaLog.trimToBudget(file, 1024);
            assertEquals(1024, file.length());
            String text = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            String original = new String(bytes, StandardCharsets.UTF_8);
            assertEquals(original.substring(original.length() - 1024), text);
        } finally {
            assertTrue(!file.exists() || file.delete());
        }
    }

    @Test
    public void trimToExplicitTargetKeepsOnlyTargetTail() throws Exception {
        File file = File.createTempFile("fba-log-target", ".txt");
        try {
            byte[] bytes = new byte[5000];
            for (int i = 0; i < bytes.length; i++) {
                bytes[i] = (byte) ('a' + (i % 26));
            }
            Files.write(file.toPath(), bytes);
            FbaLog.trimToBudget(file, 1024, 800);
            assertEquals(800, file.length());
            String text = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            String original = new String(bytes, StandardCharsets.UTF_8);
            assertEquals(original.substring(original.length() - 800), text);
        } finally {
            assertTrue(!file.exists() || file.delete());
        }
    }

    @Test
    public void appendingAfterTrimDoesNotImmediatelyExceedBudget() throws Exception {
        File file = File.createTempFile("fba-log-append", ".txt");
        try {
            byte[] bytes = new byte[5000];
            for (int i = 0; i < bytes.length; i++) {
                bytes[i] = (byte) ('a' + (i % 26));
            }
            Files.write(file.toPath(), bytes);
            FbaLog.trimToBudget(file, 1024, 800);
            try (FileOutputStream output = new FileOutputStream(file, true)) {
                output.write("a new line that is not very long\n".getBytes(StandardCharsets.UTF_8));
            }
            assertTrue(file.length() <= 1024);
            // A second trim on the same file must be a no-op: we did not cross the budget.
            long before = file.lastModified();
            FbaLog.trimToBudget(file, 1024, 800);
            assertEquals(before, file.lastModified());
        } finally {
            assertTrue(!file.exists() || file.delete());
        }
    }

    @Test
    public void fileSinkEnforcesBudgetAfterAppendAndClearsCurrentAndLegacyFiles() throws Exception {
        File dir = Files.createTempDirectory("fba-log-sink").toFile();
        File file = new File(dir, "events.log");
        File rotated = new File(dir, "events.log.1");
        try {
            FbaLog.FileSink sink = new FbaLog.FileSink(dir);
            sink.append("x".repeat((int) FbaLog.MAX_BYTES + 100));
            assertEquals(FbaLog.TRIM_TARGET_BYTES, file.length());
            sink.append("newest");
            assertTrue(file.length() <= FbaLog.MAX_BYTES);
            assertTrue(sink.read().endsWith("newest\n"));
            Files.write(rotated.toPath(), "legacy".getBytes(StandardCharsets.UTF_8));
            sink.clear();
            assertFalse(file.exists());
            assertFalse(rotated.exists());
            assertEquals("", sink.read());
        } finally {
            Files.deleteIfExists(file.toPath());
            Files.deleteIfExists(rotated.toPath());
            Files.deleteIfExists(new File(dir, "events.log.tmp").toPath());
            Files.deleteIfExists(dir.toPath());
        }
    }

    @Test
    public void smallFileIsNotRewritten() throws Exception {
        File file = File.createTempFile("fba-log-small", ".txt");
        try {
            Files.write(file.toPath(), "newest log".getBytes(StandardCharsets.UTF_8));
            FbaLog.trimToBudget(file, 1024);
            assertEquals("newest log", new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
        } finally {
            assertTrue(!file.exists() || file.delete());
        }
    }
}
