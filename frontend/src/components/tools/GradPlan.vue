<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { loadToolData, type ToolData } from './toolData'

const emit = defineEmits<{ back: [] }>()
const tool = ref<ToolData | null>(null)
const loading = ref(true)
const semesters = ref(2)

const grad = computed(() => tool.value?.grad || null)
const totalGap = computed(() => Number(grad.value?.total?.gap || 0))
const percent = computed(() => Number(grad.value?.total?.percent || 0))
const maxCredit = computed(() => Number(tool.value?.cfg?.maxCreditPerSemester || 0))
const perSem = computed(() => semesters.value > 0 ? Math.ceil(totalGap.value / semesters.value) : 0)
const feasible = computed(() => maxCredit.value <= 0 || perSem.value <= maxCredit.value)
const curSem = computed(() => Number(grad.value?.currentSemesterNo || 1))

/** 按缺口挑课：每个大类只挑到刚好补齐缺口 */
const picks = computed(() => {
  const gaps: Record<string, number> = {}
  for (const c of grad.value?.categories || []) gaps[c.key] = Number(c.gap || 0)
  const out: any[] = []
  for (const c of grad.value?.recommend || []) {
    const g = String(c.group || '')
    if ((gaps[g] || 0) <= 0) continue
    gaps[g] -= Number(c.credit) || 0
    out.push(c)
  }
  return out
})
const covered = computed(() => picks.value.reduce((s, c) => s + (Number(c.credit) || 0), 0))

/** 学分从高到低轮转到当前最空的学期，尽量均衡 */
const buckets = computed(() => {
  const n = semesters.value
  const arr: any[][] = Array.from({ length: n }, () => [])
  const load = Array(n).fill(0)
  const sorted = [...picks.value].sort((a, b) => (Number(b.credit) || 0) - (Number(a.credit) || 0))
  for (const c of sorted) {
    let i = 0
    for (let k = 1; k < n; k++) if (load[k] < load[i]) i = k
    arr[i].push(c); load[i] += Number(c.credit) || 0
  }
  return arr.map((cs, i) => ({ no: curSem.value + i, courses: cs,
    credits: cs.reduce((s, c) => s + (Number(c.credit) || 0), 0) }))
})

const catRows = computed(() => (grad.value?.categories || []).map((c: any) => ({
  key: c.key, gap: Number(c.gap || 0), earned: Number(c.earned || 0), required: Number(c.required || 0),
  perSem: semesters.value > 0 ? (Number(c.gap || 0) / semesters.value).toFixed(1) : '0', done: !!c.done,
})))

onMounted(async () => { tool.value = await loadToolData(); loading.value = false })
</script>

<template>
  <div class="tl">
    <button class="tool-back" @click="emit('back')">← 工具箱</button>
    <div v-if="loading" class="tl-loading">正在读取毕业进度与培养方案…</div>
    <template v-else>
      <div v-if="!grad" class="tl-warn">毕业进度仅对学生账号开放。</div>
      <template v-else>
        <div class="tl-bar">
          <span class="tl-label">剩余学期</span>
          <select v-model.number="semesters" class="tl-select"><option v-for="n in 6" :key="n" :value="n">{{ n }} 学期</option></select>
          <span class="tl-meta">培养方案：{{ grad.requirementLabel || '全校默认要求' }}</span>
        </div>
        <div class="tl-kpis">
          <div class="tl-kpi"><b>{{ totalGap }}</b><span>毕业学分缺口</span></div>
          <div class="tl-kpi"><b>{{ percent }}%</b><span>当前完成度</span></div>
          <div class="tl-kpi"><b>{{ perSem }}</b><span>每学期需修（分）</span></div>
          <div class="tl-kpi" :class="{ bad: !feasible }"><b>{{ maxCredit || '不限' }}</b><span>本学期学分上限</span></div>
        </div>
        <div v-if="!feasible" class="tl-alert err">⚠ 按 {{ semesters }} 学期均摊需每学期修 {{ perSem }} 分，超过系统上限 {{ maxCredit }} 分：需增加学期数，或由教务调整上限。</div>
        <div v-else-if="totalGap > 0" class="tl-alert ok">✓ 均摊后每学期 {{ perSem }} 分，未超过上限 {{ maxCredit }} 分，排期可行。</div>
        <div v-else class="tl-alert ok">✓ 毕业学分要求已全部达标。</div>
        <div v-if="covered < totalGap" class="tl-alert warn">系统推荐课程仅覆盖 {{ covered }} 分，其余 {{ totalGap - covered }} 分缺口需靠必修自动排课、重修或新增选修课补齐。</div>

        <div class="tl-box">
          <div class="tl-box-h">四大类缺口与分摊</div>
          <table class="tl-table">
            <thead><tr><th>毕业大类</th><th>已获 / 要求</th><th>缺口</th><th>每学期目标</th><th>状态</th></tr></thead>
            <tbody>
              <tr v-for="c in catRows" :key="c.key">
                <td><b>{{ c.key }}</b></td><td class="num">{{ c.earned }} / {{ c.required }}</td>
                <td class="num">{{ c.gap }}</td><td class="num">{{ c.perSem }}</td>
                <td class="num"><span :class="c.done ? 'ok-text' : 'warn-text'">{{ c.done ? '已达标' : '未达标' }}</span></td>
              </tr>
            </tbody>
          </table>
        </div>

        <div class="tl-box" style="margin-top:12px">
          <div class="tl-box-h">按学期排课建议（来自系统推荐课程）</div>
          <div v-if="!picks.length" class="tl-empty">当前没有需要补修的课程，或推荐清单为空。</div>
          <div class="tl-sems">
            <div v-for="b in buckets" :key="b.no" class="tl-sem">
              <div class="tl-sem-h"><b>第 {{ b.no }} 学期</b><span>{{ b.credits }} 分 · {{ b.courses.length }} 门</span></div>
              <div v-if="!b.courses.length" class="tl-sub">本轮无需补修</div>
              <div v-for="c in b.courses" :key="c.courseId" class="tl-course">
                <span class="tl-course-name">《{{ c.name }}》</span>
                <span class="tl-sub">{{ c.credit }} 学分 · {{ c.group }} · {{ c.time || '时间待定' }}</span>
              </div>
            </div>
          </div>
        </div>
        <div class="tl-tip">数据来源：毕业进度 <code>/graduation/summary</code> 与系统配置。必修由教学计划自动排入、不占选课额度；推荐课程按大类缺口排序，此处按学分均衡分摊，实际选课仍需避开时间冲突（用「选课方案预演」校验）。</div>
      </template>
    </template>
  </div>
</template>

<style scoped>
.tl { padding: 4px 2px 20px; }
.tl-loading, .tl-empty { padding: 20px 8px; text-align: center; color: var(--text-lighter); font-size: 13px; }
.tl-warn { padding: 10px 12px; border-radius: 10px; background: rgba(230,162,60,.12); color: #d98b2b; font-size: 13px; margin-bottom: 12px; }
.tl-bar { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; margin-bottom: 12px; }
.tl-label { font-size: 12.5px; color: var(--text-light); }
.tl-select { width: 120px; padding: 7px 10px; border: 1px solid var(--border); border-radius: 8px; background: var(--card); color: var(--text); font-family: inherit; font-size: 12.5px; }
.tl-meta { font-size: 12px; color: var(--text-lighter); }
.tl-kpis { display: grid; grid-template-columns: repeat(4, 1fr); gap: 10px; margin-bottom: 12px; }
.tl-kpi { padding: 12px; border: 1px solid var(--border); border-radius: 12px; background: var(--card); text-align: center; }
.tl-kpi b { display: block; font-size: 22px; color: var(--text); line-height: 1.2; }
.tl-kpi span { font-size: 12px; color: var(--text-lighter); }
.tl-kpi.bad { border-color: rgba(240,80,80,.5); }
.tl-kpi.bad b { color: #e05a5a; }
.tl-alert { padding: 9px 12px; border-radius: 9px; font-size: 12.5px; line-height: 1.6; margin-bottom: 8px; }
.tl-alert.err { background: rgba(240,80,80,.11); color: #d85050; }
.tl-alert.warn { background: rgba(230,162,60,.12); color: #c9822b; }
.tl-alert.ok { background: rgba(43,177,135,.12); color: #1fa877; }
.tl-box { padding: 14px; border: 1px solid var(--border); border-radius: 12px; background: var(--card); }
.tl-box-h { font-size: 13px; font-weight: 600; color: var(--text); margin-bottom: 10px; }
.tl-table { width: 100%; border-collapse: collapse; font-size: 12.5px; }
.tl-table th, .tl-table td { padding: 8px 10px; border-bottom: 1px solid var(--border); text-align: left; color: var(--text); }
.tl-table th { font-size: 11.5px; color: var(--text-lighter); font-weight: 600; }
.tl-table tr:last-child td { border-bottom: none; }
.tl-table .num { text-align: right; }
.ok-text { color: #1fa877; } .warn-text { color: #d98b2b; }
.tl-sems { display: grid; grid-template-columns: repeat(auto-fit, minmax(210px, 1fr)); gap: 10px; }
.tl-sem { padding: 11px; border: 1px solid var(--border); border-radius: 10px; background: rgba(127,127,127,.04); }
.tl-sem-h { display: flex; justify-content: space-between; align-items: baseline; font-size: 12.5px; color: var(--text); margin-bottom: 8px; }
.tl-sem-h span { font-size: 11.5px; color: var(--text-lighter); }
.tl-course { display: flex; flex-direction: column; padding: 6px 0; border-top: 1px dashed var(--border); }
.tl-course-name { font-size: 12.5px; font-weight: 600; color: var(--text); }
.tl-sub { font-size: 11px; color: var(--text-lighter); margin-top: 2px; }
.tl-tip { margin-top: 12px; font-size: 12px; line-height: 1.7; color: var(--text-lighter); }
.tl-tip code { padding: 1px 5px; border-radius: 4px; background: rgba(127,127,127,.12); }
</style>
