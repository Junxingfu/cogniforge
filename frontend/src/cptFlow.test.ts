// @vitest-environment jsdom
import { mount, flushPromises } from "@vue/test-utils";
import { it, expect, vi, afterEach } from "vitest";
import Training from "./Training.vue";
import { api } from "./api";
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
it.each(["PRACTICE", "TRAINING"])(
  "uses frozen CPT timing and correct feedback policy in %s",
  async (mode) => {
    vi.useFakeTimers();
    vi.stubGlobal("requestAnimationFrame", (cb: FrameRequestCallback) =>
      setTimeout(() => cb(performance.now()), 0),
    );
    vi.mocked(api)
      .mockResolvedValueOnce({
        id: "cpt-flow",
        plan: {
          task: "cpt",
          level: 2,
          version: "2.0",
          windowMs: 800,
          gapMs: 400,
          cptConfig: {
            variant: "CUED",
            durationSeconds: mode === "PRACTICE" ? 30 : 90,
          },
          stimuli: ["A", "X"],
          expected: ["", "PRESS"],
        },
      })
      .mockResolvedValueOnce({ trials: 2, correct: 1, valid: true });
    const w = mount(Training, {
      props: { task: "cpt", level: 2, mode, connected: true },
    });
    expect(w.text()).toContain("A → X");
    await w
      .findAll("button")
      .find((b) => b.text().includes("我理解了"))!
      .trigger("click");
    await flushPromises();
    const request = vi.mocked(api).mock.calls[0][2] as any;
    expect(request.cptConfig).toEqual({ variant: "CUED", durationSeconds: 90 });
    await vi.advanceTimersByTimeAsync(402);
    window.dispatchEvent(new KeyboardEvent("keydown", { code: "Space" }));
    await w.vm.$nextTick();
    expect(w.text()).toContain(mode === "PRACTICE" ? "提前按了" : "已记录");
    if (mode === "TRAINING") expect(w.text()).not.toContain("提前按了");
    await vi.advanceTimersByTimeAsync(1205);
    window.dispatchEvent(new KeyboardEvent("keydown", { code: "Space" }));
    await vi.advanceTimersByTimeAsync(1300);
    await flushPromises();
    const body = vi
      .mocked(api)
      .mock.calls.find((c) => c[1] === "PUT")![2] as any;
    expect(body.status).toBe("COMPLETED");
    expect(body.events).toHaveLength(2);
    expect(body.events[0].onsetMs).toBeGreaterThanOrEqual(400);
    expect(
      body.events[1].onsetMs - body.events[0].onsetMs,
    ).toBeGreaterThanOrEqual(1200);
    expect(body.events.every((e: any) => e.rtMs <= 800)).toBe(true);
    w.unmount();
  },
);

it("shows actual practice duration and immediately readable settings without changing saved duration", async () => {
  const w = mount(Training, {
    props: { task: "cpt", level: 2, mode: "PRACTICE", connected: true },
  });
  const settings = w.find(".cpt-settings");
  expect(settings.findAll(".cpt-field")).toHaveLength(3);
  expect(settings.find(".cpt-duration").text()).toContain("30 秒");
  expect(w.find(".task-parameter").exists()).toBe(false);
  expect(settings.text()).toContain("每项 1.2 秒");
  await settings.find("select").setValue("BASIC");
  expect(settings.text()).toContain("看到 X 按键，其他字母不按。");
  await w.setProps({ mode: "TRAINING" });
  const duration = settings.findAll("select")[1];
  expect((duration.element as HTMLSelectElement).value).toBe("90");
  await duration.setValue("180");
  await w.setProps({ mode: "PRACTICE" });
  expect(settings.find(".cpt-duration").text()).toContain("30 秒");
  await w.setProps({ mode: "RETEST" });
  expect(
    (settings.findAll("select")[1].element as HTMLSelectElement).value,
  ).toBe("180");
  await settings.findAll("select")[2].setValue("3");
  expect(w.emitted("update:level")).toEqual([[3]]);
  w.unmount();
});
