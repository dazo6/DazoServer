import { test } from "node:test";
import assert from "node:assert/strict";
import {
  completedUploadStamp,
  uploadSummary,
  type UploadJob,
} from "../src/upload-state.ts";

const job = (id: string, values: Partial<UploadJob> = {}): UploadJob => ({
  id,
  scope: "host|user",
  folder: "D:/Gallery/A",
  name: id,
  phase: "queued",
  total: 1000,
  bytes: 0,
  dismissed: false,
  ...values,
});
test("upload progress is weighted by file bytes rather than averaging file percentages", () => {
  const result = uploadSummary([
    job("small", { phase: "complete", total: 100 }),
    job("large", { phase: "uploading", total: 900 }),
  ]);
  assert.equal(result.percent, 10);
  assert.equal(result.complete, 1);
  assert.equal(result.current?.name, "large");
});
test("receiving all bytes does not report a committed upload until the server accepts it", () => {
  assert.equal(
    uploadSummary([job("file", { phase: "uploading", bytes: 1000 })]).percent,
    99,
  );
  assert.equal(
    uploadSummary([job("file", { phase: "complete", bytes: 1000 })]).percent,
    100,
  );
});
test("failed files leave the remaining queue active and keep an actionable result at the end", () => {
  const failed = job("bad", { phase: "error", error: "connection lost" });
  assert.equal(uploadSummary([failed, job("next")]).active, 1);
  const result = uploadSummary([failed, job("next", { phase: "complete" })]);
  assert.equal(result.failed, 1);
  assert.equal(result.complete, 1);
  assert.equal(result.active, 0);
});
test("preparing sources and dismissed earlier transfers cannot corrupt current batch progress", () => {
  const result = uploadSummary([
    job("old", { phase: "complete", total: 1000000, dismissed: true }),
    job("new", { phase: "preparing", total: 0 }),
  ]);
  assert.equal(result.percent, 0);
  assert.equal(result.total, 0);
  assert.equal(result.visible.length, 1);
  assert.equal(result.active, 1);
});
test("gallery invalidation only follows successful uploads to that destination and survives dismissal", () => {
  const rows = [
    job("done", { phase: "complete" }),
    job("failed", { phase: "error" }),
    job("other", { phase: "complete", folder: "D:/Gallery/B" }),
  ];
  assert.equal(completedUploadStamp(rows, "D:/Gallery/A"), "done");
  assert.equal(
    completedUploadStamp(
      rows.map((row) => ({ ...row, dismissed: true })),
      "D:/Gallery/A",
    ),
    "done",
  );
  assert.equal(
    completedUploadStamp(
      [...rows, job("new", { phase: "complete" })],
      "D:/Gallery/A",
    ),
    "done|new",
  );
});
test("cancelled transfers are distinguishable from successful completion", () => {
  const result = uploadSummary([
    job("cancelled", { phase: "cancelled", bytes: 400 }),
  ]);
  assert.equal(result.cancelled, 1);
  assert.equal(result.complete, 0);
  assert.equal(result.active, 0);
  assert.equal(result.failed, 0);
});
