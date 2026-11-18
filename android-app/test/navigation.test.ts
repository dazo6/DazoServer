import test from "node:test";
import assert from "node:assert/strict";
import {
  normalizeNavigation,
  popScreen,
  pushScreen,
  selectTab,
} from "../src/navigation.ts";
import { galleryRestoreOffset, updateAge } from "../src/domain.ts";

test("upgrading saved navigation keeps the old tab's nested gallery", () => {
  const gallery = { name: "gallery", path: "A/subfolder" };
  assert.deepEqual(normalizeNavigation({ tab: "free", stack: [gallery] }), {
    tab: "free",
    stacks: { free: [gallery] },
  });
});
test("switching tabs restores each independent navigation stack", () => {
  let state = normalizeNavigation({ tab: "artists", stack: [] });
  state = pushScreen(state, { name: "gallery", path: "Artist A" });
  state = selectTab(state, "free");
  state = pushScreen(state, { name: "gallery", path: "Gallery B" });
  state = selectTab(state, "artists");
  assert.equal(state.stacks.artists?.at(-1)?.path, "Artist A");
  assert.equal(state.stacks.free?.at(-1)?.path, "Gallery B");
});
test("back pops one folder without changing another tab's place", () => {
  let state = normalizeNavigation({
    tab: "free",
    stacks: {
      free: [
        { name: "gallery", path: "A" },
        { name: "gallery", path: "A/B" },
      ],
      more: [{ name: "storage" }],
    },
  });
  state = popScreen(state);
  assert.equal(state.stacks.free?.at(-1)?.path, "A");
  assert.deepEqual(state.stacks.more, [{ name: "storage" }]);
  state = popScreen(popScreen(state));
  assert.equal(state.tab, "home");
  assert.deepEqual(state.stacks.more, [{ name: "storage" }]);
});
test("artist update ages use hours and days, including missing and future times", () => {
  const now = Date.parse("2026-11-19T12:00:00Z");
  assert.equal(updateAge("2026-11-19T09:00:00Z", now), "上次更新 3 小时前");
  assert.equal(updateAge(now - 24 * 3600000, now), "上次更新 1 天前");
  assert.equal(updateAge(now - 72 * 3600000, now), "上次更新 3 天前");
  assert.equal(updateAge(now - 59000, now), "上次更新 不到 1 小时前");
  assert.equal(updateAge(now + 3600000, now), "上次更新 不到 1 小时前");
  assert.equal(updateAge("bad date", now), "尚未更新");
});
test("a loading placeholder cannot replace a saved gallery position with zero", () => {
  const images = [{ name: "first.jpg" }, { name: "second.jpg" }];
  const layouts = [
    { offset: 94, length: 600 },
    { offset: 694, length: 600 },
  ];
  const saved = { anchor: "first.jpg", within: 500, offset: 594 };
  assert.equal(galleryRestoreOffset(images, layouts, saved, 800, 450), null);
  assert.equal(galleryRestoreOffset(images, layouts, saved, 0, 1294), null);
  assert.equal(galleryRestoreOffset(images, layouts, saved, 400, 1294), 594);
});
test("deleted gallery content clamps the position after the new list is measured", () => {
  const images = [{ name: "only.jpg" }],
    layouts = [{ offset: 94, length: 600 }];
  const saved = { anchor: "deleted.jpg", within: 50, offset: 2000 };
  assert.equal(galleryRestoreOffset(images, layouts, saved, 400, 300), null);
  assert.equal(galleryRestoreOffset(images, layouts, saved, 400, 694), 294);
});
