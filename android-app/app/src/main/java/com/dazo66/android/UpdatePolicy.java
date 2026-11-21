package com.dazo66.android;

/** Update paths stay relative to the signed-in server and never come from a remote manifest. */
final class UpdatePolicy {
    static final long MAX_APK_BYTES = 256L * 1024 * 1024;
    static boolean validVersion(String version) { return version != null && version.matches("[0-9]{1,6}(?:\\.[0-9]{1,6}){2,3}"); }
    static String apkPath(String version) {
        if (!validVersion(version)) throw new IllegalArgumentException("更新版本无效");
        return "/api/app-updates/" + version + "/apk";
    }
    static void validate(String packageName, String expectedPackage, String version, String path,
                         long versionCode, long size, String sha256, int minSdk) {
        if (!expectedPackage.equals(packageName)) throw new IllegalArgumentException("更新包不属于当前应用");
        if (!validVersion(version) || versionCode < 1 || versionCode > 2100000000L) throw new IllegalArgumentException("更新版本号无效");
        if (!apkPath(version).equals(path)) throw new IllegalArgumentException("更新下载路径无效");
        if (size < 1 || size > MAX_APK_BYTES || sha256 == null || !sha256.matches("[a-fA-F0-9]{64}"))
            throw new IllegalArgumentException("更新包校验信息无效");
        if (minSdk < 26) throw new IllegalArgumentException("更新兼容信息无效");
    }
}
