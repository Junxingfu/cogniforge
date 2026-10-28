<script setup lang="ts">
import AppSelect from "./AppSelect.vue";
import CptReport from "./CptReport.vue";
import { cptLabel } from "./api";
import {
  localDay,
  dailyKey,
  readPlan,
  itemState,
  type DailyItem,
} from "./daily";
import { resultMetrics } from "./taskGuides";
import { expanded } from "./expansion";
import { searchLabel } from "./api";
import {
  computed,
  nextTick,
  onMounted,
  onBeforeUnmount,
  ref,
  watch,
} from "vue";
import { comparable, conditionKey } from "./analysis";
import Training from "./Training.vue";
import { api, identity, names, modes, percent, type Row } from "./api";
const page = ref("今日");
const rows = ref<Row[]>([]);
const error = ref("");
const loading = ref(false);
const ready = ref(false);
const active = ref("");
const level = ref(1);
const taskLevels = ref<Record<string, number>>({});
function setTaskLevel(value: number) {
  level.value = value;
  taskLevels.value[active.value] = value;
}
const mode = ref("PRACTICE");
const filter = ref("nback");
const analysisLevel = ref(1);
const analysisMode = ref("TRAINING");
const searchCondition = ref("");
watch(filter, (task) => {
  if (analysisLevel.value > (["digits", "reverse"].includes(task) ? 4 : 3))
    analysisLevel.value = 1;
});
watch([filter, analysisMode], () => {
  searchCondition.value = "";
});
const searchConditions = computed(() => {
  const options = new Map<string, string>();
  for (const row of rows.value)
    if (
      ["schulte", "cpt"].includes(row.task) &&
      row.task === filter.value &&
      row.mode === analysisMode.value &&
      row.result?.valid
    )
      options.set(
        conditionKey(row),
        (row.task === "cpt"
          ? cptLabel(row.cptConfig)
          : searchLabel(row.searchConfig)) +
          " · v" +
          (row.version ?? "1.0"),
      );
  return [...options].map(([value, label]) => ({ value, label }));
});
const content = ref<HTMLElement>();
async function focusContent() {
  await nextTick();
  content.value?.focus({ preventScroll: true });
  window.scrollTo({ top: 0, left: 0, behavior: "instant" });
}
async function navigate(destination: string) {
  if (active.value) return;
  refreshDay();
  page.value = destination;
  selected.value = undefined;
  await focusContent();
}
async function closeTask() {
  active.value = "";
  await navigate(returnPage.value);
  activeSlot.value = null;
  await load();
}
const selected = ref<Row>();
const details = ref("");
const pending = ref(0);
const confirmDelete = ref(false);
const strategy = ref(localStorage.getItem("cf-strategy") || "");
const notice = ref("");
const tasks = [
  ...expanded,
  {
    id: "nback",
    tag: "工作记忆更新",
    icon: "◈",
    description: "关注变化中的信息，练习持续更新。",
    time: "30 个评分项 · 约 1 分 40 秒",
    color: "mint",
  },
  {
    id: "gonogo",
    tag: "反应抑制",
    icon: "◐",
    description: "该行动时行动，该停下时停下。",
    time: "40 项 · 约 1 分钟",
    color: "peach",
  },
  {
    id: "corsi",
    tag: "视空间记忆",
    icon: "▦",
    description: "跟随光点，重现一段空间路径。",
    time: "5 轮 · 时长随输入速度变化",
    color: "lavender",
  },
  {
    id: "schulte",
    tag: "视觉搜索 · 热身",
    icon: "⊞",
    description: "先观察，再按序点击数字或字母；可选择隐藏回忆。",
    time: "4×4 / 5×5 · 数量可选 · 两种模式",
    color: "sand",
  },
];
const returnPage = ref("训练库");
const activeSlot = ref<number | null>(null);
const day = ref(localDay());
const daily = ref<DailyItem[]>(
  readPlan(
    day.value,
    tasks.map((t) => t.id),
  ),
);
function refreshDay() {
  if (!active.value && day.value !== localDay()) {
    day.value = localDay();
    daily.value = readPlan(
      day.value,
      tasks.map((t) => t.id),
    );
  }
}
function persistPlan() {
  localStorage.setItem(dailyKey(day.value), JSON.stringify(daily.value));
}
const states = computed(() =>
  daily.value.map((item) => itemState(item, rows.value)),
);
const nextSlot = computed(() =>
  states.value.findIndex((s) => s !== "已完成" && s !== "已跳过"),
);
const completed = computed(
  () => states.value.filter((s) => s === "已完成").length,
);
const skipped = computed(
  () => states.value.filter((s) => s === "已跳过").length,
);
const todayRows = computed(() =>
  rows.value.filter((r) => localDay(new Date(r.createdAt)) === day.value),
);
const needsReflection = computed(() =>
  todayRows.value.filter((r) => r.result?.valid && !r.reflection?.trim()),
);
function startDaily(index: number) {
  if (index < 0 || states.value[index] === "已完成") return;
  daily.value[index].skipped = false;
  persistPlan();
  openTask(daily.value[index].task);
  activeSlot.value = index;
}
function started(id: string) {
  if (activeSlot.value !== null) {
    daily.value[activeSlot.value].sessionId = id;
    persistPlan();
  }
}
function replaceDaily(index: number, event: Event) {
  daily.value[index] = {
    task: (event.target as HTMLSelectElement).value,
    skipped: false,
  };
  persistPlan();
}
async function reviewToday(row: Row) {
  await navigate("训练记录");
  await detail(row);
}
let dayTimer: ReturnType<typeof setInterval>;
onMounted(() => {
  dayTimer = setInterval(refreshDay, 60000);
});
onBeforeUnmount(() => clearInterval(dayTimer));
const category = ref("全部");
function taskCategory(id: string) {
  return ["chunk", "loci"].includes(id)
    ? "记忆策略"
    : ["schulte", "cpt"].includes(id)
      ? "注意与搜索"
      : ["stroop", "switch", "gonogo"].includes(id)
        ? "抑制与灵活性"
        : "记忆";
}
const libraryTasks = computed(() =>
  tasks.filter(
    (t) => category.value === "全部" || taskCategory(t.id) === category.value,
  ),
);
const valid = computed(() =>
  rows.value.filter((r) => r.mode === "TRAINING" && r.result?.valid),
);
const chart = computed(() =>
  comparable(
    rows.value,
    filter.value,
    filter.value === "schulte" ? 1 : analysisLevel.value,
    analysisMode.value,
    ["schulte", "cpt"].includes(filter.value) ? searchCondition.value : "",
  ),
);
function countPending() {
  pending.value = Object.keys(localStorage).filter((k) =>
    k.startsWith("cf-pending-"),
  ).length;
}
async function load() {
  loading.value = true;
  error.value = "";
  try {
    await identity();
    const capabilities = await api<{ protocol: string; tasks: string[] }>(
      "/capabilities",
    );
    if (
      capabilities.protocol !== "2026-10-expansion-1" ||
      !tasks.every((t) => capabilities.tasks?.includes(t.id))
    )
      throw new Error("训练服务版本尚未更新，请重启当前项目后端后重新连接。");
    rows.value = await api<Row[]>("/sessions");
    refreshDay();
    daily.value = readPlan(
      day.value,
      tasks.map((t) => t.id),
    );
    ready.value = true;
    countPending();
  } catch (e) {
    ready.value = false;
    error.value =
      "连接训练服务失败，请确认当前项目后端已更新并重启：" +
      (e as Error).message;
  } finally {
    loading.value = false;
  }
}
async function retry() {
  loading.value = true;
  try {
    for (const key of Object.keys(localStorage).filter((k) =>
      k.startsWith("cf-pending-"),
    )) {
      await api(
        "/sessions/" + key.slice(11) + "/result",
        "PUT",
        JSON.parse(localStorage.getItem(key)!),
      );
      localStorage.removeItem(key);
    }
    await load();
  } catch (e) {
    error.value = (e as Error).message;
  } finally {
    loading.value = false;
    countPending();
  }
}
async function saveSelectedReflection() {
  if (!selected.value) return;
  try {
    await api("/sessions/" + selected.value.id + "/reflection", "PATCH", {
      reflection: selected.value.reflection ?? "",
    });
    notice.value = "复盘已保存";
    await load();
  } catch (e) {
    error.value = (e as Error).message;
  }
}
async function detail(row: Row) {
  selected.value = row;
  details.value = "";
  try {
    details.value = JSON.stringify(await api("/sessions/" + row.id), null, 2);
  } catch (e) {
    error.value = (e as Error).message;
  }
}
async function exportData() {
  try {
    const data = await Promise.all(
      rows.value.map(async (row) => ({
        ...row,
        detail: await api("/sessions/" + row.id),
      })),
    );
    const url = URL.createObjectURL(
      new Blob([JSON.stringify(data, null, 2)], { type: "application/json" }),
    );
    const a = document.createElement("a");
    a.href = url;
    a.download = "cogniforge-records.json";
    a.click();
    URL.revokeObjectURL(url);
  } catch (e) {
    error.value = (e as Error).message;
  }
}
async function remove() {
  loading.value = true;
  try {
    await api("/sessions", "DELETE");
    for (const k of Object.keys(localStorage).filter((k) =>
      k.startsWith("cf-pending-"),
    ))
      localStorage.removeItem(k);
    for (const key of Object.keys(localStorage).filter((k) =>
      k.startsWith(
        "cf-daily-" + (localStorage.getItem("cf-token") ?? "guest") + "-",
      ),
    ))
      localStorage.removeItem(key);
    localStorage.removeItem("cf-strategy");
    strategy.value = "";
    selected.value = undefined;
    confirmDelete.value = false;
    await load();
  } catch (e) {
    error.value = (e as Error).message;
  } finally {
    loading.value = false;
  }
}
function openTask(id: string) {
  returnPage.value = page.value;
  activeSlot.value = null;
  level.value = id === "schulte" ? 1 : (taskLevels.value[id] ?? 1);
  active.value = id;
  error.value = "";
  void focusContent();
}
function saveStrategy() {
  localStorage.setItem("cf-strategy", strategy.value);
  notice.value = "已保存到本机浏览器";
}
onMounted(load);
</script>
<template>
  <div class="app-shell">
    <aside>
      <a class="brand" href="#" @click.prevent="navigate('今日')"
        ><span class="brand-icon">✳</span
        ><span>CogniForge<small>认知练习室</small></span></a
      >
      <div class="nav-label">你的练习空间</div>
      <nav>
        <button
          v-for="(item, i) in [
            '今日',
            '训练库',
            '训练记录',
            '分析复盘',
            '我的',
          ]"
          :key="item"
          :class="{ active: page === item }"
          :aria-current="page === item ? 'page' : undefined"
          :disabled="!!active"
          @click="navigate(item)"
        >
          {{ ["◷", "▦", "☷", "⌁", "○"][i] }} <span>{{ item }}</span>
        </button>
      </nav>
      <div class="sidebar-note">
        <span>一点练习，一点发现。</span>
        <p>关注自己的过程，<br />不必与别人比较。</p>
      </div>
    </aside>
    <main
      ref="content"
      tabindex="-1"
      :aria-label="active ? '训练说明与工作台' : page"
    >
      <header>
        <span>个人认知训练 / {{ active ? "训练工作台" : page }}</span
        ><span class="status"
          ><i :class="{ offline: !ready }"></i
          >{{
            ready ? "本机个人档案" : loading ? "连接服务中" : "服务未连接"
          }}</span
        >
      </header>
      <p v-if="error" class="error" role="alert">
        {{ error }} <button @click="load">重新连接</button>
      </p>
      <p v-if="pending && !active" class="banner">
        有 {{ pending }} 条记录待同步。<button
          :disabled="loading"
          @click="retry"
        >
          同步记录
        </button>
      </p>
      <Training
        v-if="active"
        :task="active"
        :level="level"
        :mode="mode"
        @close="closeTask"
        :connected="ready"
        @update:level="setTaskLevel"
        @saved="load"
        @started="started"
        :return-label="returnPage === '今日' ? '返回今日计划' : '返回训练库'"
      />
      <template v-else>
        <section v-if="page === '今日'" class="daily-page">
          <div class="section-head">
            <div>
              <small>{{ day }} · 按日轮换，可自行调整</small>
              <h1>今天，专注做好三件事。</h1>
              <p>
                热身、主要练习、记忆策略。默认从低档位开始，时长随参数和作答速度变化。
              </p>
            </div>
          </div>
          <div class="panel daily-plan">
            <div class="section-head">
              <h2>今日计划</h2>
              <strong>已完成 {{ completed }} / 3 · 跳过 {{ skipped }}</strong>
            </div>
            <progress
              :value="completed"
              max="3"
              aria-label="今日计划完成进度"
            ></progress>
            <div class="controls">
              <label
                >训练模式<AppSelect v-model="mode">
                  <option v-for="(label, key) in modes" :value="key">
                    {{ label }}
                  </option>
                </AppSelect></label
              ><span
                >初次使用建议先练习规则；完成练习也计入计划，但不会混入正式趋势。</span
              >
            </div>
            <ol class="daily-list">
              <li v-for="(item, i) in daily" :key="i" class="panel">
                <div class="section-head">
                  <div class="daily-heading">
                    <span class="daily-kicker"
                      >0{{ i + 1 }} /
                      {{ ["热身", "主要练习", "记忆策略"][i] }}</span
                    >
                    <h3>{{ names[item.task] }}</h3>
                  </div>
                  <span class="daily-state">{{ states[i] }}</span>
                </div>
                <p>{{ tasks.find((t) => t.id === item.task)?.time }}</p>
                <template v-if="states[i] !== '已完成'">
                  <div class="daily-actions">
                    <label class="daily-task-select"
                      >调整任务<AppSelect
                        :aria-label="'调整第' + (i + 1) + '项'"
                        :value="item.task"
                        @change="replaceDaily(i, $event)"
                      >
                        <option v-for="task in tasks" :value="task.id">
                          {{ names[task.id] }}
                        </option>
                      </AppSelect></label
                    >
                    <button
                      class="primary"
                      :disabled="!ready"
                      @click="startDaily(i)"
                    >
                      {{ item.sessionId ? "重新开始" : "开始这项" }}
                    </button>
                    <button
                      class="quiet"
                      @click="
                        item.skipped = !item.skipped;
                        persistPlan();
                      "
                    >
                      {{ item.skipped ? "恢复计划" : "今天跳过" }}
                    </button>
                  </div>
                  <p v-if="item.sessionId" class="muted">
                    上次未形成完整有效记录，重新开始会创建新记录。
                  </p> </template
                ><span v-else>已完成本项，无需重复刷分。</span>
              </li>
            </ol>
            <div class="daily-footer">
              <div class="daily-footer-actions">
                <button
                  v-if="nextSlot >= 0"
                  class="primary"
                  :disabled="!ready"
                  @click="startDaily(nextSlot)"
                >
                  {{ completed ? "继续今日计划" : "开始今天的练习" }}
                </button>
                <p v-else>
                  {{
                    completed === 3
                      ? "今日计划已完成，写下一条发现就可以休息了。"
                      : "今日安排已结束，跳过的项目不计为完成。"
                  }}
                </p>
                <button class="quiet" @click="navigate('训练库')">
                  浏览全部训练 ↗
                </button>
              </div>
              <p class="muted">
                计划仅保存在此浏览器；训练结果保存在后端。自由训练不会自动顶替计划项目。
              </p>
            </div>
          </div>
          <section class="panel">
            <h2>今日复盘</h2>
            <p>
              今天已保存 {{ todayRows.length }} 条记录，{{
                needsReflection.length
              }}
              条完整记录尚未填写复盘。
            </p>
            <button
              v-for="row in needsReflection.slice(0, 3)"
              :key="row.id"
              class="record"
              @click="reviewToday(row)"
            >
              {{ names[row.task] }} · 查看并复盘 ↗
            </button>
            <label
              >把方法带回生活<textarea
                v-model="strategy"
                maxlength="1000"
                placeholder="今天用了什么方法？准备在哪个生活场景试一次？"
              /></label
            ><button class="quiet" @click="saveStrategy">保存策略笔记</button
            ><small aria-live="polite">{{ notice }}</small>
          </section>
        </section>
        <template v-if="page === '训练库'"
          ><div class="section-head">
            <div>
              <small>每项练习都有自己的意义</small>
              <h2>训练库</h2>
            </div>
            <span class="muted">{{ tasks.length }} 个入口 · 自主选择</span>
          </div>
          <div class="controls panel">
            <label
              >模式<AppSelect v-model="mode">
                <option v-for="(label, key) in modes" :value="key">
                  {{ label }}
                </option>
              </AppSelect></label
            >
            <p>
              先选择任务，再设置具体参数。初次使用请选规则练习；正式训练与固定复测分别记录。
            </p>
            <p v-if="page === '训练库'" class="muted">
              现已开放原有四项及颜色冲突、规则切换、持续注意、数字正背/倒背、组块与位置记忆法。正背/倒背和两种策略分别记录，不代表同等数量的独立认知能力。
            </p>
          </div>
          <label class="controls"
            >训练分类<AppSelect v-model="category">
              <option
                v-for="c in [
                  '全部',
                  '注意与搜索',
                  '记忆',
                  '抑制与灵活性',
                  '记忆策略',
                ]"
              >
                {{ c }}
              </option>
            </AppSelect></label
          >
          <div class="task-grid">
            <button
              v-for="task in libraryTasks"
              :key="task.id"
              class="task-card"
              @click="openTask(task.id)"
            >
              <span class="task-icon" :class="task.color">{{ task.icon }}</span
              ><span class="tag">{{ task.tag }}</span>
              <h3>{{ names[task.id] }}</h3>
              <p>{{ task.description }}</p>
              <div class="card-bottom">
                <small>{{ task.time }}</small
                ><span>查看规则与参数 ↗</span>
              </div>
            </button>
          </div>
        </template>
        <template v-if="page === '训练记录'"
          ><div class="section-head">
            <div>
              <small>每一次尝试，都值得记录</small>
              <h1>训练记录</h1>
            </div>
            <button class="quiet" @click="exportData">导出全部数据 ↓</button>
          </div>
          <div class="panel">
            <p v-if="!rows.length" class="empty">
              还没有训练记录。从一次规则练习开始吧。
            </p>
            <button
              v-for="row in rows"
              :key="row.id"
              class="record"
              @click="detail(row)"
            >
              <div>
                <strong>{{ names[row.task] }}</strong
                ><small
                  >{{ new Date(row.createdAt).toLocaleString() }} ·
                  {{ modes[row.mode] }} ·
                  {{
                    row.task === "schulte"
                      ? searchLabel(row.searchConfig)
                      : row.task === "cpt"
                        ? cptLabel(row.cptConfig) + " · 档位 " + row.level
                        : "档位 " + row.level
                  }}</small
                >
              </div>
              <span>{{
                row.result?.valid
                  ? percent(row.result.accuracy)
                  : row.status === "RUNNING"
                    ? "未完成"
                    : "已中断"
              }}</span
              ><span>查看 ↗</span>
            </button>
          </div>
          <section v-if="selected" class="panel detail">
            <h2>{{ names[selected.task] }} · 记录详情</h2>
            <p>
              {{
                selected.result?.advice ||
                "训练尚未提交，可同步本机待保存数据。"
              }}
            </p>
            <div v-if="selected.result" class="metrics">
              <div
                v-for="metric in resultMetrics(selected.task, selected.result)"
                :key="metric.label"
              >
                <strong>{{ metric.value }}</strong
                ><span>{{ metric.label }}</span>
              </div>
            </div>
            <CptReport v-if="selected.result" :result="selected.result" />
            <ul
              v-if="
                selected.result?.breakdown && !selected.result.breakdown['全程']
              "
            >
              <li
                v-for="(stats, label) in selected.result.breakdown"
                :key="label"
              >
                {{ label }}：正确 {{ stats.correct }} /
                {{ stats.n }}；正确时间中位数
                {{
                  stats.medianRt == null
                    ? "—"
                    : Math.round(stats.medianRt) + " ms"
                }}<span v-if="stats.misses != null"
                  >；遗漏 {{ stats.misses }}，误报 {{ stats.falseAlarms }}</span
                ><span v-if="stats.positionAccuracy != null"
                  >；位置正确
                  {{ Math.round(stats.positionAccuracy * 100) }}%</span
                >
              </li>
            </ul>
            <label
              >训练复盘<textarea
                v-model="selected.reflection"
                maxlength="1000"
                placeholder="哪一步容易出错？下次准备怎样调整？"
              /></label
            ><button @click="saveSelectedReflection">保存训练复盘</button
            ><small>{{ notice }}</small>
            <details>
              <summary>查看配置与逐试次数据</summary>
              <pre>{{ details }}</pre>
            </details>
          </section></template
        >
        <template v-if="page === '分析复盘'"
          ><small>看见变化，也理解变化</small>
          <h1>分析与复盘</h1>
          <div class="controls panel">
            <label
              >任务<AppSelect v-model="filter">
                <option v-for="(name, key) in names" :value="key">
                  {{ name }}
                </option>
              </AppSelect></label
            ><label
              >难度<AppSelect
                v-model.number="analysisLevel"
                :disabled="filter === 'schulte'"
              >
                <option
                  v-for="n in ['digits', 'reverse'].includes(filter) ? 4 : 3"
                  :value="n"
                >
                  {{
                    ["digits", "reverse"].includes(filter)
                      ? `${n + 2} 位`
                      : `档位 ${n}`
                  }}
                </option>
              </AppSelect></label
            ><label
              >模式<AppSelect v-model="analysisMode">
                <option v-for="(name, key) in modes" :value="key">
                  {{ name }}
                </option>
              </AppSelect></label
            >
          </div>
          <label
            v-if="['schulte', 'cpt'].includes(filter)"
            class="controls panel"
            >训练条件
            <AppSelect v-model="searchCondition">
              <option value="">最近一次有效记录的条件</option>
              <option
                v-for="option in searchConditions"
                :key="option.value"
                :value="option.value"
              >
                {{ option.label }}
              </option>
            </AppSelect>
          </label>
          <section class="panel">
            <div class="section-head">
              <h2>同条件表现</h2>
              <span>正确率 · 最近 12 次</span>
            </div>
            <p class="muted">
              仅展示所选任务、档位、训练模式下，与最近有效记录的设备、版本、网格、字符类型、数量和作答模式相同的数据；中断记录排除。顺序寻踪使用档位
              1，具体难度以配置为准。
            </p>
            <p v-if="filter === 'schulte' && chart.length">
              本组：{{ searchLabel(chart[chart.length - 1].searchConfig) }}
            </p>
            <p v-if="filter === 'cpt' && chart.length">
              本组：{{ cptLabel(chart[chart.length - 1].cptConfig) }} ·
              正式版本和时长分别比较
            </p>
            <div v-if="chart.length" class="chart">
              <div v-for="r in chart" :key="r.id" class="bar-wrap">
                <span>{{ percent(r.result?.accuracy) }}</span>
                <div
                  class="bar"
                  :style="{
                    height: Math.max(2, (r.result?.accuracy || 0) * 150) + 'px',
                  }"
                ></div>
                <small>{{ new Date(r.createdAt).toLocaleDateString() }}</small>
              </div>
            </div>
            <p v-else class="empty">
              暂无可比较的有效记录。完成同一配置的练习后，在这里查看趋势。
            </p>
            <div class="table-scroll">
              <table v-if="chart.length">
                <thead>
                  <tr>
                    <th>时间</th>
                    <th>正确率</th>
                    <th>命中率</th>
                    <th>虚报率</th>
                    <th>正确响应中位数</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="r in chart" :key="r.id">
                    <td>{{ new Date(r.createdAt).toLocaleString() }}</td>
                    <td>{{ percent(r.result?.accuracy) }}</td>
                    <td>{{ percent(r.result?.hitRate) }}</td>
                    <td>{{ percent(r.result?.falseAlarmRate) }}</td>
                    <td>
                      {{
                        r.result?.medianRt == null
                          ? "—"
                          : Math.round(r.result.medianRt) + " ms"
                      }}
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
            <p class="callout">
              速度变快时，也看看错误是否增加。这里的变化反映任务表现，不代表智力或日常能力的变化。
            </p>
          </section></template
        >
        <section v-if="page === '我的'" class="panel settings">
          <small>你的记录，由你掌握</small>
          <h1>本机个人档案</h1>
          <p>
            当前以浏览器中保存的随机凭证识别个人数据，不需要填写个人信息。清除浏览器数据会失去访问凭证；跨设备账号与凭证恢复尚未开放。
          </p>
          <p>
            建议定期导出训练记录。策略笔记目前仅保存在本机，训练试次和训练复盘保存到后端数据库。
          </p>
          <button class="primary" @click="exportData">导出训练数据</button
          ><button class="quiet danger" @click="confirmDelete = true">
            删除训练记录
          </button>
          <div v-if="confirmDelete" class="callout">
            <p>
              将删除此档案的全部训练记录、待同步记录和本机策略笔记，无法撤销。
            </p>
            <button class="danger" :disabled="loading" @click="remove">
              确认删除全部记录</button
            ><button class="quiet" @click="confirmDelete = false">取消</button>
          </div>
        </section>
      </template>
      <footer>
        CogniForge · 练习与自我观察工具，不提供医疗诊断或智力测评。
      </footer>
    </main>
  </div>
</template>
