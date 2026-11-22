import { test } from "node:test";
import assert from "node:assert/strict";
import {
  dragSelection,
  selectionScrollStep,
} from "../src/gallery-selection.ts";

test("drag selects contiguous range and reversing restores the initial selection", () => {
  const base = new Set(["a", "f"]),
    names = ["a", "b", "c", "d", "e", "f"];
  assert.deepEqual(
    [...dragSelection(base, names, 1, 4, true, () => true)].sort(),
    ["a", "b", "c", "d", "e", "f"],
  );
  assert.deepEqual(
    [...dragSelection(base, names, 1, 2, true, () => true)].sort(),
    ["a", "b", "c", "f"],
  );
  assert.deepEqual([...base], ["a", "f"]);
});
test("drag can deselect, skips forbidden files, and respects the selection limit", () => {
  assert.deepEqual(
    [
      ...dragSelection(
        new Set(["a", "b", "c"]),
        ["a", "b", "c"],
        2,
        0,
        false,
        (n) => n !== "b",
      ),
    ],
    ["b"],
  );
  assert.deepEqual(
    [
      ...dragSelection(
        new Set(["z"]),
        ["a", "b", "c"],
        0,
        2,
        true,
        (n) => n !== "b",
        2,
      ),
    ],
    ["z", "a"],
  );
});
test("edge scrolling goes in the finger direction with a bounded speed", () => {
  assert.equal(selectionScrollStep(300, 100, 600), 0);
  assert.equal(selectionScrollStep(-100, 100, 600), -24);
  assert.equal(selectionScrollStep(800, 100, 600), 24);
  assert.ok(selectionScrollStep(120, 100, 600) < 0);
  assert.equal(selectionScrollStep(100, 200, 100), 0);
});
