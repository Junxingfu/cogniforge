export interface SearchConfig {
  gridSize: number;
  symbols: "NUMBER" | "LETTER";
  count: number;
  visibility: "VISIBLE" | "HIDDEN";
}
export function searchLabel(c?: SearchConfig | null) {
  return c
    ? `${c.gridSize}×${c.gridSize} · ${c.count} 个${c.symbols === "NUMBER" ? "数字" : "字母"} · ${c.visibility === "VISIBLE" ? "可见搜索" : "隐藏回忆"}`
    : "旧版固定 4×4";
}
export interface CptConfig {
  variant: "BASIC" | "CUED";
  durationSeconds: number;
}
export function cptLabel(config?: CptConfig | null) {
  return config
    ? `${config.variant === "CUED" ? "线索守望" : "基础守望"} · ${config.durationSeconds} 秒`
    : "旧版持续守望";
}
export interface Plan {
  cptConfig?: CptConfig;
  gapMs?: number;
  searchConfig?: SearchConfig;
  task: string;
  level: number;
  version: string;
  windowMs: number;
  stimuli: string[];
  expected: string[];
}
export interface Trial {
  index: number;
  response: string;
  rtMs: number | null;
  onsetMs: number;
}
export interface Submission {
  observationMs?: number;
  status: string;
  events: Trial[];
  quality: string;
  reflection: string;
}
export interface Result {
  breakdown?: Record<string, Record<string, number | null>>;
  observationMs?: number;
  totalResponseMs?: number;
  errorClicks?: number;
  targets?: number;
  hits?: number;
  falseAlarms?: number;
  nonTargets?: number;
  trials: number;
  correct: number;
  accuracy: number | null;
  hitRate: number | null;
  falseAlarmRate: number | null;
  medianRt: number | null;
  advice: string;
  valid: boolean;
}
export interface Row {
  cptConfig?: CptConfig;
  version?: string;
  searchConfig?: SearchConfig;
  id: string;
  task: string;
  level: number;
  mode: string;
  status: string;
  createdAt: string;
  device: string;
  reflection: string | null;
  result: Result | null;
}
export async function api<T>(
  path: string,
  method = "GET",
  body?: unknown,
): Promise<T> {
  const token = localStorage.getItem("cf-token");
  const res = await fetch("/api/v1" + path, {
    method,
    headers: {
      "Content-Type": "application/json",
      ...(token ? { Authorization: "Bearer " + token } : {}),
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  if (!res.ok) {
    let message = "服务暂不可用（" + res.status + "）";
    try {
      message = (await res.json()).message || message;
    } catch {}
    throw new Error(message);
  }
  const text = await res.text();
  return text ? JSON.parse(text) : undefined;
}
export async function identity() {
  if (!localStorage.getItem("cf-token")) {
    const data = await api<{ token: string }>("/identity", "POST");
    localStorage.setItem("cf-token", data.token);
  }
}
import { expanded } from "./expansion";
export const names: Record<string, string> = {
  ...Object.fromEntries(expanded.map((t) => [t.id, t.name])),
  nback: "更新挑战",
  gonogo: "停走信号",
  corsi: "空间路径",
  schulte: "顺序寻踪",
};
export const modes: Record<string, string> = {
  PRACTICE: "规则练习",
  TRAINING: "正式训练",
  RETEST: "固定复测",
};
export const percent = (n: number | null | undefined) =>
  n == null ? "—" : Math.round(n * 100) + "%";
