import type { Row } from "./api";
export interface DailyItem {
  task: string;
  skipped: boolean;
  sessionId?: string;
}
export function localDay(date = new Date()) {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;
}
export function dailyKey(day: string) {
  return (
    "cf-daily-" + (localStorage.getItem("cf-token") ?? "guest") + "-" + day
  );
}
export function defaultPlan(day: string): DailyItem[] {
  const seed = Number(day.replaceAll("-", ""));
  return [
    "schulte",
    ["switch", "nback", "gonogo", "stroop"][seed % 4],
    ["chunk", "loci"][seed % 2],
  ].map((task) => ({ task, skipped: false }));
}
export function readPlan(day: string, allowed: string[]): DailyItem[] {
  try {
    const value = JSON.parse(localStorage.getItem(dailyKey(day)) ?? "null");
    if (
      Array.isArray(value) &&
      value.length === 3 &&
      value.every(
        (x) =>
          allowed.includes(x.task) &&
          typeof x.skipped === "boolean" &&
          (x.sessionId === undefined || typeof x.sessionId === "string"),
      )
    )
      return value;
  } catch {}
  return defaultPlan(day);
}
export function itemState(item: DailyItem, rows: Row[]) {
  const row = rows.find((r) => r.id === item.sessionId);
  if (row?.status === "COMPLETED" && row.result?.valid) return "已完成";
  if (item.skipped) return "已跳过";
  return item.sessionId ? "需重新开始" : "待训练";
}
