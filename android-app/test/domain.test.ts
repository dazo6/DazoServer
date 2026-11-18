import { test } from "node:test";
import assert from "node:assert/strict";
import {
  allowed,
  fileUrl,
  query,
  readingOffset,
  galleryMediaUrl,
  upcomingImageUrls,
} from "../src/domain.ts";

test("read-only users cannot see write operations", () => {
  const me = { permissions: ["read:*"] };
  assert.equal(allowed(me, "read", "D:/图库/画师 A"), true);
  assert.equal(allowed(me, "update", "op:artist.create"), false);
});
test("path ACLs normalize Windows slashes and retain literal metacharacters", () => {
  const me = { permissions: ["read:D:/Gallery/[A]/*"] };
  assert.equal(allowed(me, "read", "d:\\gallery\\[a]\\file.jpg"), true);
  assert.equal(allowed(me, "read", "D:/Gallery/A/file.jpg"), false);
  assert.equal(allowed(me, "read", "D:/Gallery/[A]-private/file.jpg"), false);
});
test("operation rules are scoped by action", () => {
  const me = { permissions: ["update:op:user.*"] };
  assert.equal(allowed(me, "update", "op:user.password"), true);
  assert.equal(allowed(me, "delete", "op:user.delete"), false);
});
test("gallery URLs preserve names and paths containing separators and query characters", () => {
  const url = new URL(
    fileUrl("D:/图库/画师 A", "1 & 2+#.jpg", true),
    "https://example.com",
  );
  assert.equal(url.searchParams.get("folderPath"), "D:/图库/画师 A");
  assert.equal(url.searchParams.get("path"), "1 & 2+#.jpg");
  assert.equal(url.searchParams.get("thumbnail"), "true");
});
test("false filters and zero values are sent while unset filters are omitted", () => {
  assert.equal(
    query("/api/test", { done: false, page: 0, start: "", end: undefined }),
    "/api/test?done=false&page=0",
  );
});
test("reading anchor follows a file when an item is inserted before it", () => {
  assert.equal(
    readingOffset(
      [{ name: "new" }, { name: "last" }],
      [{ offset: 0 }, { offset: 550 }],
      { anchor: "last", within: 81, offset: 81 },
    ),
    631,
  );
});
test("deleted anchors fall back to saved offset", () => {
  assert.equal(
    readingOffset([{ name: "other" }], [{ offset: 0 }], {
      anchor: "deleted",
      within: 81,
      offset: 450,
    }),
    450,
  );
  assert.equal(readingOffset([], [], { anchor: "", within: 0, offset: -2 }), 0);
});

test("original mode bypasses image thumbnails while retaining video and folder previews", () => {
  assert.equal(
    new URL(
      galleryMediaUrl("D:/A", { name: "a.jpg", type: "image" }, true),
      "https://example.com",
    ).searchParams.has("thumbnail"),
    false,
  );
  assert.equal(
    new URL(
      galleryMediaUrl("D:/A", { name: "a.mp4", type: "video" }, true),
      "https://example.com",
    ).searchParams.get("thumbnail"),
    "true",
  );
  assert.equal(
    galleryMediaUrl(
      "D:/A",
      { name: "folder", type: "folder", posterUrl: "/cover.jpg" },
      true,
    ),
    "/cover.jpg",
  );
  assert.equal(
    new URL(
      galleryMediaUrl("D:/A", { name: "a.jpg", type: "image" }, false),
      "https://example.com",
    ).searchParams.get("thumbnail"),
    "true",
  );
});

test("lookahead selects at most ten future images and skips videos and folders", () => {
  const items = [
    { name: "current.jpg", type: "image" },
    { name: "movie.mp4", type: "video" },
    { name: "folder", type: "folder" },
    ...Array.from({ length: 15 }, (_, i) => ({
      name: `${i}.jpg`,
      type: "image",
    })),
  ];
  const urls = upcomingImageUrls("D:/A", items, 0);
  assert.equal(urls.length, 10);
  assert.deepEqual(
    urls.map((url) =>
      new URL(url, "https://example.com").searchParams.get("path"),
    ),
    Array.from({ length: 10 }, (_, i) => `${i}.jpg`),
  );
  assert.ok(urls.every((url) => !url.includes("thumbnail")));
  assert.deepEqual(upcomingImageUrls("D:/A", items, items.length - 1), []);
});
