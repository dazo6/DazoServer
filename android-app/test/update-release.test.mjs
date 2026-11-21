import { test } from "node:test";
import assert from "node:assert/strict";
import { createHash } from "node:crypto";
import {
  createManifest,
  inspectApk,
  RELEASE_CERTIFICATE_SHA256,
  ANIMATION_REFLECTION_TYPES,
  validateAnimationMappings,
} from "../ci/prepare-update.mjs";

const info = {
  packageName: "com.dazo66.android",
  versionCode: 100006,
  versionName: "1.2.0.6",
  minSdk: 26,
  certificateSha256: RELEASE_CERTIFICATE_SHA256,
  abis: ["arm64-v8a"],
  debuggable: false,
  label: "DazoGallery",
};
const env = {
  GITHUB_RUN_NUMBER: "6",
  GITHUB_REPOSITORY: "dazo6/DazoServer",
  GITHUB_SHA: "b".repeat(40),
  APP_VERSION_NAME: "1.2.0.6",
};
const apk = Buffer.from("test APK bytes");

test("GIF/WebP reflection classes must survive release shrinking with their original names", () => {
  const mapping = ANIMATION_REFLECTION_TYPES.map(
    (name) => `${name} -> ${name}:`,
  ).join("\n");
  validateAnimationMappings(mapping);
  for (const name of ANIMATION_REFLECTION_TYPES) {
    assert.throws(
      () =>
        validateAnimationMappings(
          mapping.replace(`${name} -> ${name}:`, `${name} -> a:`),
        ),
      /reflection entry/,
    );
  }
});
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
      inspectApk(
        pkg +
          `${field}:'26'\ntargetSdkVersion:'36'\napplication-label:'DazoGallery'\nnative-code: 'arm64-v8a'\n`,
        certificate,
      ),
      info,
    );
  }
});

test("debug, mislabeled and multi-architecture packages cannot be published", () => {
  for (const change of [
    { debuggable: true },
    { label: "Dazo" },
    { abis: ["arm64-v8a", "x86_64"] },
    { abis: ["x86_64"] },
    { abis: [] },
  ])
    assert.throws(() => createManifest({ ...info, ...change }, apk, env));
});

test("a changed certificate blocks publishing an incompatible upgrade", () => {
  const different = { ...info, certificateSha256: "a".repeat(64) };
  assert.throws(
    () => createManifest(different, apk, env),
    /certificate changed/,
  );
  assert.equal(
    createManifest(different, apk, {
      ...env,
      GITHUB_EVENT_NAME: "pull_request",
    }).certificateSha256,
    different.certificateSha256,
  );
});

test("inspection detects the actual APK architecture, name and debug flag", () => {
  const raw =
    "package: name='com.dazo66.android' versionCode='100006' versionName='1.2.0.6'\nminSdkVersion:'26'\napplication-label:'Dazo'\napplication-debuggable\nnative-code: 'arm64-v8a' 'x86_64'\n";
  const found = inspectApk(
    raw,
    `Signer #1 certificate SHA-256 digest: ${info.certificateSha256}`,
  );
  assert.equal(found.debuggable, true);
  assert.equal(found.label, "Dazo");
  assert.deepEqual(found.abis, ["arm64-v8a", "x86_64"]);
  assert.throws(() => createManifest(found, apk, env));
});
test("missing Android compatibility or signing metadata aborts publication", () => {
  assert.throws(() =>
    inspectApk(
      "package: name='com.dazo66.android' versionCode='100006' versionName='1.2.0.6'",
      "",
    ),
  );
});
