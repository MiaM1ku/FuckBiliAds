package com.fuck.bili.ads;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class DanmuSenderLookupTest {

    @Test
    public void releaseStartupPreservesTheHostsWebViewInitializationTiming() {
        assertFalse(DanmuSenderLookup.shouldEnableSpaceProbe(false, false));
        assertFalse(DanmuSenderLookup.shouldEnableSpaceProbe(false, true));
    }

    @Test
    public void debugStartupRequiresExplicitWebViewDiagnosticOptIn() {
        assertFalse(DanmuSenderLookup.shouldEnableSpaceProbe(true, false));
        assertTrue(DanmuSenderLookup.shouldEnableSpaceProbe(true, true));
    }
}
