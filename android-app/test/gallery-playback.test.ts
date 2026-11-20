import { test } from "node:test";
import assert from "node:assert/strict";
import {
  centeredVideoIndex,
  createScrollActivity,
  fitImageRect,
} from "../src/gallery-playback.ts";
import { createMediaCache } from "../src/media-cache.ts";

const items = [
  { type: "image" },
  { type: "video" },
  { type: "video" },
  { type: "folder" },
];
const layouts = items.map((_, i) => ({ offset: 100 + i * 400, length: 400 }));
test("a held drag cannot start another preview even while the finger stops moving", (t) => {
  t.mock.timers.enable({ apis: ["setTimeout"] });
  let idle = true;
  const activity = createScrollActivity((value) => {
    idle = value;
  });
  activity.beginDrag();
  activity.scroll();
  t.mock.timers.tick(1000);
  assert.equal(idle, false);
  activity.endDrag();
  t.mock.timers.tick(159);
  assert.equal(idle, false);
  t.mock.timers.tick(1);
  assert.equal(idle, true);
  activity.dispose();
});
test("momentum after release postpones playback until scrolling finishes", (t) => {
  t.mock.timers.enable({ apis: ["setTimeout"] });
  let idle = true;
  const activity = createScrollActivity((value) => {
    idle = value;
  });
  activity.beginDrag();
  activity.endDrag();
  for (let i = 0; i < 10; i++) {
    t.mock.timers.tick(100);
    activity.scroll();
    assert.equal(idle, false);
  }
  activity.endMomentum();
  assert.equal(idle, true);
  activity.dispose();
});
test("missing momentum-end events recover after silence and discarded screens cancel pending callbacks", (t) => {
  t.mock.timers.enable({ apis: ["setTimeout"] });
  let idle = true,
    callbacks = 0;
  const activity = createScrollActivity((value) => {
    idle = value;
    callbacks++;
  });
  activity.scroll();
  t.mock.timers.tick(160);
  assert.equal(idle, true);
  activity.beginDrag();
  activity.reset();
  assert.equal(idle, true);
  activity.scroll();
  const before = callbacks;
  activity.dispose();
  t.mock.timers.tick(1000);
  assert.equal(callbacks, before);
});
test("autoplay selects only the video covering the viewport centre", () => {
  assert.equal(centeredVideoIndex(items, layouts, 0, 600), null);
  assert.equal(centeredVideoIndex(items, layouts, 400, 600), 1);
  assert.equal(centeredVideoIndex(items, layouts, 800, 600), 2);
  assert.equal(centeredVideoIndex(items, layouts, 1200, 600), null);
});
test("a sliver at the boundary and an unmeasured viewport cannot start playback", () => {
  assert.equal(centeredVideoIndex(items, layouts, 410, 200), null);
  assert.equal(centeredVideoIndex(items, layouts, 400, 0), null);
  assert.equal(centeredVideoIndex([], [], 0, 600), null);
});
test("a video taller than the viewport plays while filling its centre", () => {
  assert.equal(
    centeredVideoIndex(
      [{ type: "video" }],
      [{ offset: 100, length: 2000 }],
      900,
      600,
    ),
    0,
  );
});
test("image transitions contain both portrait and landscape images without cropping", () => {
  assert.deepEqual(fitImageRect(2, 400, 600, 50), {
    x: 0,
    y: 250,
    width: 400,
    height: 200,
  });
  assert.deepEqual(fitImageRect(0.5, 400, 600, 50), {
    x: 50,
    y: 50,
    width: 300,
    height: 600,
  });
  assert.deepEqual(fitImageRect(NaN, 400, 600), {
    x: 0,
    y: 100,
    width: 400,
    height: 400,
  });
});
test("loaded previews are reused only within their server/user scope", () => {
  const cache = createMediaCache<{ uri: string }>();
  const image = { uri: "file://image" };
  cache.set("server|alice", "/image", image);
  assert.equal(cache.get("server|alice", "/image"), image);
  assert.equal(cache.get("server|bob", "/image"), undefined);
  assert.equal(cache.get("other|alice", "/image"), undefined);
  cache.clear();
  assert.equal(cache.get("server|alice", "/image"), undefined);
});
test("preview metadata has a bounded LRU and protects recently viewed images", () => {
  const cache = createMediaCache<number>(2);
  cache.set("s", "a", 1);
  cache.set("s", "b", 2);
  cache.get("s", "a");
  cache.set("s", "c", 3);
  assert.equal(cache.get("s", "b"), undefined);
  assert.equal(cache.get("s", "a"), 1);
  assert.equal(cache.get("s", "c"), 3);
});
