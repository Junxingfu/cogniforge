export const expanded = [
  {
    id: "stroop",
    name: "颜色冲突",
    tag: "冲突控制",
    description: "忽略字义，选择文字的墨水颜色。",
    time: "40 项 · 约 2 分钟",
    icon: "◑",
    color: "peach",
  },
  {
    id: "switch",
    name: "规则切换",
    tag: "认知灵活性",
    description: "跟随线索，在大小与奇偶规则间切换。",
    time: "40 个评分项 · 约 2 分钟",
    icon: "⇄",
    color: "mint",
  },
  {
    id: "cpt",
    name: "持续守望",
    tag: "持续注意",
    description: "跟踪线索，只在 A 后紧接 X 时作答；也可选基础玩法。",
    time: "短练90秒 / 标准3分钟 · 规则练习约30秒",
    icon: "◎",
    color: "sand",
  },
  {
    id: "digits",
    name: "数字正背",
    tag: "短时记忆",
    description: "逐个观察数字，按原顺序复述。",
    time: "5 轮 · 3–6 位",
    icon: "123",
    color: "lavender",
  },
  {
    id: "reverse",
    name: "数字倒背",
    tag: "工作记忆操作",
    description: "逐个观察数字，再倒序复述。",
    time: "5 轮 · 3–6 位",
    icon: "321",
    color: "lavender",
  },
  {
    id: "chunk",
    name: "组块练习",
    tag: "记忆策略",
    description: "把数字分成小组，再完整回忆。",
    time: "3 轮 · 6–12 位",
    icon: "▥",
    color: "sand",
  },
  {
    id: "loci",
    name: "位置记忆法",
    tag: "记忆策略",
    description: "把词语放在熟悉路线中，按路线回忆。",
    time: "3 轮 · 4–8 个词",
    icon: "⌂",
    color: "mint",
  },
];
export const recallTasks = ["digits", "reverse", "chunk", "loci"];
export const locations = [
  "门口",
  "鞋柜",
  "走廊",
  "沙发",
  "茶几",
  "窗边",
  "餐桌",
  "厨房",
];
export const ink: Record<string, string> = {
  红: "#ff5252",
  蓝: "#5796ff",
  绿: "#38cb75",
  黄: "#ffdf00",
};
export function expansionGuide(task: string, level: number) {
  const item = expanded.find((x) => x.id === task);
  if (!item) return undefined;
  const examples: Record<string, string> = {
    stroop:
      "蓝色墨水写的“红” → 选择蓝。点击对应汉字，也可按 1红、2蓝、3绿、4黄。位置固定；请先确认能辨认四种颜色。",
    switch:
      "大小：小于5选左，大于5选右；奇偶：奇数选左，偶数选右。例如奇偶规则的8选右，大小规则的3选左。第一项只熟悉操作。",
    cpt: "线索守望：A 后紧接 X 时才按；可切换为仅对 X 作答的基础守望。",
    digits: "观察 3、8、1；隐藏后输入 381，再提交。",
    reverse: "观察 3、8、1；隐藏后输入 183，再提交。",
    chunk:
      "把 381729 想成 381 / 729 两组；闭眼默念每组，再把两组连起来。隐藏后输入完整数字。",
    loci: "先在脑中走一遍门口→鞋柜→走廊→沙发等路线。想象门口堆满苹果、鞋柜撑着雨伞；每个位置形成一个鲜明互动。隐藏后按路线回忆词语，用空格分隔。",
  };
  const span = task === "digits" || task === "reverse";
  return {
    parameter:
      task === "cpt"
        ? "节奏与干扰"
        : span
          ? "序列长度"
          : recallTasks.includes(task)
            ? "材料数量"
            : "作答窗口",
    options:
      task === "cpt"
        ? ["入门 · 每项1.5秒", "标准 · 每项1.2秒", "挑战 · 每项1秒"]
        : span
          ? ["3 位", "4 位", "5 位", "6 位"]
          : task === "chunk"
            ? ["6 位", "9 位", "12 位"]
            : task === "loci"
              ? ["4 个词", "6 个词", "8 个词"]
              : ["2500 ms", "2000 ms", "1500 ms"],
    goal: item.description,
    example: examples[task],
    dose:
      task === "cpt"
        ? "默认短练90秒，可选3分钟；规则练习约30秒"
        : span
          ? `5轮 × ${level + 2}位，每数字800ms+200ms空白；每轮15秒作答`
          : recallTasks.includes(task)
            ? `3轮，每轮${10 + level * 5}秒编码、60秒回忆`
            : `${task === "switch" ? "1个熟悉项 + " : ""}40个评分项；每项间隔500ms`,
    focus:
      task === "cpt"
        ? "同时看漏报、误报和分段波动；全不按不代表注意力好。"
        : recallTasks.includes(task)
          ? "先看回忆的准确程度；输入速度不等于记忆能力。练习后记录一次生活应用。"
          : "准确率与速度一起看；条件差异只反映本次任务表现。",
  };
}
export function normalizeRecall(task: string, text: string) {
  return task === "loci"
    ? text
        .trim()
        .split(/[，,、\s]+/)
        .filter(Boolean)
        .join(",")
    : text
        .replace(/[，,、\s]/g, "")
        .split("")
        .join(",");
}
