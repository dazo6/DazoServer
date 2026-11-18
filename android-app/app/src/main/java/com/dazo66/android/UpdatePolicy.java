package com.dazo66.android;

import java.net.URI;

/** Shared checks for the public, immutable Android release channel. */
final class UpdatePolicy {
    static final String REPOSITORY = "dazo6/DazoServer";
    static final long MAX_APK_BYTES = 256L * 1024 * 1024;
    static long buildNumber(String tag) {
        if (tag == null || !tag.matches("android-build-[1-9][0-9]{0,8}")) return -1;
        return Long.parseLong(tag.substring("android-build-".length()));
    }
    static String assetUrl(String tag, String name) {
        if (buildNumber(tag) < 0 || !("update.json".equals(name) || "DazoMobile.apk".equals(name)))
            throw new IllegalArgumentException("更新版本无效");
        return "https://github.com/" + REPOSITORY + "/releases/download/" + tag + "/" + name;
    }
    static void validate(String packageName, String expectedPackage, String tag, String apkUrl,
                         long versionCode, long size, String sha256, int minSdk) {
        if (!expectedPackage.equals(packageName)) throw new IllegalArgumentException("更新包不属于当前应用");
        if (buildNumber(tag) < 0 || versionCode != 100000 + buildNumber(tag) || versionCode > 2100000000L)
            throw new IllegalArgumentException("更新版本号无效");
        if (!assetUrl(tag, "DazoMobile.apk").equals(apkUrl)) throw new IllegalArgumentException("更新下载地址无效");
        if (size < 1 || size > MAX_APK_BYTES || sha256 == null || !sha256.matches("[a-fA-F0-9]{64}"))
            throw new IllegalArgumentException("更新包校验信息无效");
        if (minSdk < 26) throw new IllegalArgumentException("更新兼容信息无效");
    }
    static boolean isHttps(String address) {
        try {
            URI uri = URI.create(address);
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null && uri.getUserInfo() == null;
        } catch (Exception ignored) { return false; }
    }
}
