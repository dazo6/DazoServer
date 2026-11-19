package com.dazo66.android;

import org.junit.Test;
import static org.junit.Assert.*;

public class MediaVersionsTest {
    @Test public void cacheReadsAndAccessTimeChangesReuseTheDecodedImage() {
        MediaVersions versions = new MediaVersions();
        long identity = versions.version("image", true);
        for (int read = 0; read < 100; read++) assertEquals(identity, versions.version("image", false));
        assertNotEquals(identity, versions.version("image", true));
    }
    @Test public void clearingFilesAlsoInvalidatesTheirDecodedIdentity() {
        MediaVersions versions = new MediaVersions();
        long identity = versions.version("image", false);
        versions.clear();
        assertNotEquals(identity, versions.version("image", false));
    }
    @Test public void coldEntriesAreBoundedButRecentlyUsedFilesKeepTheirIdentity() {
        MediaVersions versions = new MediaVersions();
        long recent = versions.version("recent", false), cold = versions.version("cold", false);
        for (int i = 0; i < 300; i++) { versions.version("file" + i, false); versions.version("recent", false); }
        assertEquals(recent, versions.version("recent", false));
        assertNotEquals(cold, versions.version("cold", false));
    }
}
