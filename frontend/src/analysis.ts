import type { Row } from "./api";
export const conditionKey = (r: Row) =>
  JSON.stringify([
    r.version ?? "1.0",
    r.searchConfig?.gridSize,
    r.searchConfig?.symbols,
    r.searchConfig?.count,
    r.searchConfig?.visibility,
    r.cptConfig?.variant,
    r.cptConfig?.durationSeconds,
  ]);
/** Compare only valid records from the latest valid record's device. */
export function comparable(
  rows: Row[],
  task: string,
  level: number,
  mode: string,
  conditionFilter = "",
): Row[] {
  const matches = rows.filter(
    (r) =>
      r.task === task &&
      r.level === level &&
      r.mode === mode &&
      r.result?.valid &&
      (!conditionFilter || conditionKey(r) === conditionFilter),
  );
  const device = matches[0]?.device;
  const condition = matches[0] ? conditionKey(matches[0]) : "";
  return matches
    .filter((r) => r.device === device && conditionKey(r) === condition)
    .slice(0, 12)
    .reverse();
}
