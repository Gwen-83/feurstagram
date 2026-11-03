package com.feurstagram.extension;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class UpdateManagerTest {
    @Test
    public void parseRelease_handlesWrappedReleaseObject() {
        UpdateManager.Release release = UpdateManager.parseRelease(
                "{\"release\":{\"version\":\"1.0.13\",\"apkUrl\":\"https://example.com/feurstagram-release.apk\",\"notes\":\"Version 1.0.13\"}}"
        );

        assertEquals("1.0.13", release.version);
        assertEquals("https://example.com/feurstagram-release.apk", release.apkUrl);
        assertEquals("Version 1.0.13", release.notes);
    }
}
