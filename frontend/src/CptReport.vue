<script setup lang="ts">
import type { Result } from "./api";
defineProps<{ result: Result }>();
</script>
<template>
  <section v-if="result.breakdown?.['全程']" class="cpt-report">
    <h3>本次守望复盘</h3>
    <div class="table-scroll">
      <table>
        <thead>
          <tr>
            <th>时段</th>
            <th>目标命中</th>
            <th>遗漏</th>
            <th>非目标误按</th>
            <th>正确时间中位数</th>
            <th>时间波动（标准差）</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(stats, label) in result.breakdown" :key="label">
            <td>{{ label }}</td>
            <td>{{ stats.hits }} / {{ stats.targets }}</td>
            <td>{{ stats.misses }} / {{ stats.targets }}</td>
            <td>{{ stats.falseAlarms }} / {{ stats.nonTargets }}</td>
            <td>
              {{
                stats.medianRt == null
                  ? "不足5个正确响应"
                  : Math.round(stats.medianRt) + " ms"
              }}
            </td>
            <td>
              {{
                stats.rtSd == null ? "样本不足" : Math.round(stats.rtSd) + " ms"
              }}
            </td>
          </tr>
        </tbody>
      </table>
    </div>
    <p>
      无线索 X 误按 {{ result.breakdown["全程"].lureFalseAlarms }} /
      {{ result.breakdown["全程"].lureOpportunities }} 次机会；A 提前按
      {{ result.breakdown["全程"].premature }} /
      {{ result.breakdown["全程"].cueOpportunities }} 次机会；其他误按
      {{ result.breakdown["全程"].otherFalseAlarms }} 次。
    </p>
    <p class="muted">
      没有相应机会时不解释该项；基础玩法不要求等待 A
      的后续。每半段样本较少，仅供复盘，不生成专注力等级。中断记录的后半段可能尚未发生。
    </p>
    <p>
      下次建议：{{
        !result.valid
          ? "重新完成同条件练习。"
          : (result.falseAlarms ?? 0) > 0
            ? "先保持档位，练清楚何时不该按。"
            : (result.targets ?? 0) > (result.hits ?? 0)
              ? "保持档位，优先减少遗漏。"
              : "本次没有遗漏或误按，可在多次同条件表现稳定后手动提高一档。"
      }}
    </p>
  </section>
</template>
