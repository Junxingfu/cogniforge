// @vitest-environment jsdom
import { beforeEach, it, expect, vi } from "vitest";
import { mount, flushPromises } from "@vue/test-utils";
import { localDay, readPlan, dailyKey, itemState } from "./daily";
import { api, names, type Row } from "./api";
import App from "./App.vue";
vi.mock("./api", async (original) => ({
  ...(await original<typeof import("./api")>()),
  identity: vi.fn().mockResolvedValue(undefined),
  api: vi.fn(),
}));
beforeEach(() => {
  localStorage.clear();
  window.scrollTo = vi.fn();
  vi.mocked(api).mockImplementation(async (path) =>
    path === "/capabilities"
      ? { protocol: "2026-10-expansion-1", tasks: Object.keys(names) }
      : [],
  );
});
it("isolates days and owners and recovers invalid plans", () => {
  const day = localDay(new Date(2026, 9, 27, 23, 59));
  expect(day).toBe("2026-10-27");
  const plan = readPlan(day, Object.keys(names));
  plan[0].skipped = true;
  localStorage.setItem(dailyKey(day), JSON.stringify(plan));
  expect(readPlan(day, Object.keys(names))[0].skipped).toBe(true);
  expect(readPlan("2026-10-28", Object.keys(names))[0].skipped).toBe(false);
  localStorage.setItem("cf-token", "another");
  expect(readPlan(day, Object.keys(names))[0].skipped).toBe(false);
  localStorage.setItem(dailyKey(day), "bad");
  expect(readPlan(day, Object.keys(names))).toHaveLength(3);
});
it("does not count free training, interruption or skips as completion", () => {
  const row = {
    id: "one",
    status: "COMPLETED",
    result: { valid: true },
  } as Row;
  expect(itemState({ task: "schulte", skipped: false }, [row])).toBe("待训练");
  expect(
    itemState({ task: "schulte", skipped: false, sessionId: "one" }, [row]),
  ).toBe("已完成");
  expect(
    itemState({ task: "schulte", skipped: false, sessionId: "two" }, [row]),
  ).toBe("需重新开始");
  expect(itemState({ task: "schulte", skipped: true }, [row])).toBe("已跳过");
});
it("Today has a plan, library has catalog, and plan session returns to Today", async () => {
  const w = mount(App, {
    global: {
      stubs: {
        Training: {
          name: "Training",
          template: `<button @click="$emit('started','session-one'); $emit('saved'); $emit('close')">完成模拟训练</button>`,
          emits: ["started", "saved", "close"],
        },
      },
    },
  });
  await flushPromises();
  expect(w.findAll(".task-card")).toHaveLength(0);
  expect(w.findAll(".daily-list li")).toHaveLength(3);
  await w
    .findAll("button")
    .find((b) => b.text().includes("开始今天"))!
    .trigger("click");
  vi.mocked(api).mockImplementation(async (path) =>
    path === "/capabilities"
      ? { protocol: "2026-10-expansion-1", tasks: Object.keys(names) }
      : [
          {
            id: "session-one",
            status: "COMPLETED",
            task: "schulte",
            createdAt: new Date().toISOString(),
            mode: "PRACTICE",
            result: { valid: true },
            reflection: "",
          },
        ],
  );
  await w
    .findAll("button")
    .find((b) => b.text() === "完成模拟训练")!
    .trigger("click");
  await flushPromises();
  expect(w.find("header").text()).toContain("今日");
  expect(w.text()).toContain("已完成 1 / 3");
  await w
    .findAll("button")
    .find((b) => b.text().includes("浏览全部"))!
    .trigger("click");
  expect(w.findAll(".task-card")).toHaveLength(11);
  expect(w.find(".daily-list").exists()).toBe(false);
  w.unmount();
});
it("blocks training against an old service and explains how to reconnect", async () => {
  vi.mocked(api).mockImplementation(async (path) => {
    if (path === "/capabilities") throw new Error("404");
    return [];
  });
  const w = mount(App);
  await flushPromises();
  expect(w.text()).toContain("确认当前项目后端已更新并重启");
  const start = w.findAll("button").find((b) => b.text().includes("开始今天"))!;
  expect(start.attributes("disabled")).toBeDefined();
  w.unmount();
});
