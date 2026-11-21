package com.dazo66.android;

import org.junit.Test;
import static org.junit.Assert.*;

public class UpdatePolicyTest {
    private final String hash = "a".repeat(64);
    private void valid(String path, long code, long size, String sha) {
        UpdatePolicy.validate("com.dazo66.android", "com.dazo66.android", "1.4.0.21", path, code, size, sha, 26);
    }
    @Test public void acceptsAuthenticatedServerVersionAndRelativePath() {
        valid(UpdatePolicy.apkPath("1.4.0.21"), 100021, 18000000, hash);
    }
    @Test public void rejectsTraversalQueriesAndRemoteOrigins() {
        for (String version : new String[]{"../other", "1.4.0.21?host=evil", "https://evil.test", "1.4.0.21/apk", "", "1.4"})
            assertThrows(IllegalArgumentException.class, () -> UpdatePolicy.apkPath(version));
        for (String path : new String[]{"https://evil.test/api/app-updates/1.4.0.21/apk", "/api/app-updates/1.4.0.22/apk", "//evil.test/apk"})
            assertThrows(IllegalArgumentException.class, () -> valid(path, 100021, 3, hash));
    }
    @Test public void rejectsWrongPackageAndUnboundedOrUnverifiableDownloads() {
        String path = UpdatePolicy.apkPath("1.4.0.21");
        assertThrows(IllegalArgumentException.class, () -> valid(path, 0, 3, hash));
        assertThrows(IllegalArgumentException.class, () -> valid(path, 2100000001L, 3, hash));
        assertThrows(IllegalArgumentException.class, () -> valid(path, 100021, 0, hash));
        assertThrows(IllegalArgumentException.class, () -> valid(path, 100021, UpdatePolicy.MAX_APK_BYTES+1, hash));
        assertThrows(IllegalArgumentException.class, () -> valid(path, 100021, 3, ""));
        assertThrows(IllegalArgumentException.class, () -> UpdatePolicy.validate("other.app", "com.dazo66.android", "1.4.0.21", path, 100021, 3, hash, 26));
    }
    @Test public void acceptsThreeAndFourPartVersionNames() {
        assertEquals("/api/app-updates/2.0.0/apk", UpdatePolicy.apkPath("2.0.0"));
        assertEquals("/api/app-updates/1.4.0.21/apk", UpdatePolicy.apkPath("1.4.0.21"));
    }
}
