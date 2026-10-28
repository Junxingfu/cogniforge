// @vitest-environment jsdom
import { mount } from "@vue/test-utils";
import { describe, it, expect } from "vitest";
import Training from "./Training.vue";
import { resultMetrics } from "./taskGuides";
import type { Result } from "./api";
describe("task instructions and honest feedback", () => {
  it.each(["digits", "reverse"])(
    "offers lengths 3 through 6 for %s",
    async (task) => {
      const w = mount(Training, {
        props: { task, level: 1, mode: "TRAINING", connected: true },
      });
      expect(w.findAll("select option").map((o) => o.text())).toEqual([
        "3 位",
        "4 位",
        "5 位",
        "6 位",
      ]);
      await w.find("select").setValue("4");
      expect(w.emitted("update:level")).toEqual([[4]]);
      await w.setProps({ level: 4 });
      expect(w.text()).toContain("5轮 × 6位");
      w.unmount();
    },
  );
  it("shows rule examples and concrete parameters before allowing a disconnected user to start", () => {
    const wrapper = mount(Training, {
      props: { task: "nback", level: 2, mode: "RETEST", connected: false },
    });
    expect(wrapper.text()).toContain("A → B → A");
    expect(wrapper.text()).toContain("32 项");
    expect(wrapper.text()).toContain("固定条件复测");
    const start = wrapper
      .findAll("button")
      .find((b) => b.text().includes("我理解了"))!;
    expect(start.attributes("disabled")).toBeDefined();
    wrapper.unmount();
  });
  it("updates the spatial span description when the user changes the parameter", async () => {
    const wrapper = mount(Training, {
      props: { task: "corsi", level: 1, mode: "PRACTICE", connected: true },
    });
    await wrapper.find("select").setValue("3");
    expect(wrapper.emitted("update:level")).toEqual([[3]]);
    await wrapper.setProps({ level: 3 });
    expect(wrapper.text()).toContain("5 轮 × 5 个位置");
    wrapper.unmount();
  });
  it("does not present 75% overall accuracy as good inhibition when every stimulus is pressed", () => {
    const result: Result = {
      trials: 40,
      correct: 30,
      accuracy: 0.75,
      hitRate: 1,
      falseAlarmRate: 1,
      medianRt: 400,
      advice: "",
      valid: true,
    };
    const metrics = resultMetrics("gonogo", result);
    expect(metrics).toContainEqual({ label: "STOP 误按率", value: "100%" });
    expect(metrics.some((m) => m.value === "75%")).toBe(false);
  });
  it("distinguishes whole-sequence completion time from single-response time", () => {
    const result: Result = {
      trials: 5,
      correct: 3,
      accuracy: 0.6,
      hitRate: null,
      falseAlarmRate: null,
      medianRt: 2400,
      advice: "",
      valid: true,
    };
    expect(resultMetrics("corsi", result)).toContainEqual({
      label: "正确序列输入时间中位数",
      value: "2.40 秒",
    });
  });
});
