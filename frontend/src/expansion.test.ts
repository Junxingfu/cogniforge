// @vitest-environment jsdom
import { mount, flushPromises } from "@vue/test-utils";
import { it, expect, vi, afterEach } from "vitest";
import Training from "./Training.vue";
import { api } from "./api";
import { expanded, normalizeRecall } from "./expansion";
vi.mock("./api", async (original) => ({
  ...(await original<typeof import("./api")>()),
  api: vi.fn(),
}));
afterEach(() => {
  vi.useRealTimers();
  vi.unstubAllGlobals();
  vi.mocked(api).mockReset();
  localStorage.clear();
});
it.each(expanded.map((t) => t.id))(
  "completes and submits %s with hidden recall materials",
  async (task) => {
    vi.useFakeTimers();
    vi.stubGlobal("requestAnimationFrame", (cb: FrameRequestCallback) =>
      setTimeout(() => cb(performance.now()), 0),
    );
    const stimulus =
      task === "stroop"
        ? "红|蓝"
        : task === "switch"
          ? "大小|3"
          : task === "cpt"
            ? "X"
            : task === "loci"
              ? "苹果,雨伞"
              : "3,8,1";
    const expected =
      task === "stroop"
        ? "蓝"
        : task === "switch"
          ? "LEFT"
          : task === "cpt"
            ? "PRESS"
            : task === "reverse"
              ? "1,8,3"
              : stimulus;
    vi.mocked(api)
      .mockResolvedValueOnce({
        id: task,
        plan: {
          task,
          level: 1,
          version: "1.0",
          windowMs: 60000,
          stimuli: [stimulus],
          expected: [expected],
        },
      })
      .mockResolvedValueOnce({
        trials: 1,
        correct: 1,
        accuracy: 1,
        valid: true,
      });
    const w = mount(Training, {
      props: { task, level: 1, mode: "PRACTICE", connected: true },
    });
    await w
      .findAll("button")
      .find((b) => b.text().includes("我理解了"))!
      .trigger("click");
    await flushPromises();
    await vi.advanceTimersByTimeAsync(16000);
    if (task === "stroop" || task === "switch")
      await w
        .findAll(".choice-stage button")
        .find((b) =>
          task === "stroop"
            ? b.attributes("aria-label") === "蓝，快捷键 2"
            : b.text().includes("小于5"),
        )!
        .trigger("click");
    else if (task === "cpt")
      await w
        .findAll("button")
        .find((b) => b.text().includes("记录目标"))!
        .trigger("click");
    else {
      expect(w.find(".recall-stage h2").exists()).toBe(false);
      await w.find("input").setValue(expected.replaceAll(",", " "));
      await w.find("form").trigger("submit");
    }
    await vi.advanceTimersByTimeAsync(61000);
    await flushPromises();
    const call = vi.mocked(api).mock.calls.find((c) => c[1] === "PUT")!;
    expect(call).toBeDefined();
    expect((call[2] as any).events[0].response).toBe(expected);
    expect((call[2] as any).status).toBe("COMPLETED");
    w.unmount();
  },
);
it("normalizes Chinese separators and preserves zero digits", () => {
  expect(normalizeRecall("digits", "0 1，2")).toBe("0,1,2");
  expect(normalizeRecall("loci", " 苹果、雨伞 ")).toBe("苹果,雨伞");
});
it("records the first Stroop shortcut and ignores repeats and later answers", async () => {
  vi.useFakeTimers();
  vi.stubGlobal("requestAnimationFrame", (cb: FrameRequestCallback) =>
    setTimeout(() => cb(performance.now()), 0),
  );
  vi.mocked(api)
    .mockResolvedValueOnce({
      id: "shortcut",
      plan: {
        task: "stroop",
        level: 1,
        version: "1.2",
        windowMs: 2500,
        stimuli: ["红|黄"],
        expected: ["黄"],
      },
    })
    .mockResolvedValueOnce({ trials: 1, correct: 1, valid: true });
  const w = mount(Training, {
    props: { task: "stroop", level: 1, mode: "PRACTICE", connected: true },
  });
  await w
    .findAll("button")
    .find((b) => b.text().includes("我理解了"))!
    .trigger("click");
  await flushPromises();
  await vi.advanceTimersByTimeAsync(600);
  expect(w.findAll(".stroop-swatch")).toHaveLength(0);
  expect(w.findAll(".stroop-option")).toHaveLength(4);
  expect(w.findAll(".stroop-option [style]")).toHaveLength(0);
  expect(w.find(".stroop-stimulus").attributes("style")).toContain(
    "rgb(255, 223, 0)",
  );
  window.dispatchEvent(
    new KeyboardEvent("keydown", { code: "Digit1", repeat: true }),
  );
  window.dispatchEvent(new KeyboardEvent("keydown", { code: "Digit4" }));
  window.dispatchEvent(new KeyboardEvent("keydown", { code: "Digit1" }));
  await vi.advanceTimersByTimeAsync(3000);
  await flushPromises();
  const body = vi.mocked(api).mock.calls.find((c) => c[1] === "PUT")![2] as any;
  expect(body.events[0].response).toBe("黄");
  w.unmount();
});
