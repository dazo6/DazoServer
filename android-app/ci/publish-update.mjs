import { createHash } from "node:crypto";
import { readFileSync } from "node:fs";

const repo = process.env.GITHUB_REPOSITORY;
const token = process.env.GH_TOKEN;
if (repo !== "dazo6/DazoServer" || !token)
  throw new Error("Publishing repository credentials unavailable");
const manifest = JSON.parse(readFileSync("dist/update.json", "utf8"));
const tag = `android-build-${process.env.GITHUB_RUN_NUMBER}`;
if (manifest.tag !== tag || manifest.commit !== process.env.GITHUB_SHA)
  throw new Error("Artifact does not belong to this build");
const apk = readFileSync("dist/DazoMobile.apk");
if (
  apk.length !== manifest.size ||
  createHash("sha256").update(apk).digest("hex") !== manifest.sha256
)
  throw new Error("APK checksum mismatch");

async function api(path, method = "GET", body) {
  const response = await fetch(`https://api.github.com/repos/${repo}/${path}`, {
    method,
    headers: {
      Authorization: `Bearer ${token}`,
      Accept: "application/vnd.github+json",
      "X-GitHub-Api-Version": "2026-03-10",
      "Content-Type": "application/json",
    },
    body: body === undefined ? undefined : JSON.stringify(body),
    signal: AbortSignal.timeout(60000),
  });
  if (response.status === 404 && method === "GET") return null;
  if (!response.ok)
    throw new Error(`GitHub ${method} ${path}: ${response.status}`);
  return response.status === 204 ? null : response.json();
}

let release = await api(`releases/tags/${tag}`);
if (release && !release.draft) {
  if (
    ["DazoMobile.apk", "DazoMobile.apk.sha256", "update.json"].some(
      (name) =>
        !release.assets.some(
          (asset) => asset.name === name && asset.state === "uploaded",
        ),
    )
  )
    throw new Error(
      "Existing published update is incomplete; refusing to overwrite it",
    );
  console.log(`Update ${tag} is already published; keeping immutable assets`);
} else {
  if (!release)
    release = await api("releases", "POST", {
      tag_name: tag,
      target_commitish: process.env.GITHUB_SHA,
      name: `Android ${manifest.versionName}`,
      body: `Android 自动构建版本 ${manifest.versionName}\n\n提交：${manifest.commit}\n\n支持在应用内检查、下载并覆盖安装更新。`,
      draft: true,
      prerelease: false,
      make_latest: "false",
    });
  for (const name of [
    "DazoMobile.apk",
    "DazoMobile.apk.sha256",
    "update.json",
  ]) {
    const old = release.assets.find((asset) => asset.name === name);
    if (old) await api(`releases/assets/${old.id}`, "DELETE");
    const url = new URL(release.upload_url.replace(/\{.*$/, ""));
    if (url.protocol !== "https:" || url.hostname !== "uploads.github.com")
      throw new Error("Unexpected upload host");
    url.searchParams.set("name", name);
    const response = await fetch(url, {
      method: "POST",
      headers: {
        Authorization: `Bearer ${token}`,
        "Content-Type": name.endsWith(".json")
          ? "application/json"
          : "application/octet-stream",
      },
      body: readFileSync(`dist/${name}`),
      signal: AbortSignal.timeout(180000),
      redirect: "error",
    });
    if (!response.ok)
      throw new Error(`Upload ${name} failed: ${response.status}`);
    console.log(`Uploaded ${name}`);
  }
  release = await api(`releases/${release.id}`, "PATCH", {
    draft: false,
    make_latest: "false",
  });
  console.log(`Published ${release.html_url}`);
}
