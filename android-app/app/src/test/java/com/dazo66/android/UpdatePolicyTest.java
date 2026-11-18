package com.dazo66.android;

import org.junit.Test;
import static org.junit.Assert.*;

public class UpdatePolicyTest {
    private void valid(String url, long code, long size, String hash) {
        UpdatePolicy.validate("com.dazo66.android", "com.dazo66.android", "android-build-6", url, code, size, hash, 26);
    }
    private final String apk = UpdatePolicy.assetUrl("android-build-6", "DazoMobile.apk");
    private final String hash = "a".repeat(64);

    @Test public void acceptsMatchingImmutableRelease() { valid(apk, 100006, 75000000, hash); }
    @Test public void filtersUnrelatedAndMalformedTags() {
        assertEquals(-1, UpdatePolicy.buildNumber("server-v1.0"));
        assertEquals(-1, UpdatePolicy.buildNumber("android-build-6/../../other"));
        assertEquals(6, UpdatePolicy.buildNumber("android-build-6"));
    }
    @Test public void rejectsDifferentHostOrArtifactPath() {
        assertThrows(IllegalArgumentException.class, () -> valid(apk.replace("github.com", "github.com.evil.test"), 100006, 3, hash));
        assertThrows(IllegalArgumentException.class, () -> valid(apk.replace("DazoServer", "AnotherApp"), 100006, 3, hash));
        assertThrows(IllegalArgumentException.class, () -> valid(apk.replace("android-build-6", "android-build-7"), 100006, 3, hash));
    }
    @Test public void rejectsMismatchedVersionAndUnboundedDownloads() {
        assertThrows(IllegalArgumentException.class, () -> valid(apk, 2, 3, hash));
        assertThrows(IllegalArgumentException.class, () -> valid(apk, 100006, 0, hash));
        assertThrows(IllegalArgumentException.class, () -> valid(apk, 100006, UpdatePolicy.MAX_APK_BYTES + 1, hash));
    }
    @Test public void rejectsWrongPackageAndInvalidChecksum() {
        assertThrows(IllegalArgumentException.class, () -> UpdatePolicy.validate("other.app", "com.dazo66.android", "android-build-6", apk, 100006, 3, hash, 26));
        assertThrows(IllegalArgumentException.class, () -> valid(apk, 100006, 3, "not-a-checksum"));
    }
    @Test public void neverFollowsUnencryptedOrCredentialBearingRedirects() {
        assertTrue(UpdatePolicy.isHttps("https://release-assets.githubusercontent.com/file.apk?sig=example"));
        assertFalse(UpdatePolicy.isHttps("http://github.com/file.apk"));
        assertFalse(UpdatePolicy.isHttps("https://user:pass@github.com/file.apk"));
    }
}
