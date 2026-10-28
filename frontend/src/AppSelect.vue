<script setup lang="ts">
import {
  ref,
  computed,
  onMounted,
  onUpdated,
  onBeforeUnmount,
  nextTick,
  useAttrs,
  useId,
} from "vue";
defineOptions({ inheritAttrs: false });
const props = defineProps<{
  modelValue?: string | number;
  value?: string | number;
  modelModifiers?: { number?: boolean };
  disabled?: boolean;
}>();
const emit = defineEmits<{
  "update:modelValue": [value: string | number];
  change: [event: Event];
}>();
const attrs = useAttrs();
const native = ref<HTMLSelectElement>();
const trigger = ref<HTMLButtonElement>();
const popup = ref<HTMLElement>();
const options = ref<{ value: string; label: string; disabled: boolean }[]>([]);
const label = ref("选择选项");
const open = ref(false);
const highlighted = ref(0);
const id = useId();
const position = ref({
  left: "0px",
  top: "0px",
  width: "180px",
  maxHeight: "280px",
});
const current = computed(() =>
  String(props.modelValue ?? props.value ?? options.value[0]?.value ?? ""),
);
const selected = computed(() =>
  options.value.find((o) => o.value === current.value),
);
function syncOptions() {
  const items = Array.from(native.value?.options ?? []).map((o) => ({
    value: o.value,
    label: o.text.trim(),
    disabled: o.disabled,
  }));
  if (JSON.stringify(items) !== JSON.stringify(options.value))
    options.value = items;
  const parent = trigger.value?.closest("label");
  const text = parent
    ? Array.from(parent.childNodes)
        .filter((n) => n.nodeType === Node.TEXT_NODE)
        .map((n) => n.textContent?.trim())
        .join("")
    : "";
  label.value = String(attrs["aria-label"] ?? (text || "选择选项"));
  if (props.disabled) open.value = false;
}
function close(focus = false) {
  open.value = false;
  if (focus) trigger.value?.focus();
}
async function show() {
  if (props.disabled) return;
  const rect = trigger.value!.getBoundingClientRect();
  const below = window.innerHeight - rect.bottom - 12;
  const above = rect.top - 12;
  const height = Math.min(228, Math.max(below, above));
  const upward =
    below < Math.min(228, options.value.length * 36 + 12) && above > below;
  position.value = {
    left:
      Math.max(8, Math.min(rect.left, window.innerWidth - rect.width - 8)) +
      "px",
    top:
      (upward
        ? Math.max(
            8,
            rect.top - 6 - Math.min(height, options.value.length * 36 + 12),
          )
        : rect.bottom + 6) + "px",
    width: rect.width + "px",
    maxHeight: Math.max(48, Math.min(228, upward ? height : below)) + "px",
  };
  highlighted.value = Math.max(
    0,
    options.value.findIndex((o) => o.value === current.value),
  );
  open.value = true;
  await nextTick();
  if (upward && popup.value) {
    position.value.top =
      Math.max(8, rect.top - 6 - popup.value.getBoundingClientRect().height) +
      "px";
  }
  popup.value?.focus();
  scrollOption();
}
function scrollOption() {
  popup.value
    ?.querySelector('[data-highlighted="true"]')
    ?.scrollIntoView({ block: "nearest" });
}
function choose(index: number) {
  const option = options.value[index];
  if (!option || option.disabled) return;
  const value = props.modelModifiers?.number
    ? Number(option.value)
    : option.value;
  emit("update:modelValue", value);
  // Preserve the existing change-event contract for task configuration handlers.
  native.value!.value = option.value;
  emit("change", { target: native.value } as unknown as Event);
  close(true);
}
function nativeChange(e: Event) {
  const value = (e.target as HTMLSelectElement).value;
  emit(
    "update:modelValue",
    props.modelModifiers?.number ? Number(value) : value,
  );
  emit("change", e);
}
async function keys(e: KeyboardEvent) {
  if (e.key === "Tab") {
    close(true);
    return;
  }
  if (e.key === "Escape") {
    e.preventDefault();
    close(true);
    return;
  }
  if (!open.value) {
    if (["ArrowDown", "ArrowUp", "Enter", " "].includes(e.key)) {
      e.preventDefault();
      await show();
    }
    return;
  }
  if (["Enter", " "].includes(e.key)) {
    e.preventDefault();
    choose(highlighted.value);
    return;
  }
  if (["ArrowDown", "ArrowUp", "Home", "End"].includes(e.key)) {
    e.preventDefault();
    const direction = e.key === "ArrowUp" || e.key === "End" ? -1 : 1;
    let next =
      e.key === "Home"
        ? -1
        : e.key === "End"
          ? options.value.length
          : highlighted.value;
    for (let i = 0; i < options.value.length; i++) {
      next = (next + direction + options.value.length) % options.value.length;
      if (!options.value[next].disabled) break;
    }
    highlighted.value = next;
    await nextTick();
    scrollOption();
  } else if (e.key.length === 1) {
    const found = options.value.findIndex(
      (o) =>
        !o.disabled && o.label.toLowerCase().startsWith(e.key.toLowerCase()),
    );
    if (found >= 0) {
      highlighted.value = found;
      await nextTick();
      scrollOption();
    }
  }
}
function outside(e: PointerEvent) {
  if (
    !trigger.value?.contains(e.target as Node) &&
    !popup.value?.contains(e.target as Node)
  )
    close();
}
function moved(e: Event) {
  if (
    open.value &&
    (!(e.target instanceof Node) || !popup.value?.contains(e.target))
  )
    close();
}
onMounted(() => {
  syncOptions();
  document.addEventListener("pointerdown", outside);
  window.addEventListener("resize", moved);
  window.addEventListener("scroll", moved, true);
});
onUpdated(syncOptions);
onBeforeUnmount(() => {
  document.removeEventListener("pointerdown", outside);
  window.removeEventListener("resize", moved);
  window.removeEventListener("scroll", moved, true);
});
</script>
<template>
  <span class="app-select">
    <select
      ref="native"
      class="select-source"
      tabindex="-1"
      aria-hidden="true"
      :value="current"
      :disabled="disabled"
      @change="nativeChange"
    >
      <slot />
    </select>
    <button
      ref="trigger"
      type="button"
      class="select-trigger"
      :disabled="disabled"
      :aria-label="label"
      aria-haspopup="listbox"
      :aria-expanded="open"
      :aria-controls="open ? id : undefined"
      :aria-describedby="!open ? id + '-hint' : undefined"
      @click="open ? close() : show()"
      @keydown="keys"
    >
      <span>{{ selected?.label }}</span
      ><svg aria-hidden="true" width="16" height="16" viewBox="0 0 16 16">
        <path
          d="m4 6 4 4 4-4"
          fill="none"
          stroke="currentColor"
          stroke-width="1.6"
          stroke-linecap="round"
          stroke-linejoin="round"
        />
      </svg>
    </button>
    <span v-if="!open" :id="id + '-hint'" class="select-hint" role="tooltip">{{
      selected?.label
    }}</span>
    <Teleport to="body"
      ><div
        v-if="open"
        :id="id"
        ref="popup"
        class="select-popup"
        :style="position"
        role="listbox"
        tabindex="-1"
        :aria-label="label"
        :aria-activedescendant="id + '-' + highlighted"
        @keydown="keys"
      >
        <div
          v-for="(option, index) in options"
          :id="id + '-' + index"
          :key="option.value"
          class="select-option"
          role="option"
          :aria-selected="option.value === current"
          :aria-disabled="option.disabled"
          :data-highlighted="index === highlighted"
          @pointermove="highlighted = index"
          @click="choose(index)"
        >
          <span class="select-check">{{
            option.value === current ? "✓" : ""
          }}</span
          ><span>{{ option.label }}</span>
        </div>
      </div></Teleport
    >
  </span>
</template>
