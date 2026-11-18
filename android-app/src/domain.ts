type Row = Record<string, any>;
export function query(path: string, params: Row) {
  return (
    path +
    "?" +
    Object.entries(params)
      .filter(([, v]) => v !== "" && v !== undefined && v !== null)
      .map(
        ([k, v]) => encodeURIComponent(k) + "=" + encodeURIComponent(String(v)),
      )
      .join("&")
  );
}
export function allowed(me: Row, type: string, point: string) {
  if (me.isSuperAdmin) return true;
  return (me.permissions || []).some((p: string) => {
    if (!p.startsWith(type + ":")) return false;
    const pattern = p
      .slice(type.length + 1)
      .replace(/\\/g, "/")
      .toLowerCase()
      .split("*")
      .map((part: string) => part.replace(/[.*+?^${}()|[\]\\]/g, "\\$&"))
      .join(".*");
    return new RegExp("^" + pattern + "$").test(
      point.replace(/\\/g, "/").toLowerCase(),
    );
  });
}
export function dateLabel(value: any) {
  if (!value) return "—";
  if (typeof value === "number")
    return new Date(value).toLocaleString("zh-CN", { hour12: false });
  return String(value)
    .replace("T", " ")
    .replace(/\.\d+(Z)?$/, "");
}
export function updateAge(value: any, now = Date.now()) {
  if (!value) return "尚未更新";
  const timestamp =
    typeof value === "number"
      ? value
      : Date.parse(String(value).replace(" ", "T"));
  if (!Number.isFinite(timestamp)) return "尚未更新";
  const hours = Math.max(0, Math.floor((now - timestamp) / 3600000));
  return hours < 1
    ? "上次更新 不到 1 小时前"
    : hours < 24
      ? `上次更新 ${hours} 小时前`
      : `上次更新 ${Math.floor(hours / 24)} 天前`;
}
export function bytesLabel(bytes: number) {
  return (bytes / 1024 / 1024).toFixed(1) + " MiB";
}
export function fileUrl(path: string, name: string, thumbnail = false) {
  return query("/api/fanbox/gallery/file", {
    folderPath: path,
    path: name,
    thumbnail: thumbnail ? true : undefined,
  });
}
export function galleryMediaUrl(path: string, item: Row, originals: boolean) {
  return item.type === "folder"
    ? item.posterUrl
    : fileUrl(path, item.name, item.type === "video" || !originals);
}
export function upcomingImageUrls(path: string, items: Row[], index: number) {
  return items
    .slice(index + 1)
    .filter((item) => item.type !== "folder" && item.type !== "video")
    .slice(0, 10)
    .map((item) => fileUrl(path, item.name));
}
export function readingOffset(
  items: Row[],
  layouts: { offset: number }[],
  saved: { anchor: string; within: number; offset: number },
) {
  const anchor = items.findIndex((item) => item.name === saved.anchor);
  return Math.max(
    0,
    anchor >= 0 ? (layouts[anchor]?.offset || 0) + saved.within : saved.offset,
  );
}
export function galleryRestoreOffset(
  items: Row[],
  layouts: { offset: number; length: number }[],
  saved: { anchor: string; within: number; offset: number },
  viewport: number,
  content: number,
) {
  const last = layouts.at(-1);
  if (!last || viewport <= 0 || content <= 0) return null;
  const requested = readingOffset(items, layouts, saved);
  if (content + 2 < Math.min(last.offset + last.length, requested + viewport))
    return null;
  return Math.min(requested, Math.max(0, content - viewport));
}
