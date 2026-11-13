<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { loadToolData, timeSlots, overlap, groupOf, type ToolData } from './toolData'

const emit = defineEmits<{ back: [] }>()
const tool = ref<ToolData | null>(null)
const loading = ref(true)
const picked = ref<number[]>([])
const q = ref('')
const cat = ref('全部')

const cats = computed(() => ['全部', ...new Set((tool.value?.courses || []).map((c: any) => c.category).filter(Boolean))] as string[])
const pool = computed(() => (tool.value?.courses || [])
  .filter((c: any) => !String(c.category || '').includes('必修'))
  .filter((c: any) => (cat.value === '全部' || c.category === cat.value))
  .filter((c: any) => !q.value.trim() || String(c.name).toLowerCase().includes(q.value.trim().toLowerCase()))
  .slice(0, 60))
const selected = computed(() => (tool.value?.courses || []).filter((c: any) => picked.value.includes(c.id)))
const credits = computed(() => selected.value.reduce((s: number, c: any) => s + (Number(c.credit) || 0), 0))
const mineApproved = computed(() => {
  const ids = new Set((tool.value?.mine || []).filter((e: any) => e.status === 'approved').map((e: any) => e.courseId))
  return (tool.value?.courses || []).filter((c: any) => ids.has(c.id))
})
const passedIds = computed(() => new Set((tool.value?.mine || [])
  .filter((e: any) => Number(e.grade) >= 60).map((e: any) => e.courseId)))

interface Issue { level: 'err' | 'warn' | 'ok'; text: string }
const issues = computed<Issue[]>(() => {
  const out: Issue[] = []
  const sel = selected.value
  const cfg = tool.value?.cfg || {}

  // 两两时间冲突
  for (let i = 0; i < sel.length; i++) for (let j = i + 1; j < sel.length; j++) {
    if (overlap(timeSlots(sel[i].time), timeSlots(sel[j].time)))
      out.push({ level: 'err', text: `《${sel[i].name}》与《${sel[j].name}》上课时间重叠` })
  }
  // 与本学期已录取课程冲突
  for (const c of sel) for (const m of mineApproved.value) {
    if (c.id !== m.id && overlap(timeSlots(c.time), timeSlots(m.time)))
      out.push({ level: 'err', text: `《${c.name}》与已录取的《${m.name}》时间冲突` })
  }
  // 学分 / 门数上限
  const maxCredit = Number(cfg.maxCreditPerSemester || 0)
  if (maxCredit > 0 && credits.value > maxCredit)
    out.push({ level: 'err', text: `选修学分合计 ${credits.value} 分，超出本学期上限 ${maxCredit} 分` })
  const maxCourses = Number(cfg.maxCoursesPerSemester || 0)
  if (maxCourses > 0 && sel.length > maxCourses)
    out.push({ level: 'err', text: `已选 ${sel.length} 门，超出本学期门数上限 ${maxCourses} 门` })
  // 先修课
  for (const c of sel) if (c.prereqId && !passedIds.value.has(c.prereqId))
    out.push({ level: 'warn', text: `《${c.name}》需先通过先修课《${c.prereqName || c.prereqId}》（总评 ≥ 60）` })
  // 容量 / 重复 / 大类缺口
  const gaps: Record<string, number> = {}
  for (const g of tool.value?.grad?.categories || []) gaps[g.key] = Number(g.gap || 0)
  for (const c of sel) {
    if (c.full || (c.capacity > 0 && (c.stat?.total || 0) >= c.capacity))
      out.push({ level: 'warn', text: `《${c.name}》名额已满，报名后需进入候补队列` })
    if (mineApproved.value.some((m: any) => m.id === c.id))
      out.push({ level: 'warn', text: `《${c.name}》本学期已录取，无需重复报名` })
    const g = groupOf(c.category)
    if (gaps[g] != null && gaps[g] <= 0)
      out.push({ level: 'warn', text: `《${c.name}》所属「${g}」大类已达标，修读不计入毕业缺口` })
  }
  if (!sel.length) out.push({ level: 'ok', text: '从左侧勾选课程，这里会实时校验冲突与额度。' })
  else if (!out.length) out.push({ level: 'ok', text: '✓ 这套方案没有检测到冲突或超额，可以按本轮规则报名。' })
  return out
})

const errCount = computed(() => issues.value.filter(i => i.level === 'err').length)
const warnCount = computed(() => issues.value.filter(i => i.level === 'warn').length)

const week = computed(() => {
  const days = ['周一', '周二', '周三', '周四', '周五', '周六', '周日']
  const map: Record<string, { label: string; name: string }[]> = {}
  days.forEach(d => map[d] = [])
  for (const c of selected.value) for (const s of timeSlots(c.time))
    map[s.day]?.push({ label: s.label, name: c.name })
  return days.filter(d => map[d].length).map(d => ({ day: d, items: map[d].sort((a, b) => a.label.localeCompare(b.label)) }))
})

function toggle(id: number) {
  const i = picked.value.indexOf(id)
  if (i >= 0) picked.value.splice(i, 1)
  else picked.value.push(id)
}

onMounted(async () => { tool.value = await loadToolData(); loading.value = false })
</script>

<template>
  <div class="tl">
    <button class="tool-back" @click="emit('back')">← 工具箱</button>
    <div v-if="loading" class="tl-loading">正在读取课程、课表与毕业要求…</div>
    <template v-else>
      <div class="tl-kpis">
        <div class="tl-kpi"><b>{{ selected.length }}</b><span>已选课程</span></div>
        <div class="tl-kpi"><b>{{ credits }}</b><span>学分合计</span></div>
        <div class="tl-kpi" :class="{ bad: errCount }"><b>{{ errCount }}</b><span>硬冲突</span></div>
        <div class="tl-kpi"><b>{{ warnCount }}</b><span>需留意</span></div>
      </div>

      <div class="tl-grid">
        <div class="tl-box">
          <div class="tl-box-h">选择课程（不含必修，必修由系统自动排课）</div>
          <div class="tl-filters">
            <input v-model="q" class="tl-search" placeholder="搜索课程名" />
            <select v-model="cat" class="tl-select"><option v-for="c in cats" :key="c" :value="c">{{ c }}</option></select>
          </div>
          <div class="tl-pool">
            <label v-for="c in pool" :key="c.id" class="tl-pick" :class="{ on: picked.includes(c.id) }">
              <input type="checkbox" :checked="picked.includes(c.id)" @change="toggle(c.id)" />
              <span class="tl-pick-main">
                <span class="tl-pick-name">《{{ c.name }}》</span>
                <span class="tl-sub">{{ c.credit }} 学分 · {{ c.time || '时间待定' }} · {{ c.stat?.total || 0 }}/{{ c.capacity }} 人</span>
              </span>
            </label>
            <div v-if="!pool.length" class="tl-empty">没有匹配的课程</div>
          </div>
          <div v-if="selected.length" class="tl-chips">
            <button v-for="c in selected" :key="c.id" class="tl-chip" @click="toggle(c.id)">{{ c.name }} ×</button>
            <button class="tl-clear" @click="picked = []">清空</button>
          </div>
        </div>

        <div class="tl-box">
          <div class="tl-box-h">校验结果</div>
          <div class="tl-issues">
            <div v-for="(it, i) in issues" :key="i" class="tl-issue" :class="it.level">{{ it.text }}</div>
          </div>
          <div class="tl-box-h" style="margin-top:16px">周课表预览</div>
          <div v-if="!week.length" class="tl-empty">勾选课程后显示上课时段</div>
          <div v-for="d in week" :key="d.day" class="tl-day">
            <b>{{ d.day }}</b>
            <div><span v-for="(x, i) in d.items" :key="i" class="tl-slot">{{ x.label }} 《{{ x.name }}》</span></div>
          </div>
        </div>
      </div>

      <div class="tl-tip">校验口径与报名/结算一致：同日时段重叠即冲突；学分与门数上限取系统当前配置；先修课需总评 ≥ 60；名额已满时只能进候补。第 1、2 轮报名阶段只登记不拦截，最终由结算统一裁决。</div>
    </template>
  </div>
</template>

<style scoped>
.tl { padding: 4px 2px 20px; }
.tl-loading, .tl-empty { padding: 20px 8px; text-align: center; color: var(--text-lighter); font-size: 13px; }
.tl-kpis { display: grid; grid-template-columns: repeat(4, 1fr); gap: 10px; margin-bottom: 14px; }
.tl-kpi { padding: 12px; border: 1px solid var(--border); border-radius: 12px; background: var(--card); text-align: center; }
.tl-kpi b { display: block; font-size: 22px; color: var(--text); line-height: 1.2; }
.tl-kpi span { font-size: 12px; color: var(--text-lighter); }
.tl-kpi.bad { border-color: rgba(240,80,80,.5); }
.tl-kpi.bad b { color: #e05a5a; }
.tl-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
.tl-box { padding: 14px; border: 1px solid var(--border); border-radius: 12px; background: var(--card); min-width: 0; }
.tl-box-h { font-size: 13px; font-weight: 600; color: var(--text); margin-bottom: 10px; }
.tl-filters { display: flex; gap: 8px; margin-bottom: 10px; }
.tl-search, .tl-select { flex: 1; min-width: 0; padding: 7px 10px; border: 1px solid var(--border); border-radius: 8px; background: var(--card); color: var(--text); font-family: inherit; font-size: 12.5px; }
.tl-select { flex: 0 0 110px; }
.tl-pool { max-height: 260px; overflow-y: auto; border: 1px solid var(--border); border-radius: 9px; }
.tl-pick { display: flex; gap: 8px; align-items: flex-start; padding: 8px 10px; border-bottom: 1px solid var(--border); cursor: pointer; }
.tl-pick:last-child { border-bottom: none; }
.tl-pick.on { background: rgba(43,177,135,.09); }
.tl-pick input { margin-top: 3px; accent-color: var(--primary, #2bb187); }
.tl-pick-main { display: flex; flex-direction: column; min-width: 0; }
.tl-pick-name { font-size: 12.5px; font-weight: 600; color: var(--text); }
.tl-sub { font-size: 11px; color: var(--text-lighter); margin-top: 2px; }
.tl-chips { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 10px; }
.tl-chip, .tl-clear { padding: 3px 9px; border-radius: 999px; border: 1px solid rgba(43,177,135,.35); background: rgba(43,177,135,.10); color: var(--primary, #2bb187); font-size: 11.5px; cursor: pointer; font-family: inherit; }
.tl-clear { border-color: var(--border); background: var(--card); color: var(--text-lighter); }
.tl-issues { display: flex; flex-direction: column; gap: 7px; max-height: 210px; overflow-y: auto; }
.tl-issue { padding: 8px 10px; border-radius: 8px; font-size: 12.5px; line-height: 1.5; }
.tl-issue.err { background: rgba(240,80,80,.11); color: #d85050; }
.tl-issue.warn { background: rgba(230,162,60,.12); color: #c9822b; }
.tl-issue.ok { background: rgba(43,177,135,.12); color: #1fa877; }
.tl-day { display: flex; gap: 10px; padding: 7px 0; border-bottom: 1px dashed var(--border); font-size: 12px; color: var(--text-light); }
.tl-day b { flex: 0 0 34px; color: var(--text); }
.tl-slot { display: inline-block; margin: 0 6px 4px 0; padding: 2px 7px; border-radius: 6px; background: rgba(127,127,127,.10); font-size: 11.5px; }
.tl-tip { margin-top: 12px; font-size: 12px; line-height: 1.7; color: var(--text-lighter); }
@media (max-width: 900px) { .tl-grid { grid-template-columns: 1fr; } }
</style>
