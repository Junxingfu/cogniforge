<script setup lang="ts">
import AppSelect from "./AppSelect.vue";
import CptReport from "./CptReport.vue";
import { cptGuide, cptFeedback } from "./cpt";
import { cptLabel, type CptConfig } from "./api";
import { computed, nextTick, onBeforeUnmount, ref } from "vue";
import {
  api,
  searchLabel,
  type SearchConfig,
  names,
  modes,
  type Plan,
  type Trial,
  type Result,
  type Submission,
} from "./api";
import {
  expanded,
  recallTasks,
  locations,
  ink,
  normalizeRecall,
} from "./expansion";
import { taskGuide, resultMetrics } from "./taskGuides";
const props = defineProps<{
  task: string;
  level: number;
  mode: string;
  connected?: boolean;
  returnLabel?: string;
}>();
const cptConfig = ref<CptConfig>({ variant: "CUED", durationSeconds: 90 });
const guide = computed(() =>
  props.task === "cpt"
    ? cptGuide(props.level, cptConfig.value, props.mode === "PRACTICE")
    : taskGuide(props.task, props.level),
);
const emit = defineEmits<{
  close: [];
  saved: [];
  started: [id: string];
  "update:level": [level: number];
}>();
const config = ref<SearchConfig>({
  gridSize: 4,
  symbols: "NUMBER",
  count: 16,
  visibility: "VISIBLE",
});
const observationMs = ref(0);
const observing = ref(false);
const hidden = computed(
  () => plan.value?.searchConfig?.visibility === "HIDDEN" && !observing.value,
);
function resizeGrid() {
  config.value.count = Math.min(config.value.count, config.value.gridSize ** 2);
}
function beginRecall() {
  observationMs.value = performance.now() - base;
  observing.value = false;
  finishTrial?.();
}
const recallInput = ref("");
const isRecall = computed(() => recallTasks.includes(props.task));
const parts = computed(() => stimulus.value.split("|"));
const plan = ref<Plan>();
const sessionId = ref("");
const running = ref(false);
const busy = ref(false);
const phase = ref("ready");
const stimulus = ref("");
const highlighted = ref(-1);
const index = ref(0);
const result = ref<Result>();
const error = ref("");
const note = ref("");
const feedback = ref("");
let events: Trial[] = [];
let response = "";
let rt: number | null = null;
let onset = 0;
let base = 0;
let generation = 0;
let input: string[] = [];
let finishTrial: (() => void) | undefined;
const instructions: Record<string, string> = {
  nback:
    "记住字母顺序。当前字母与 N 步前相同时按空格，不同时不按。前 N 项只观察，不计分。每项有 2.5 秒响应时间。",
  gonogo:
    "看到 GO 时按空格；看到 STOP 时不要按。每项只有一次有效响应，长按不会重复记录。",
  corsi:
    "先观察方块亮起的位置。出现“请按顺序重现”后，按相同顺序点击方块。每轮最多等待 15 秒。",
  schulte:
    "先观察位置，点击“开始作答”后，按数字或字母顺序点击。隐藏回忆模式会遮住字符；空格不能点击。点错不推进，每个目标 60 秒未完成则中断。",
};
const cells = computed(() =>
  plan.value?.task === "schulte"
    ? plan.value.stimuli[0].split(",")
    : Array.from({ length: 9 }, (_, i) => String(i)),
);
const sleep = (ms: number) =>
  new Promise<void>((resolve) => setTimeout(resolve, ms));
function cache(body: Submission) {
  localStorage.setItem("cf-pending-" + sessionId.value, JSON.stringify(body));
}
function interrupted() {
  const snapshot = [...events];
  if (
    props.task === "schulte" &&
    phase.value === "respond" &&
    input.length &&
    snapshot.length === index.value
  ) {
    snapshot.push({
      index: index.value,
      response: input.join(","),
      rtMs: Math.min(performance.now() - onset, plan.value!.windowMs),
      onsetMs: onset - base,
    });
  }
  if (
    props.task !== "schulte" &&
    phase.value === "respond" &&
    response &&
    snapshot.length === index.value &&
    index.value < (plan.value?.expected.length ?? 0)
  ) {
    snapshot.push({
      index: index.value,
      response,
      rtMs: rt,
      onsetMs: onset - base,
    });
  }
  return {
    status: "ABORTED",
    events: snapshot,
    quality: "INTERRUPTED",
    reflection: note.value,
    ...(props.task === "schulte"
      ? {
          observationMs: observing.value
            ? performance.now() - base
            : observationMs.value,
        }
      : {}),
  };
}
async function start() {
  if (busy.value || running.value || props.connected === false) return;
  busy.value = true;
  error.value = "";
  try {
    const data = await api<{ id: string; plan: Plan }>("/sessions", "POST", {
      task: props.task,
      ...(props.task === "cpt" ? { cptConfig: cptConfig.value } : {}),
      ...(props.task === "schulte" ? { searchConfig: config.value } : {}),
      level: props.level,
      mode: props.mode,
      device:
        navigator.userAgent.slice(0, 400) +
        " | " +
        innerWidth +
        "x" +
        innerHeight,
    });
    sessionId.value = data.id;
    emit("started", data.id);
    plan.value = data.plan;
    events = [];
    base = performance.now();
    cache(interrupted());
    running.value = true;
    busy.value = false;
    const token = ++generation;
    if (props.task === "schulte") {
      observing.value = true;
      phase.value = "观察位置";
      await nextTick();
      base = performance.now();
      await new Promise<void>((resolve) => {
        finishTrial = resolve;
      });
      finishTrial = undefined;
      if (!running.value || token !== generation) return;
    }
    for (
      index.value = 0;
      index.value < data.plan.stimuli.length;
      index.value++
    ) {
      if (!running.value || token !== generation) return;
      phase.value = "准备";
      stimulus.value = "";
      await sleep(
        props.task === "schulte"
          ? 0
          : props.task === "gonogo"
            ? 1500 - data.plan.windowMs
            : props.task === "cpt"
              ? (data.plan.gapMs ?? 500)
              : 500,
      );
      if (!running.value || token !== generation) return;
      response = "";
      rt = null;
      input = [];
      feedback.value = "";
      const value = data.plan.stimuli[index.value];
      if (isRecall.value) {
        recallInput.value = "";
        phase.value = "记忆材料";
        if (["digits", "reverse"].includes(props.task)) {
          for (const digit of value.split(",")) {
            stimulus.value = digit;
            await sleep(800);
            if (!running.value || token !== generation) return;
            stimulus.value = "";
            await sleep(200);
            if (!running.value || token !== generation) return;
          }
        } else {
          stimulus.value =
            props.task === "loci"
              ? value
                  .split(",")
                  .map((word, i) => locations[i] + "：" + word)
                  .join("　")
              : value
                  .replaceAll(",", "")
                  .match(/.{1,3}/g)!
                  .join(" / ");
          await sleep((10 + props.level * 5) * 1000);
          if (!running.value || token !== generation) return;
        }
      }
      if (props.task === "corsi") {
        phase.value = "观察顺序";
        for (const cell of value.split(",")) {
          highlighted.value = Number(cell);
          await sleep(550);
          highlighted.value = -1;
          await sleep(200);
          if (!running.value || token !== generation) return;
        }
      }
      stimulus.value = isRecall.value ? "请回忆并输入" : value;
      phase.value = "呈现";
      await nextTick();
      await new Promise(requestAnimationFrame);
      if (!running.value || token !== generation) return;
      onset = performance.now();
      phase.value = "respond";
      await new Promise<void>((resolve) => {
        const timer = setTimeout(resolve, data.plan.windowMs);
        finishTrial = () => {
          clearTimeout(timer);
          resolve();
        };
      });
      finishTrial = undefined;
      if (!running.value || token !== generation) return;
      if (!response && input.length) {
        response = input.join(",");
        rt = data.plan.windowMs;
      }
      events.push({
        index: index.value,
        response,
        rtMs: rt,
        onsetMs: onset - base,
      });
      cache(interrupted());
      if (props.task === "cpt" && props.mode === "PRACTICE")
        feedback.value = cptFeedback(data.plan, index.value, response);
      if (props.task === "schulte" && phase.value !== "已记录") {
        await finish("ABORTED", "TARGET_TIMEOUT");
        return;
      }
      if (performance.now() - onset > data.plan.windowMs + 250) {
        await finish("ABORTED", "TIMING_DELAY");
        return;
      }
    }
    if (props.task === "cpt" && props.mode === "PRACTICE") {
      phase.value = "练习反馈";
      stimulus.value = "";
      await sleep(data.plan.gapMs ?? 500);
      if (!running.value || token !== generation) return;
    }
    await finish("COMPLETED", "");
  } catch (e) {
    error.value = (e as Error).message;
    if (running.value) {
      running.value = false;
      generation++;
    }
  } finally {
    busy.value = false;
  }
}
function choose(value: string) {
  if (!running.value || phase.value !== "respond" || response) return;
  const elapsed = performance.now() - onset;
  if (elapsed > plan.value!.windowMs || !value) return;
  response = value;
  rt = elapsed;
  cache(interrupted());
  feedback.value = "已记录";
  if (isRecall.value) {
    phase.value = "已记录";
    finishTrial?.();
  }
}
function submitRecall() {
  const value = normalizeRecall(props.task, recallInput.value);
  if (
    value.length > 100 ||
    (!/^[0-9,]+$/.test(value) && props.task !== "loci")
  ) {
    feedback.value = "请输入有效数字";
    return;
  }
  choose(value);
}
function press() {
  if (
    !running.value ||
    phase.value !== "respond" ||
    response ||
    !["nback", "gonogo", "cpt"].includes(props.task)
  )
    return;
  const elapsed = performance.now() - onset;
  if (elapsed > plan.value!.windowMs) return;
  response = "PRESS";
  rt = elapsed;
  cache(interrupted());
  feedback.value =
    props.task === "cpt" && props.mode === "PRACTICE"
      ? cptFeedback(plan.value!, index.value, response)
      : "已记录";
}
function clickCell(cell: string) {
  if (!running.value || phase.value !== "respond" || !cell) return;
  const elapsed = performance.now() - onset;
  if (elapsed > plan.value!.windowMs) return;
  if (props.task === "corsi") {
    input.push(cell);
    feedback.value = `已输入 ${input.length} / ${props.level + 2}`;
    if (input.length === props.level + 2) {
      response = input.join(",");
      rt = elapsed;
      phase.value = "已记录";
      finishTrial?.();
    }
  }
  if (props.task === "schulte") {
    input.push(cell);
    cache(interrupted());
    if (input.join(",").length > 90) {
      void finish("ABORTED", "TOO_MANY_CLICKS");
      return;
    }
    if (cell === plan.value!.expected[index.value]) {
      response = input.join(",");
      rt = elapsed;
      phase.value = "已记录";
      finishTrial?.();
    } else feedback.value = "请继续寻找 " + plan.value!.expected[index.value];
  }
}
async function finish(status: string, quality: string) {
  if (!running.value) return;
  const snapshot = interrupted();
  running.value = false;
  generation++;
  finishTrial?.();
  highlighted.value = -1;
  phase.value = "done";
  const body = { ...snapshot, status, quality };
  cache(body);
  await sync(body);
}
async function sync(body?: Submission) {
  busy.value = true;
  error.value = "";
  try {
    const stored =
      body ??
      JSON.parse(localStorage.getItem("cf-pending-" + sessionId.value)!);
    result.value = await api<Result>(
      "/sessions/" + sessionId.value + "/result",
      "PUT",
      stored,
    );
    localStorage.removeItem("cf-pending-" + sessionId.value);
    emit("saved");
  } catch (e) {
    error.value = "记录保留在本机，等待同步：" + (e as Error).message;
  } finally {
    busy.value = false;
  }
}
async function saveNote() {
  busy.value = true;
  try {
    await api("/sessions/" + sessionId.value + "/reflection", "PATCH", {
      reflection: note.value,
    });
    feedback.value = "复盘已保存";
    emit("saved");
  } catch (e) {
    error.value = (e as Error).message;
  } finally {
    busy.value = false;
  }
}
function key(e: KeyboardEvent) {
  if (
    running.value &&
    props.task === "stroop" &&
    !e.repeat &&
    /^(Digit|Numpad)[1-4]$/.test(e.code)
  ) {
    e.preventDefault();
    choose(["红", "蓝", "绿", "黄"][Number(e.code.slice(-1)) - 1]);
    return;
  }
  if (isRecall.value) return;
  if (
    running.value &&
    !e.repeat &&
    props.task === "switch" &&
    ["ArrowLeft", "ArrowRight"].includes(e.code)
  ) {
    e.preventDefault();
    choose(e.code === "ArrowLeft" ? "LEFT" : "RIGHT");
  }
  if (e.code === "Space" && running.value) {
    e.preventDefault();
    if (!e.repeat) press();
  }
}
function blur() {
  if (running.value) void finish("ABORTED", "WINDOW_BLUR");
}
function visibility() {
  if (document.hidden) blur();
}
function unload(e: BeforeUnloadEvent) {
  if (running.value) {
    e.preventDefault();
    e.returnValue = "";
  }
}
window.addEventListener("keydown", key);
window.addEventListener("blur", blur);
document.addEventListener("visibilitychange", visibility);
window.addEventListener("beforeunload", unload);
onBeforeUnmount(() => {
  running.value = false;
  generation++;
  finishTrial?.();
  window.removeEventListener("keydown", key);
  window.removeEventListener("blur", blur);
  document.removeEventListener("visibilitychange", visibility);
  window.removeEventListener("beforeunload", unload);
});
</script>
<template>
  <section class="training panel">
    <div class="section-head">
      <div>
        <small>专注于当下的一次练习</small>
        <h2>
          {{ names[task] }} ·
          {{
            task === "schulte"
              ? searchLabel(plan?.searchConfig ?? config)
              : task === "cpt"
                ? cptLabel(
                    plan?.cptConfig ?? {
                      ...cptConfig,
                      durationSeconds:
                        mode === "PRACTICE" ? 30 : cptConfig.durationSeconds,
                    },
                  )
                : guide.options[level - 1]
          }}
        </h2>
      </div>
      <button
        v-if="!running"
        class="quiet"
        :disabled="busy"
        @click="emit('close')"
      >
        {{ returnLabel || "返回训练库" }}</button
      ><button v-else class="quiet" @click="finish('ABORTED', 'USER_STOP')">
        结束本次
      </button>
    </div>
    <p v-if="error" role="alert" class="error">
      {{ error }}
      <button v-if="phase === 'done'" :disabled="busy" @click="sync()">
        重试同步
      </button>
    </p>
    <div v-if="phase === 'ready'" class="intro task-intro">
      <div class="orb">◎</div>
      <h2>先熟悉规则，再开始</h2>
      <p class="mode-note">
        {{ modes[mode] }} ·
        {{
          mode === "PRACTICE"
            ? "熟悉操作，数据不计入正式训练趋势"
            : mode === "RETEST"
              ? "固定条件复测，请保持设备与参数一致"
              : "正式训练，固定本次参数"
        }}
      </p>
      <p>{{ guide.goal }}</p>
      <div v-if="task === 'schulte'" class="controls search-controls">
        <label
          >方格大小<AppSelect
            v-model.number="config.gridSize"
            :disabled="busy"
            @change="resizeGrid"
          >
            <option :value="4">4 × 4</option>
            <option :value="5">5 × 5</option>
          </AppSelect></label
        >
        <label
          >字符类型<AppSelect v-model="config.symbols" :disabled="busy">
            <option value="NUMBER">数字 1、2、3…</option>
            <option value="LETTER">字母 A、B、C…</option>
          </AppSelect></label
        >
        <label
          >字符数量<AppSelect v-model.number="config.count" :disabled="busy">
            <option
              v-for="n in config.gridSize ** 2 - 1"
              :key="n"
              :value="n + 1"
            >
              {{ n + 1 }} 个
            </option>
          </AppSelect></label
        >
        <label
          >作答模式<AppSelect v-model="config.visibility" :disabled="busy">
            <option value="VISIBLE">可见搜索</option>
            <option value="HIDDEN">隐藏回忆</option>
          </AppSelect></label
        >
        <p>
          {{
            config.visibility === "VISIBLE"
              ? "作答时保留字符，按顺序搜索。"
              : "作答时遮住所有字符，凭位置记忆点击。建议从 4–6 个字符开始。"
          }}
          观察时长单独记录，不与作答时间混合。
        </p>
      </div>
      <div v-if="task === 'cpt'" class="cpt-settings">
        <div class="cpt-field">
          <label
            >玩法
            <AppSelect v-model="cptConfig.variant" :disabled="busy">
              <option value="CUED">线索守望</option>
              <option value="BASIC">基础守望</option>
            </AppSelect>
          </label>
          <p>
            {{
              cptConfig.variant === "CUED"
                ? "仅在 A 紧接着出现 X 时按键。"
                : "看到 X 按键，其他字母不按。"
            }}
          </p>
        </div>
        <div class="cpt-field">
          <template v-if="mode === 'PRACTICE'">
            <span class="cpt-field-label">本次时长</span>
            <div class="cpt-duration">约 30 秒 <span>规则练习</span></div>
          </template>
          <label v-else
            >本次时长
            <AppSelect
              v-model.number="cptConfig.durationSeconds"
              :disabled="busy"
            >
              <option :value="90">90 秒</option>
              <option :value="180">3 分钟</option>
            </AppSelect>
          </label>
          <p>
            {{
              mode === "PRACTICE"
                ? "正式训练、固定复测可选 90 秒或 3 分钟。"
                : "短练 90 秒，或延长至 3 分钟。"
            }}
          </p>
        </div>
        <div class="cpt-field">
          <label
            >节奏与干扰
            <AppSelect
              :value="level"
              :disabled="busy"
              @change="
                emit(
                  'update:level',
                  Number(($event.target as HTMLSelectElement).value),
                )
              "
            >
              <option :value="1">入门</option>
              <option :value="2">标准</option>
              <option :value="3">挑战</option>
            </AppSelect>
          </label>
          <p>
            每项 {{ [1.5, 1.2, 1][level - 1] }} 秒 ·
            {{ ["较少", "适量", "较多"][level - 1] }}干扰
          </p>
        </div>
      </div>
      <label v-if="task !== 'schulte' && task !== 'cpt'" class="task-parameter"
        >{{ guide.parameter }}
        <AppSelect
          :value="task === 'schulte' ? 1 : level"
          :disabled="busy || task === 'schulte'"
          @change="
            emit(
              'update:level',
              Number(($event.target as HTMLSelectElement).value),
            )
          "
        >
          <option
            v-for="(option, i) in guide.options"
            :key="option"
            :value="i + 1"
          >
            {{ option }}
          </option>
        </AppSelect>
      </label>
      <p class="dose">{{ guide.dose }}</p>
      <p>{{ instructions[task] }}</p>
      <div class="rule-example">
        <strong>操作示例</strong>
        <p>{{ guide.example }}</p>
      </div>
      <p class="muted">{{ guide.focus }}</p>
      <p v-if="connected === false" role="status" class="banner">
        服务暂未连接，可以先阅读规则。恢复连接后返回训练库重试。
      </p>
      <p class="muted">
        字母与停走任务需要实体键盘。切换窗口会结束本轮并保留中断记录。请先完成规则练习；练习数据不会进入正式趋势。
      </p>
      <button
        class="primary"
        :disabled="busy || connected === false"
        @click="start"
      >
        {{ busy ? "准备中…" : "我理解了，开始" }}
      </button>
    </div>
    <div v-else-if="running" class="stage">
      <p v-if="task === 'cpt' && plan?.cptConfig" class="muted">
        {{ cptLabel(plan.cptConfig) }} · 本轮参数固定 · 约剩
        {{
          Math.max(
            0,
            Math.ceil(
              ((plan.expected.length - index) *
                (plan.windowMs + (plan.gapMs ?? 500))) /
                1000,
            ),
          )
        }}
        秒
      </p>
      <small
        >第 {{ index + 1 }} / {{ plan?.stimuli.length }} 项 ·
        {{ phase === "respond" ? "请作答" : phase }}</small
      >
      <div
        v-if="task === 'nback' || task === 'gonogo' || task === 'cpt'"
        class="stimulus"
        :class="{ stop: stimulus === 'STOP' }"
      >
        {{ ["respond", "呈现"].includes(phase) ? stimulus : "＋" }}
      </div>
      <div v-else-if="task === 'stroop'" class="choice-stage">
        <div class="stimulus stroop-stimulus" :style="{ color: ink[parts[1]] }">
          {{ ["respond", "呈现"].includes(phase) ? parts[0] : "＋" }}
        </div>
        <p>按墨水颜色选择，忽略字义 · 点击汉字，或按 1–4</p>
        <div class="stroop-choices">
          <button
            v-for="(color, colorIndex) in ['红', '蓝', '绿', '黄']"
            class="stroop-option"
            :aria-label="color + '，快捷键 ' + (colorIndex + 1)"
            :key="color"
            :disabled="phase !== 'respond' || feedback === '已记录'"
            @click="choose(color)"
          >
            <span>{{ color }}</span
            ><kbd aria-hidden="true">{{ colorIndex + 1 }}</kbd>
          </button>
        </div>
      </div>
      <div v-else-if="task === 'switch'" class="choice-stage">
        <h2>
          {{ ["respond", "呈现"].includes(phase) ? parts[0] + "规则" : "准备" }}
        </h2>
        <div class="stimulus">
          {{ ["respond", "呈现"].includes(phase) ? parts[1] : "＋" }}
        </div>
        <button
          :disabled="phase !== 'respond' || feedback === '已记录'"
          @click="choose('LEFT')"
        >
          ← {{ parts[0] === "奇偶" ? "奇数" : "小于5" }}
        </button>
        <button
          :disabled="phase !== 'respond' || feedback === '已记录'"
          @click="choose('RIGHT')"
        >
          {{ parts[0] === "奇偶" ? "偶数" : "大于5" }} →
        </button>
      </div>
      <div v-else-if="isRecall" class="recall-stage">
        <h2 v-if="phase === '记忆材料'">{{ stimulus }}</h2>
        <form v-else-if="phase === 'respond'" @submit.prevent="submitRecall">
          <label
            >{{
              task === "reverse"
                ? "请倒序输入数字"
                : task === "loci"
                  ? "按路线输入词语，用空格分隔"
                  : "请按原序输入数字"
            }}
            <input
              v-model="recallInput"
              maxlength="80"
              autocomplete="off"
              :inputmode="task === 'loci' ? 'text' : 'numeric'"
            /> </label
          ><button class="primary" type="submit">提交本轮</button>
        </form>
        <p v-else>{{ phase }}</p>
      </div>
      <div
        v-else
        class="grid"
        :class="task"
        :style="{
          gridTemplateColumns:
            task === 'schulte'
              ? `repeat(${plan?.searchConfig?.gridSize ?? 4}, 1fr)`
              : undefined,
          visibility:
            task === 'schulte' && index === 0 && phase === '准备'
              ? 'hidden'
              : 'visible',
        }"
      >
        <button
          v-for="(cell, position) in cells"
          :key="position"
          :aria-label="
            task === 'schulte'
              ? !cell
                ? '空格'
                : hidden
                  ? `位置 ${position + 1}`
                  : cell
              : undefined
          "
          :class="{ lit: highlighted === Number(cell) }"
          :disabled="phase !== 'respond' || !cell"
          @click="clickCell(cell)"
        >
          {{ task === "schulte" ? (cell && hidden ? "●" : cell) : " "
          }}<span class="sr-only" v-if="task === 'corsi'"
            >方块 {{ Number(cell) + 1 }}</span
          >
        </button>
      </div>
      <button
        v-if="task === 'cpt'"
        :disabled="phase !== 'respond' || feedback === '已记录'"
        @click="press"
      >
        记录目标（或空格）
      </button>
      <button v-if="observing" class="primary" @click="beginRecall">
        我看好了，开始作答
      </button>
      <p>
        {{
          task === "schulte"
            ? observing
              ? "先观察，准备好后开始作答"
              : "请寻找 " + plan?.expected[index]
            : task === "corsi"
              ? phase === "respond"
                ? "请按顺序重现"
                : "观察亮起的位置"
              : expanded.some((t) => t.id === task)
                ? "按上方规则完成；切换窗口会中断"
                : "符合目标时按空格"
        }}
      </p>
      <small aria-live="polite">{{ feedback || " " }}</small>
    </div>
    <div v-else-if="phase === 'done'" class="intro">
      <small>训练记录</small>
      <h2>{{ result?.valid ? "本次练习已完成" : "本次已结束" }}</h2>
      <p v-if="!result">{{ busy ? "正在保存…" : "等待同步后查看结果" }}</p>
      <template v-if="result"
        ><div class="metrics result-metrics">
          <div
            v-for="metric in resultMetrics(task, result)"
            :key="metric.label"
          >
            <strong>{{ metric.value }}</strong
            ><span>{{ metric.label }}</span>
          </div>
        </div>
        <p class="muted">
          {{ modes[mode] }} · {{ result.trials }} 个可评分{{
            task === "corsi" ? "序列" : "试次"
          }}
          · {{ result.valid ? "记录完整" : "不纳入有效趋势" }}
        </p>
        <p
          v-if="
            result.targets != null && (task === 'nback' || task === 'gonogo')
          "
          class="muted"
        >
          目标命中 {{ result.hits }} / {{ result.targets }}；非目标误按
          {{ result.falseAlarms }} / {{ result.nonTargets }}。
        </p>
        <p v-if="result.observationMs != null">
          观察 {{ (result.observationMs / 1000).toFixed(1) }} 秒 · 累计作答
          {{ ((result.totalResponseMs ?? 0) / 1000).toFixed(1) }} 秒 · 误点
          {{ result.errorClicks }} 次
        </p>
        <CptReport :result="result" />
        <div
          v-if="result.breakdown && !result.breakdown['全程']"
          class="table-scroll"
        >
          <table>
            <thead>
              <tr>
                <th>条件 / 分段</th>
                <th>正确 / 总数</th>
                <th>正确时间中位数</th>
                <th>补充指标</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(stats, label) in result.breakdown" :key="label">
                <td>{{ label }}</td>
                <td>{{ stats.correct }} / {{ stats.n }}</td>
                <td>
                  {{
                    stats.medianRt == null
                      ? "—"
                      : Math.round(stats.medianRt) + " ms"
                  }}
                </td>
                <td>
                  {{
                    stats.positionAccuracy != null
                      ? "位置正确率 " +
                        Math.round(stats.positionAccuracy * 100) +
                        "%"
                      : stats.misses != null
                        ? `遗漏 ${stats.misses} / ${stats.targets} · 误报 ${stats.falseAlarms} · RT标准差 ${stats.rtSd == null ? "—" : Math.round(stats.rtSd) + " ms"}`
                        : "结合准确率解释时间差"
                  }}
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <details v-if="isRecall">
          <summary>查看每轮回忆与参考顺序</summary>
          <ol>
            <li v-for="(expected, i) in plan?.expected" :key="i">
              参考：{{ expected.replaceAll(",", " ") }}；你的回忆：{{
                events[i]?.response.replaceAll(",", " ") || "未提交 / 超时"
              }}
            </li>
          </ol>
        </details>
        <p v-if="task === 'chunk' || task === 'loci'">
          生活应用：选一份真实清单，使用今天的策略。请在复盘中记录使用场景、是否借助提醒及遗漏内容。
        </p>
        <p>{{ guide.focus }}</p>
        <p>{{ result.advice }}</p>
        <label
          >这次有什么发现？<textarea
            v-model="note"
            maxlength="1000"
            placeholder="例如：后半段容易着急，下次先稳定准确率。"
          /></label
        ><button class="primary" :disabled="busy" @click="saveNote">
          保存复盘
        </button>
        <p aria-live="polite">{{ feedback }}</p></template
      >
    </div>
  </section>
</template>
