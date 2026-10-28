import type { CptConfig, Plan } from "./api";
export function cptGuide(level: number, config: CptConfig, practice: boolean) {
  const period = [1500, 1200, 1000][level - 1];
  return {
    parameter: "节奏与干扰",
    options: ["入门 · 每项1.5秒", "标准 · 每项1.2秒", "挑战 · 每项1秒"],
    goal:
      config.variant === "CUED"
        ? "记住前一项，仅对紧邻 A 之后的 X 作答。"
        : "持续观察，仅对 X 作答。",
    example:
      config.variant === "CUED"
        ? "A → X：在 X 时按；B → X、A → B、A → B → X：均不按。看到 A 时先等待。"
        : "X：按一次；其他字母：不按。",
    dose: `${practice ? 30 : config.durationSeconds}秒 · ${((practice ? 30 : config.durationSeconds) * 1000) / period}项 · ${practice ? "规则练习有答案反馈，不计正式趋势" : "一轮固定参数，结束后统一反馈"}`,
    focus: "同时看遗漏、误报和前后段表现。短练不能用于推断长时间专注能力。",
  };
}
export function cptFeedback(plan: Plan, index: number, response: string) {
  const expected = plan.expected[index];
  if (expected === "PRESS")
    return response ? "正确，目标已命中" : "漏掉目标：这次应当按键";
  if (!response) return "正确，保持等待";
  if (plan.cptConfig?.variant === "CUED" && plan.stimuli[index] === "X")
    return "误按：X 前一项不是 A";
  if (plan.cptConfig?.variant === "CUED" && plan.stimuli[index] === "A")
    return "提前按了：看到 A 要等紧邻的 X";
  return "误按：这不是目标";
}
