// @vitest-environment jsdom
import { mount, flushPromises } from "@vue/test-utils";
import { it, expect, vi, afterEach } from "vitest";
import Training from "./Training.vue";
import { api, type Row } from "./api";
import { comparable } from "./analysis";
vi.mock("./api", async (original) => ({
  ...(await original<typeof import("./api")>()),
  api: vi.fn(),
}));
afterEach(() => {
  vi.restoreAllMocks();
  localStorage.clear();
});
it.each(["VISIBLE", "HIDDEN"] as const)(
  "observes then completes alphabet sequence in %s mode",
  async (visibility) => {
    const config = {
      gridSize: 4,
      symbols: "LETTER" as const,
      count: 2,
      visibility,
    };
    vi.mocked(api).mockResolvedValueOnce({
      id: "test",
      plan: {
        task: "schulte",
        level: 1,
        version: "1.1",
        windowMs: 60000,
        searchConfig: config,
        stimuli: ["B,A,,,,,,,,,,,,,,", "B,A,,,,,,,,,,,,,,"],
        expected: ["A", "B"],
      },
    });
    vi.mocked(api).mockResolvedValueOnce({
      trials: 2,
      correct: 2,
      valid: true,
      observationMs: 100,
      totalResponseMs: 200,
      errorClicks: 0,
    });
    vi.stubGlobal("requestAnimationFrame", (cb: FrameRequestCallback) =>
      setTimeout(() => cb(performance.now()), 0),
    );
    const wrapper = mount(Training, {
      props: { task: "schulte", level: 1, mode: "TRAINING", connected: true },
    });
    const selects = wrapper.findAll("select");
    await selects[1].setValue("LETTER");
    await selects[2].setValue("2");
    await selects[3].setValue(visibility);
    await wrapper
      .findAll("button")
      .find((b) => b.text().includes("我理解了"))!
      .trigger("click");
    await flushPromises();
    expect(wrapper.findAll(".grid button")).toHaveLength(16);
    expect(wrapper.find(".grid button").attributes("disabled")).toBeDefined();
    expect(wrapper.find(".grid").text()).toContain("A");
    await wrapper
      .findAll("button")
      .find((b) => b.text().includes("开始作答"))!
      .trigger("click");
    await vi.waitFor(() =>
      expect(
        wrapper.findAll(".grid button")[1].attributes("disabled"),
      ).toBeUndefined(),
    );
    expect(wrapper.findAll(".grid button")[1].text()).toBe(
      visibility === "HIDDEN" ? "●" : "A",
    );
    expect(
      wrapper.findAll(".grid button")[2].attributes("disabled"),
    ).toBeDefined();
    await wrapper.findAll(".grid button")[1].trigger("click");
    await vi.waitFor(() =>
      expect(
        wrapper.findAll(".grid button")[0].attributes("disabled"),
      ).toBeUndefined(),
    );
    await wrapper.findAll(".grid button")[0].trigger("click");
    await flushPromises();
    const submitted = vi
      .mocked(api)
      .mock.calls.find((c) => c[1] === "PUT")![2] as any;
    expect(submitted.status).toBe("COMPLETED");
    expect(submitted.events.map((e: any) => e.response)).toEqual(["A", "B"]);
    expect(submitted.observationMs).toBeGreaterThanOrEqual(0);
    wrapper.unmount();
    vi.unstubAllGlobals();
    vi.mocked(api).mockClear();
  },
);
it("never mixes configurations or old versions in trends", () => {
  const base = {
    task: "schulte",
    level: 1,
    mode: "TRAINING",
    device: "same",
    result: { valid: true },
    version: "1.1",
    searchConfig: {
      gridSize: 4,
      count: 6,
      symbols: "LETTER",
      visibility: "HIDDEN",
    },
  } as Row;
  const rows: Row[] = [
    base,
    { ...base, searchConfig: { ...base.searchConfig!, visibility: "VISIBLE" } },
    { ...base, searchConfig: { ...base.searchConfig!, count: 8 } },
    { ...base, version: "1.0", searchConfig: undefined },
  ];
  expect(comparable(rows, "schulte", 1, "TRAINING")).toEqual([base]);
});
