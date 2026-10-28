// @vitest-environment jsdom
import { it, expect } from "vitest";
import { cptGuide, cptFeedback } from "./cpt";
import { comparable } from "./analysis";
import type { Row, Plan } from "./api";
it("separates duration, difficulty and practice", () => {
  expect(
    cptGuide(1, { variant: "CUED", durationSeconds: 90 }, false).dose,
  ).toContain("60项");
  expect(
    cptGuide(3, { variant: "CUED", durationSeconds: 180 }, true).dose,
  ).toContain("30秒");
  expect(
    cptGuide(2, { variant: "BASIC", durationSeconds: 90 }, false).example,
  ).toContain("X：按一次");
});
it("explains lure, premature and missed targets", () => {
  const p = {
    stimuli: ["A", "X", "B", "X"],
    expected: ["", "PRESS", "", ""],
    cptConfig: { variant: "CUED", durationSeconds: 90 },
  } as Plan;
  expect(cptFeedback(p, 0, "PRESS")).toContain("提前");
  expect(cptFeedback(p, 1, "")).toContain("漏掉");
  expect(cptFeedback(p, 3, "PRESS")).toContain("前一项不是 A");
});
it("keeps legacy, basic, cued and different durations separate", () => {
  const row = {
    task: "cpt",
    level: 1,
    mode: "TRAINING",
    device: "same",
    version: "2.0",
    result: { valid: true },
    cptConfig: { variant: "CUED", durationSeconds: 90 },
  } as Row;
  const rows: Row[] = [
    row,
    { ...row, cptConfig: { variant: "BASIC", durationSeconds: 90 } },
    { ...row, cptConfig: { variant: "CUED", durationSeconds: 180 } },
    { ...row, version: "1.0", cptConfig: undefined },
  ];
  expect(comparable(rows, "cpt", 1, "TRAINING")).toEqual([row]);
});
