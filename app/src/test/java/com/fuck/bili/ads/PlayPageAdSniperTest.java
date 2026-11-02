package com.fuck.bili.ads;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;

public class PlayPageAdSniperTest {

    @Rule
    public TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void sourcesReadsInheritedLoaderFieldsAndKeepsNestedOriginalBeforeShell() throws Exception {
        File original = temporary.newFile("original.apk");
        File shell = temporary.newFile("shell.apk");
        ClassLoader loader = new HostLoader(original.getPath());

        assertEquals(Arrays.asList(original.getPath(), shell.getPath()),
                PlayPageAdSniper.sources(loader, shell.getPath()));
        assertEquals(Collections.singletonList(original.getPath()),
                PlayPageAdSniper.sources(loader, original.getPath()));
    }

    @Test
    public void sourcesFallsBackForOpaqueLoader() {
        assertEquals(Collections.singletonList("host.apk"),
                PlayPageAdSniper.sources(new ClassLoader() {}, "host.apk"));
    }

    @Test
    public void resolvesValidImplementationsAfterMissingAndMismatchedCandidates() {
        assertEquals(Collections.singletonList(ValidImplementation.class),
                PlayPageAdSniper.resolveImplementations(getClass().getClassLoader(), Runnable.class,
                        Arrays.asList("missing.Implementation", String.class.getName(),
                                ValidImplementation.class.getName())));
    }

    public static final class ValidImplementation implements Runnable {
        @Override
        public void run() {
        }
    }

    private static class BaseLoader extends ClassLoader {
        private final PathList pathList;

        BaseLoader(String path) {
            pathList = new PathList(path);
        }
    }

    private static final class HostLoader extends BaseLoader {
        HostLoader(String path) {
            super(path);
        }
    }

    private static final class PathList {
        private final Object[] dexElements;

        PathList(String path) {
            dexElements = new Object[]{new Element(path)};
        }
    }

    private static final class Element {
        private final FakeDexFile dexFile;

        Element(String path) {
            dexFile = new FakeDexFile(path);
        }
    }

    public static final class FakeDexFile {
        private final String path;

        FakeDexFile(String path) {
            this.path = path;
        }

        public String getName() {
            return path;
        }
    }

    @Test
    public void recognizesAllUnderPlayerVariants() {
        assertTrue(PlayPageAdSniper.isUnderPlayerFactory("getUpperHDView"));
        assertTrue(PlayPageAdSniper.isUnderPlayerFactory("getUpperNestView"));
        assertTrue(PlayPageAdSniper.isUnderPlayerFactory("getUpperAdView"));
        assertFalse(PlayPageAdSniper.isUnderPlayerFactory("getAdRelateView"));
    }

    @Test
    public void relateFactoryUsesExactPublicDescriptor() throws Exception {
        Method expected = Stub.class.getDeclaredMethod("expected",
                android.view.ViewGroup.class, int.class, boolean.class);
        Method wrongName = Stub.class.getDeclaredMethod("wrongName",
                android.view.ViewGroup.class, int.class, boolean.class);
        Method wrongReturn = Stub.class.getDeclaredMethod("wrongReturn",
                android.view.ViewGroup.class, int.class, boolean.class);
        Method wrongParameter = Stub.class.getDeclaredMethod("wrongParameter",
                android.view.ViewGroup.class, long.class, boolean.class);

        assertTrue(PlayPageAdSniper.hasSignature(expected, Stub.class.getName(),
                "android.view.ViewGroup", "int", "boolean"));
        assertFalse(PlayPageAdSniper.hasSignature(wrongName, Stub.class.getName(),
                "android.view.ViewGroup", "int", "boolean")
                && PlayPageAdSniper.isRelateFactory(wrongName.getName()));
        assertFalse(PlayPageAdSniper.hasSignature(wrongReturn, Stub.class.getName(),
                "android.view.ViewGroup", "int", "boolean"));
        assertFalse(PlayPageAdSniper.hasSignature(wrongParameter, Stub.class.getName(),
                "android.view.ViewGroup", "int", "boolean"));
        assertTrue(PlayPageAdSniper.isRelateFactory("getAdRelateView"));
    }

    @SuppressWarnings("unused")
    private static final class Stub {
        private Stub expected(android.view.ViewGroup parent, int viewType, boolean fresh) {
            return this;
        }

        private Stub wrongName(android.view.ViewGroup parent, int viewType, boolean fresh) {
            return this;
        }

        private String wrongReturn(android.view.ViewGroup parent, int viewType, boolean fresh) {
            return "";
        }

        private Stub wrongParameter(android.view.ViewGroup parent, long viewType, boolean fresh) {
            return this;
        }
    }
}
