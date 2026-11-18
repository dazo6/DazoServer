import { test } from "node:test";
import assert from "node:assert/strict";
import { createHash } from "node:crypto";
import { createManifest, inspectApk } from "../ci/prepare-update.mjs";

const info = {
  packageName: "com.dazo66.android",
  versionCode: 100006,
  versionName: "1.2.0.6",
  minSdk: 26,
  certificateSha256: "a".repeat(64),
};
const env = {
  GITHUB_RUN_NUMBER: "6",
  GITHUB_REPOSITORY: "dazo6/DazoServer",
  GITHUB_SHA: "b".repeat(40),
  APP_VERSION_NAME: "1.2.0.6",
};
const apk = Buffer.from("test APK bytes");
test("published manifest binds the APK bytes, signing certificate and immutable build URL", () => {
  const result = createManifest(info, apk, env);
  assert.equal(result.sha256, createHash("sha256").update(apk).digest("hex"));
  assert.equal(result.size, apk.length);
  assert.equal(result.certificateSha256, info.certificateSha256);
  assert.equal(
    result.apkUrl,
    "https://github.com/dazo6/DazoServer/releases/download/android-build-6/DazoMobile.apk",
  );
  assert.equal(result.versionCode, 100006);
});
test("old APK version, wrong package and missing signing certificate cannot be published", () => {
  for (const change of [
    { versionCode: 3 },
    { packageName: "another.app" },
    { versionName: "1.1.0" },
    { certificateSha256: "" },
  ])
    assert.throws(() => createManifest({ ...info, ...change }, apk, env));
});
test("rejects empty files and invalid build identities", () => {
  assert.throws(() => createManifest(info, Buffer.alloc(0), env));
  assert.throws(() =>
    createManifest(info, apk, { ...env, GITHUB_RUN_NUMBER: "../6" }),
  );
  assert.throws(() =>
    createManifest(info, apk, { ...env, GITHUB_REPOSITORY: "another/repo" }),
  );
});
test("reads the Android 37 minSdkVersion output and the legacy sdkVersion output", () => {
  const pkg =
    "package: name='com.dazo66.android' versionCode='100006' versionName='1.2.0.6' compileSdkVersion='37'\n";
  const certificate = `V2 Signer: certificate SHA-256 digest: ${info.certificateSha256}\n`;
  for (const field of ["minSdkVersion", "sdkVersion"]) {
    assert.deepEqual(
      inspectApk(pkg + `${field}:'26'\ntargetSdkVersion:'36'\n`, certificate),
      info,
    );
  }
});
test("missing Android compatibility or signing metadata aborts publication", () => {
  assert.throws(() =>
    inspectApk(
      "package: name='com.dazo66.android' versionCode='100006' versionName='1.2.0.6'",
      "",
    ),
  );
});
