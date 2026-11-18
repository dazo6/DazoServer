import { test } from "node:test";
import assert from "node:assert/strict";
import { createPrefetcher } from "../src/prefetch.ts";

const tick = () => new Promise<void>((resolve) => setImmediate(resolve));
test("prefetch downloads serially and never expands beyond ten requested images", async () => {
  const requests: string[] = [];
  const releases: (() => void)[] = [];
  const worker = createPrefetcher((url) => {
    requests.push(url);
    return new Promise<void>((resolve) => releases.push(resolve));
  });
  worker.replace(Array.from({ length: 30 }, (_, i) => String(i)));
  assert.deepEqual(requests, ["0"]);
  for (let i = 0; i < 10; i++) {
    releases.shift()!();
    await tick();
  }
  assert.deepEqual(
    requests,
    Array.from({ length: 10 }, (_, i) => String(i)),
  );
  worker.stop();
});

test("moving the reader drops obsolete queued downloads and closing stops further work", async () => {
  const requests: string[] = [];
  const releases: (() => void)[] = [];
  const worker = createPrefetcher((url) => {
    requests.push(url);
    return new Promise<void>((resolve) => releases.push(resolve));
  });
  worker.replace(["old-1", "old-2", "old-3"]);
  worker.replace(["new-1", "new-2"]);
  releases.shift()!();
  await tick();
  assert.deepEqual(requests, ["old-1", "new-1"]);
  worker.stop();
  releases.shift()!();
  await tick();
  assert.deepEqual(requests, ["old-1", "new-1"]);
});

test("a failed speculative download does not prevent the next image from caching", async () => {
  const requests: string[] = [];
  const worker = createPrefetcher(async (url) => {
    requests.push(url);
    if (url === "broken") throw new Error("offline");
  });
  worker.replace(["broken", "next"]);
  await tick();
  assert.deepEqual(requests, ["broken", "next"]);
  worker.stop();
});
