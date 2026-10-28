// @vitest-environment jsdom
import { mount } from "@vue/test-utils";
import { it, expect, vi, afterEach } from "vitest";
import AppSelect from "./AppSelect.vue";
afterEach(() => {
  vi.restoreAllMocks();
  document.body.innerHTML = "";
});
it("anchors the popup to the full trigger rectangle and selects with keyboard", async () => {
  Element.prototype.scrollIntoView = vi.fn();
  const w = mount(AppSelect, {
    attachTo: document.body,
    props: { modelValue: "a" },
    slots: {
      default:
        '<option value="a">规则练习</option><option value="b">正式训练</option>',
    },
  });
  const button = w.find("button");
  vi.spyOn(button.element, "getBoundingClientRect").mockReturnValue({
    left: 120,
    top: 80,
    bottom: 124,
    right: 300,
    width: 180,
    height: 44,
    x: 120,
    y: 80,
    toJSON() {},
  });
  await button.trigger("click");
  const popup = document.querySelector('[role="listbox"]') as HTMLElement;
  expect(popup.style.left).toBe("120px");
  expect(popup.style.top).toBe("130px");
  expect(popup.style.width).toBe("180px");
  popup.dispatchEvent(
    new KeyboardEvent("keydown", { key: "ArrowDown", bubbles: true }),
  );
  await w.vm.$nextTick();
  popup.dispatchEvent(
    new KeyboardEvent("keydown", { key: "Enter", bubbles: true }),
  );
  await w.vm.$nextTick();
  expect(w.emitted("update:modelValue")).toEqual([["b"]]);
  expect(document.querySelector('[role="listbox"]')).toBeNull();
  expect(document.activeElement).toBe(button.element);
  w.unmount();
});
it("closes on viewport scroll and respects disabled controls", async () => {
  Element.prototype.scrollIntoView = vi.fn();
  const w = mount(AppSelect, {
    attachTo: document.body,
    props: { disabled: true },
    slots: { default: '<option value="1">一个较长的选项</option>' },
  });
  await w.find("button").trigger("click");
  expect(document.querySelector('[role="listbox"]')).toBeNull();
  await w.setProps({ disabled: false });
  await w.find("button").trigger("click");
  expect(document.querySelector('[role="listbox"]')).not.toBeNull();
  window.dispatchEvent(new Event("scroll"));
  await w.vm.$nextTick();
  expect(document.querySelector('[role="listbox"]')).toBeNull();
  w.unmount();
});
