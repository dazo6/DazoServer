import test from "node:test";
import assert from "node:assert/strict";
import { createPagedFeed } from "../src/paged.ts";

const deferred = <T>() => {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((done) => {
    resolve = done;
  });
  return { promise, resolve };
};
test("an infinite feed never requests the same next page concurrently", async () => {
  const next = deferred<{ records: { id: number }[]; total: number }>(),
    calls: number[] = [];
  const feed = createPagedFeed(
    async (page) => {
      calls.push(page);
      return page === 1 ? { records: [{ id: 1 }], total: 2 } : next.promise;
    },
    (item) => String(item.id),
  );
  await feed.ensure(1);
  const pending = feed.next();
  await feed.next();
  assert.deepEqual(calls, [1, 2]);
  next.resolve({ records: [{ id: 2 }], total: 2 });
  await pending;
  assert.deepEqual(
    feed.snapshot().records.map((row) => row.id),
    [1, 2],
  );
  assert.equal(feed.snapshot().hasMore, false);
});
test("refresh restores the loaded window and deduplicates overlapping artist pages", async () => {
  let revision = 0;
  const feed = createPagedFeed(
    async (page) => ({
      records:
        page === 1
          ? [
              { id: 1, revision },
              { id: 2, revision },
            ]
          : [
              { id: 2, revision },
              { id: 3, revision },
            ],
      total: 3,
    }),
    (item) => String(item.id),
  );
  await feed.ensure(2);
  assert.equal(feed.snapshot().records.length, 3);
  revision++;
  await feed.refresh();
  assert.equal(feed.snapshot().page, 2);
  assert.ok(feed.snapshot().records.every((row) => row.revision === 1));
});
test("a failed next page retries its page number without skipping artists", async () => {
  let failure = true;
  const calls: number[] = [];
  const feed = createPagedFeed(
    async (page) => {
      calls.push(page);
      if (page === 2 && failure) throw new Error("offline");
      return { records: [{ id: page }], total: 2 };
    },
    (row) => String(row.id),
  );
  await feed.ensure(1);
  await feed.next();
  assert.equal(feed.snapshot().page, 1);
  assert.ok(feed.snapshot().error);
  failure = false;
  await feed.next();
  assert.deepEqual(calls, [1, 2, 2]);
  assert.equal(feed.snapshot().records.length, 2);
});
test("stale responses after leaving a tab cannot overwrite a newer request", async () => {
  const old = deferred<{ records: { id: number }[]; total: number }>();
  let calls = 0;
  const feed = createPagedFeed(
    async () =>
      ++calls === 1 ? old.promise : { records: [{ id: 2 }], total: 1 },
    (row) => String(row.id),
  );
  const first = feed.ensure(1);
  feed.cancel();
  await feed.ensure(1);
  old.resolve({ records: [{ id: 1 }], total: 1 });
  await first;
  assert.deepEqual(feed.snapshot().records, [{ id: 2 }]);
});
test("empty server pages terminate the stream even with an outdated total", async () => {
  const feed = createPagedFeed(
    async (page) => ({ records: page === 1 ? [{ id: 1 }] : [], total: 100 }),
    (row) => String(row.id),
  );
  await feed.ensure(1);
  await feed.next();
  assert.equal(feed.snapshot().hasMore, false);
  assert.equal(feed.snapshot().page, 2);
});
