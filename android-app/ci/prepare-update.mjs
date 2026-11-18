import { createHash } from "node:crypto";
import { execFileSync } from "node:child_process";
import { copyFileSync, mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { resolve, join } from "node:path";
import { pathToFileURL } from "node:url";

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
  const source = resolve("app/build/outputs/apk/debug/app-debug.apk");
  const tools = join(process.env.ANDROID_HOME, "build-tools", "37.0.0");
  const badging = execFileSync(
    join(tools, "aapt2"),
    ["dump", "badging", source],
    { encoding: "utf8" },
  );
  const pkg = badging.match(
    /^package: name='([^']+)' versionCode='(\d+)' versionName='([^']*)'/m,
  );
  const sdk = badging.match(/^sdkVersion:'(\d+)'/m);
  if (!pkg || !sdk) throw new Error("Cannot inspect APK metadata");
  const signing = execFileSync(
    join(tools, "apksigner"),
    ["verify", "--print-certs", source],
    { encoding: "utf8" },
  );
  const certificate = signing.match(
    /certificate SHA-256 digest: ([a-f0-9]{64})/i,
  )?.[1];
  const apk = readFileSync(source);
  const manifest = createManifest(
    {
      packageName: pkg[1],
      versionCode: Number(pkg[2]),
      versionName: pkg[3],
      minSdk: Number(sdk[1]),
      certificateSha256: certificate,
    },
    apk,
    process.env,
  );
  mkdirSync("dist", { recursive: true });
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
