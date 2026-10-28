import { describe, expect, it } from "vitest";
import { comparable } from "./analysis";
import type { Row } from "./api";
const row = (id: string, overrides: Partial<Row> = {}): Row => ({
  id,
  task: "nback",
  level: 1,
  mode: "TRAINING",
  status: "COMPLETED",
  createdAt: "2026-10-27",
  device: "desktop",
  reflection: null,
  result: {
    trials: 30,
    correct: 24,
    accuracy: 0.8,
    hitRate: 0.8,
    falseAlarmRate: 0.2,
    medianRt: 400,
    advice: "",
    valid: true,
  },
  ...overrides,
});
describe("comparable results", () => {
  it("does not mix practice, difficulty, devices, tasks or interrupted sessions", () => {
    const rows = [
      row("latest"),
      row("practice", { mode: "PRACTICE" }),
      row("hard", { level: 2 }),
      row("phone", { device: "phone" }),
      row("other", { task: "gonogo" }),
      row("aborted", { result: null }),
      row("old"),
    ];
    expect(comparable(rows, "nback", 1, "TRAINING").map((r) => r.id)).toEqual([
      "old",
      "latest",
    ]);
  });
  it("uses latest valid device, not a running session device", () => {
    expect(
      comparable(
        [row("pending", { device: "phone", result: null }), row("valid")],
        "nback",
        1,
        "TRAINING",
      ).map((r) => r.id),
    ).toEqual(["valid"]);
  });
  it("limits chart to latest twelve valid entries in chronological order", () => {
    expect(
      comparable(
        Array.from({ length: 15 }, (_, i) => row(String(i))),
        "nback",
        1,
        "TRAINING",
      ).map((r) => r.id),
    ).toEqual(Array.from({ length: 12 }, (_, i) => String(11 - i)));
  });
});
