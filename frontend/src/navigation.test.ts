// @vitest-environment jsdom
import { mount, flushPromises } from "@vue/test-utils";
import { beforeEach, afterEach, describe, expect, it, vi } from "vitest";
import App from "./App.vue";
import { api, names } from "./api";
vi.mock("./api", async (importOriginal) => ({
  ...(await importOriginal<typeof import("./api")>()),
  identity: vi.fn().mockResolvedValue(undefined),
  api: vi.fn().mockResolvedValue([]),
}));
let wrapper: ReturnType<typeof mount>;
let scroll: ReturnType<typeof vi.fn>;
beforeEach(() => {
  localStorage.clear();
  vi.mocked(api).mockImplementation(async (path) =>
    path === "/capabilities"
      ? { protocol: "2026-10-expansion-1", tasks: Object.keys(names) }
      : [],
  );
  scroll = vi.fn();
  window.scrollTo = scroll;
  Object.defineProperty(window, "scrollY", { configurable: true, value: 720 });
  wrapper = mount(App, {
    attachTo: document.body,
    global: {
      stubs: {
        Training: {
          name: "Training",
          template: "<button @click=\"$emit('close')\">返回训练库</button>",
          emits: ["close"],
        },
      },
    },
  });
});
afterEach(() => {
  wrapper.unmount();
  vi.clearAllMocks();
});
const button = (text: string) =>
  wrapper.findAll("button").find((b) => b.text().includes(text))!;
describe("page navigation from a scrolled homepage", () => {
  it("changes view and resets scroll after clicking the sidebar", async () => {
    await flushPromises();
    await button("训练库").trigger("click");
    await flushPromises();
    expect(wrapper.find("header").text()).toContain("训练库");
    expect(scroll).toHaveBeenCalledWith({
      top: 0,
      left: 0,
      behavior: "instant",
    });
    expect(document.activeElement).toBe(wrapper.find("main").element);
  });
  it("uses the same scroll behavior for the homepage CTA", async () => {
    await flushPromises();
    await button("开始今天的练习").trigger("click");
    await flushPromises();
    expect(scroll).toHaveBeenCalledWith({
      top: 0,
      left: 0,
      behavior: "instant",
    });
  });
  it("opens task instructions at top and returns to the actual library", async () => {
    await flushPromises();
    await button("训练库").trigger("click");
    await button("空间路径").trigger("click");
    await flushPromises();
    expect(scroll).toHaveBeenCalled();
    scroll.mockClear();
    await button("返回训练库").trigger("click");
    await flushPromises();
    expect(wrapper.find("header").text()).toContain("训练库");
    expect(scroll).toHaveBeenCalled();
  });
  it("clicking the current page returns to its top as well", async () => {
    await flushPromises();
    await button("今日").trigger("click");
    await flushPromises();
    expect(scroll).toHaveBeenCalled();
  });
});

describe("training and analysis preferences", () => {
  it("does not reuse analysis mode or difficulty as training settings", async () => {
    await flushPromises();
    await button("分析复盘").trigger("click");
    await wrapper.findAll("select")[1].setValue("3");
    await wrapper.findAll("select")[2].setValue("RETEST");
    await button("训练库").trigger("click");
    expect((wrapper.find("select").element as HTMLSelectElement).value).toBe(
      "PRACTICE",
    );
    await button("更新挑战").trigger("click");
    const training = wrapper.findComponent({ name: "Training" });
    expect(training.attributes("level")).toBe("1");
    expect(training.attributes("mode")).toBe("PRACTICE");
  });
});
