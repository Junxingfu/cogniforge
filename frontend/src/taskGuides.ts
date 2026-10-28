import { percent, type Result } from "./api";
import { expansionGuide, recallTasks } from "./expansion";
export function taskGuide(task: string, level: number) {
  const extra = expansionGuide(task, level);
  if (extra) return extra;
  const guides: Record<
    string,
    {
      parameter: string;
      options: string[];
      goal: string;
      example: string;
      dose: string;
      focus: string;
    }
  > = {
    nback: {
      parameter: "回看距离",
      options: [
        "1-back · 回看前 1 项",
        "2-back · 回看前 2 项",
        "3-back · 回看前 3 项",
      ],
      goal: "在变化的信息中，持续记住并更新最近的字母。",
      example: [
        "A → A：第二个 A 按空格。A → B：第二项不按。",
        "A → B → A：第三项按空格；与两步前的字母比较，不是前一项。",
        "A → B → C → A：第四项按空格；与三步前的字母比较。",
      ][level - 1],
      dose: `${30 + level} 项，其中前 ${level} 项只观察 · 30 个评分项 / 9 个目标 · 约 ${Math.ceil((30 + level) * 3)} 秒`,
      focus: "先减少漏报与虚报，再看速度。全不按并不代表表现好。",
    },
    gonogo: {
      parameter: "作答窗口",
      options: ["1000 ms · 较宽裕", "850 ms · 中等", "700 ms · 较紧凑"],
      goal: "练习按规则作答，并在不该行动时抑制按键。",
      example: "GO → 按一次空格；STOP → 不按。看到下一项才开始新的判断。",
      dose: `40 项：30 个 GO、10 个 STOP · 每项间隔 1500 ms · 约 60 秒`,
      focus: "同时看 GO 漏报与 STOP 误按；只追求快容易增加误按。",
    },
    corsi: {
      parameter: "序列长度",
      options: ["3 个位置", "4 个位置", "5 个位置"],
      goal: "记住亮起位置的先后顺序，再完整重现。",
      example:
        "左上 → 中心 → 右下：等提示作答后，按这个顺序点击；观察时不用按。",
      dose: `5 轮 × ${level + 2} 个位置 · 每轮最多 15 秒作答 · 大约 15–100 秒`,
      focus: "优先看整条序列正确几轮；输入更快不代表记得更好。",
    },
    schulte: {
      parameter: "网格规格",
      options: ["4 × 4 · 16 个数字"],
      goal: "可见搜索练习按序寻找；隐藏回忆练习字符与位置的对应记忆。",
      example:
        "数字按 1 → 2 → 3，字母按 A → B → C。误点不会推进；隐藏模式不会揭晓点击位置的字符。",
      dose: "4×4 或 5×5 · 2–25 个字符（不超过格子数）· 观察后自行开始 · 超时中断",
      focus:
        "当前报告显示无误目标数，记录每项目标的误点。它不是注意力能力总分。",
    },
  };
  return guides[task];
}
export function resultMetrics(task: string, result: Result) {
  if (task === "nback" || task === "gonogo" || task === "cpt")
    return [
      {
        label: task !== "gonogo" ? "目标命中率" : "GO 命中率",
        value: percent(result.hitRate),
      },
      {
        label: task !== "gonogo" ? "非目标虚报率" : "STOP 误按率",
        value: percent(result.falseAlarmRate),
      },
      {
        label: "正确响应中位数",
        value:
          result.medianRt == null ? "—" : Math.round(result.medianRt) + " ms",
      },
    ];
  if (task === "stroop" || task === "switch") {
    const a = result.breakdown?.[task === "stroop" ? "匹配" : "重复"];
    const b = result.breakdown?.[task === "stroop" ? "冲突" : "切换"];
    return [
      { label: "正确率", value: percent(result.accuracy) },
      {
        label: task === "stroop" ? "冲突－匹配时间差" : "切换－重复时间差",
        value:
          a?.medianRt != null &&
          b?.medianRt != null &&
          (a.correctResponses ?? 0) >= 5 &&
          (b.correctResponses ?? 0) >= 5
            ? Math.round(b.medianRt - a.medianRt) + " ms"
            : "有效样本不足",
      },
    ];
  }
  if (recallTasks.includes(task))
    return [
      {
        label: "完整正确 / 总轮数",
        value: `${result.correct} / ${result.trials}`,
      },
      {
        label: "正确轮输入时间中位数",
        value:
          result.medianRt == null
            ? "—"
            : (result.medianRt / 1000).toFixed(2) + " 秒",
      },
    ];
  return [
    {
      label:
        task === "corsi" ? "完整正确序列 / 总轮数" : "无误找到 / 已处理目标",
      value: `${result.correct} / ${result.trials}`,
    },
    {
      label:
        task === "corsi" ? "正确序列输入时间中位数" : "无误目标查找时间中位数",
      value:
        result.medianRt == null
          ? "—"
          : (result.medianRt / 1000).toFixed(2) + " 秒",
    },
  ];
}
