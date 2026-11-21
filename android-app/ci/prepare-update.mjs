import { createHash } from "node:crypto";
import { execFileSync } from "node:child_process";
import { copyFileSync, mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { resolve, join } from "node:path";
import { pathToFileURL } from "node:url";

export const RELEASE_CERTIFICATE_SHA256 =
  "6a79b1bb5e1777dc936d71abd9b6e370e0c782760f064525057250e4e548ced5";

export const ANIMATION_REFLECTION_TYPES = [
  "com.facebook.animated.gif.GifImageDecoder",
  "com.facebook.animated.webp.WebPImageDecoder",
  "com.facebook.fresco.animation.factory.AnimatedFactoryV2Impl",
  "com.facebook.fresco.animation.bitmap.preparation.ondemandanimation.FrameLoaderListener",
  "com.facebook.fresco.vito.core.AnimatedImagePerfLoggingListener",
];
export function validateAnimationMappings(mapping) {
  for (const name of ANIMATION_REFLECTION_TYPES) {
    if (!mapping.includes(`${name} -> ${name}:`))
      throw new Error(
        `Animation reflection entry was removed or renamed: ${name}`,
      );
  }
}

export function inspectApk(badging, signing) {
  const pkg = badging.match(
    /^package: name='([^']+)' versionCode='(\d+)' versionName='([^']*)'/m,
  );
  const sdk = badging.match(/^(?:minSdkVersion|sdkVersion):'(\d+)'/m);
  const certificate = signing.match(
    /certificate SHA-256 digest: ([a-f0-9]{64})/i,
  )?.[1];
  const native = badging.match(/^native-code:\s*(.+)$/m)?.[1];
  const label = badging.match(/^application-label:'([^']*)'/m)?.[1];
  if (!pkg || !sdk || !certificate || !native || !label)
    throw new Error("Cannot inspect APK metadata");
  return {
    packageName: pkg[1],
    versionCode: Number(pkg[2]),
    versionName: pkg[3],
    minSdk: Number(sdk[1]),
    certificateSha256: certificate,
    abis: Array.from(native.matchAll(/'([^']+)'/g), (match) => match[1]),
    debuggable: /^application-debuggable\s*$/m.test(badging),
    label,
  };
}

export function createManifest(info, apk, env) {
  const run = Number(env.GITHUB_RUN_NUMBER);
  if (!Number.isSafeInteger(run) || run < 1 || run > 999999999)
    throw new Error("Invalid build number");
  const code = 100000 + run;
  if (
    info.packageName !== "com.dazo66.android" ||
    info.versionCode !== code ||
    info.versionName !== env.APP_VERSION_NAME ||
    info.minSdk !== 26
  )
    throw new Error("APK metadata does not match the CI build version");
  if (apk.length === 0 || apk.length > 256 * 1024 * 1024)
    throw new Error("Invalid APK size");
  if (!/^[a-f0-9]{64}$/i.test(info.certificateSha256))
    throw new Error("APK signing certificate missing");
  if (info.debuggable !== false || info.label !== "DazoGallery")
    throw new Error("Expected a non-debuggable DazoGallery release APK");
  if (
    !Array.isArray(info.abis) ||
    info.abis.length !== 1 ||
    info.abis[0] !== "arm64-v8a"
  )
    throw new Error("Published APK must contain ARM64 only");
  if (
    env.GITHUB_EVENT_NAME !== "pull_request" &&
    info.certificateSha256.toLowerCase() !== RELEASE_CERTIFICATE_SHA256
  )
    throw new Error(
      "Signing certificate changed; existing installations could not upgrade",
    );
  if (
    env.GITHUB_REPOSITORY !== "dazo6/DazoServer" ||
    !/^[a-f0-9]{40}$/.test(env.GITHUB_SHA)
  )
    throw new Error("Invalid source repository or commit");
  const tag = `android-build-${run}`;
  return {
    schemaVersion: 1,
    tag,
    versionCode: code,
    versionName: info.versionName,
    packageName: info.packageName,
    minSdk: info.minSdk,
    size: apk.length,
    sha256: createHash("sha256").update(apk).digest("hex"),
    certificateSha256: info.certificateSha256.toLowerCase(),
    apkUrl: `https://github.com/${env.GITHUB_REPOSITORY}/releases/download/${tag}/DazoMobile.apk`,
    releaseUrl: `https://github.com/${env.GITHUB_REPOSITORY}/releases/tag/${tag}`,
    commit: env.GITHUB_SHA,
    publishedAt: new Date().toISOString(),
  };
}

if (
  process.argv[1] &&
  import.meta.url === pathToFileURL(resolve(process.argv[1])).href
) {
  const source = resolve("app/build/outputs/apk/release/app-release.apk");
  const tools = join(process.env.ANDROID_HOME, "build-tools", "37.0.0");
  const badging = execFileSync(
    join(tools, process.platform === "win32" ? "aapt2.exe" : "aapt2"),
    ["dump", "badging", source],
    { encoding: "utf8" },
  );
  const signing = execFileSync(
    process.env.JAVA_HOME
      ? join(
          process.env.JAVA_HOME,
          "bin",
          process.platform === "win32" ? "java.exe" : "java",
        )
      : "java",
    [
      "-jar",
      join(tools, "lib", "apksigner.jar"),
      "verify",
      "--print-certs",
      source,
    ],
    { encoding: "utf8" },
  );
  const apk = readFileSync(source);
  validateAnimationMappings(
    readFileSync("app/build/outputs/mapping/release/mapping.txt", "utf8"),
  );
  const manifest = createManifest(
    inspectApk(badging, signing),
    apk,
    process.env,
  );
  mkdirSync("dist", { recursive: true });
  copyFileSync(source, "dist/DazoGallery.apk");
  writeFileSync(
    "dist/DazoGallery.apk.sha256",
    `${manifest.sha256}  DazoGallery.apk\n`,
  );
  // Older apps validate this exact asset URL. Both APK names contain identical bytes.
  copyFileSync(source, "dist/DazoMobile.apk");
  writeFileSync(
    "dist/DazoMobile.apk.sha256",
    `${manifest.sha256}  DazoMobile.apk\n`,
  );
  writeFileSync("dist/update.json", JSON.stringify(manifest, null, 2) + "\n");
  console.log(
    `Prepared verified APK ${manifest.versionName}, code ${manifest.versionCode}`,
  );
}
