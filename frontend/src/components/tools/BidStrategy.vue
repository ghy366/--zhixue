<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import http from '@/api'
import { loadToolData, type ToolData } from './toolData'

const emit = defineEmits<{ back: [] }>()
const tool = ref<ToolData | null>(null)
const loading = ref(true)

interface Row {
  courseId: number; name: string; category: string; capacity: number; approved: number
  safeLine: number | null; history: number | null; bidCount: number
  points: number; chance: number | null; seatsLeft: number | null; myRank: number | null
}
const rows = ref<Row[]>([])
const timers = new Map<number, any>()

const total = computed(() => Number(tool.value?.points?.total ?? 50))
const used = computed(() => Number(tool.value?.points?.used ?? 0))
const remaining = computed(() => Number(tool.value?.points?.remaining ?? 50))
const planned = computed(() => rows.value.reduce((s, r) => s + (Number(r.points) || 0), 0))
const over = computed(() => planned.value > remaining.value)

const chanceClass = (c: number | null) => c == null ? '' : c >= 70 ? 'hi' : c >= 35 ? 'mid' : 'lo'

function schedule(row: Row) {
  clearTimeout(timers.get(row.courseId))
  row.chance = null
  timers.set(row.courseId, setTimeout(() => calc(row), 350))
}

async function calc(row: Row) {
  try {
    const r: any = await http.post('/enrollments/simulate', {
      courseId: row.courseId, points: Math.max(0, Math.min(50, Number(row.points) || 0)),
    })
    const d = r?.data || {}
    row.chance = d.chance ?? null
    row.seatsLeft = d.seatsLeft ?? null
    row.myRank = d.myRank ?? null
  } catch { row.chance = null }
}

function buildRows() {
  const t = tool.value
  if (!t) return
  const candidates = t.courses
    .filter((c: any) => !String(c.category || '').includes('必修'))
    .filter((c: any) => c.myStatus !== 'approved' && c.myStatus !== 'pending')
    .sort((a: any, b: any) => {
      const w = (x: any) => (x.myIntent ? 100 : 0) + (x.guide?.bidCount || 0) + (x.guide?.safeLine != null ? 20 : 0)
      return w(b) - w(a)
    })
    .slice(0, 8)
  rows.value = candidates.map((c: any) => ({
    courseId: c.id, name: c.name, category: c.category,
    capacity: c.capacity || 0, approved: c.stat?.approved || 0,
    safeLine: c.guide?.safeLine ?? null,
    history: c.guide?.history?.cutline ?? null,
    bidCount: c.guide?.bidCount || 0,
    points: Number(c.myIntentPoints ?? t.intents.find((i: any) => i.courseId === c.id)?.desiredPoints ?? 0),
    chance: null, seatsLeft: null, myRank: null,
  }))
  rows.value.forEach(schedule)
}

/** 按竞争线从高到低分配积分：竞争线 + 5 分保底，池子用完即止 */
function autoAllocate() {
  let left = remaining.value
  const sorted = [...rows.value].sort((a, b) => (b.safeLine ?? b.history ?? 0) - (a.safeLine ?? a.history ?? 0))
  rows.value.forEach(r => { r.points = 0 })
  for (const r of sorted) {
    if (left <= 0) break
    const base = r.safeLine ?? r.history ?? 0
    const want = Math.max(0, Math.min(50, Math.ceil(base) + 5))
    r.points = Math.min(want, left)
    left -= r.points
  }
  rows.value.forEach(schedule)
}

function reset() {
  buildRows()
}

function onInput(row: Row) {
  row.points = Math.max(0, Math.min(50, Number(row.points) || 0))
  schedule(row)
}

onMounted(async () => {
  tool.value = await loadToolData()
  buildRows()
  loading.value = false
})
onBeforeUnmount(() => timers.forEach(t => clearTimeout(t)))
</script>

<template>
  <div class="tl">
    <button class="tool-back" @click="emit('back')">← 工具箱</button>
    <div v-if="loading" class="tl-loading">正在读取选课数据…</div>
    <template v-else>
      <div v-if="!tool?.isStudent" class="tl-warn">投分策略仅对学生账号可用（需要积分池与意向单数据）。</div>
      <div class="tl-kpis">
        <div class="tl-kpi"><b>{{ total }}</b><span>本轮积分池</span></div>
        <div class="tl-kpi"><b>{{ used }}</b><span>已投入</span></div>
        <div class="tl-kpi hl"><b>{{ remaining }}</b><span>可用</span></div>
        <div class="tl-kpi" :class="{ bad: over }"><b>{{ planned }}</b><span>本方案合计</span></div>
      </div>

      <div class="tl-bar">
        <button class="tl-btn tl-primary" @click="autoAllocate">按竞争线自动分配</button>
        <button class="tl-btn" @click="reset">恢复当前意向</button>
        <span v-if="over" class="tl-over">⚠ 超出可用积分 {{ planned - remaining }} 分</span>
        <span v-else class="tl-ok">✓ 方案未超支，余 {{ remaining - planned }} 分</span>
      </div>

      <div class="tl-table-wrap">
        <table class="tl-table">
          <thead><tr>
            <th>课程</th><th>竞争线</th><th>历史线</th><th>投分</th><th>录取概率</th><th>余位</th>
          </tr></thead>
          <tbody>
            <tr v-for="r in rows" :key="r.courseId">
              <td><div class="tl-name">《{{ r.name }}》</div><div class="tl-sub">{{ r.category }} · 已报 {{ r.bidCount }} 人 · 容量 {{ r.capacity }}</div></td>
              <td class="num">{{ r.safeLine ?? '未满' }}</td>
              <td class="num">{{ r.history ?? '—' }}</td>
              <td><input class="tl-pt" type="number" min="0" max="50" v-model.number="r.points" @input="onInput(r)" /></td>
              <td class="num"><span class="tl-chance" :class="chanceClass(r.chance)">{{ r.chance == null ? '计算中' : r.chance + '%' }}</span></td>
              <td class="num">{{ r.seatsLeft ?? '—' }}<span v-if="r.myRank != null" class="tl-sub"> 预估第 {{ r.myRank }} 名</span></td>
            </tr>
            <tr v-if="!rows.length"><td colspan="6" class="tl-empty">暂无可投分的选修课。</td></tr>
          </tbody>
        </table>
      </div>

      <div class="tl-tip">
        概率来自系统 <code>/enrollments/simulate</code>，按当前报名快照实时估算，截止前仍会变化；竞争线为「此刻按积分排名恰好卡在容量末位」的分数。第 1、2 轮结算后统一录取，同分随机抽签；第 3 轮先到先得、不消耗积分。
      </div>
    </template>
  </div>
</template>

<style scoped>
.tl { padding: 4px 2px 20px; }
.tl-loading, .tl-empty { padding: 26px 8px; text-align: center; color: var(--text-lighter); font-size: 13px; }
.tl-warn { padding: 10px 12px; border-radius: 10px; background: rgba(230,162,60,.12); color: #d98b2b; font-size: 13px; margin-bottom: 12px; }
.tl-kpis { display: grid; grid-template-columns: repeat(4, 1fr); gap: 10px; margin-bottom: 14px; }
.tl-kpi { padding: 12px; border: 1px solid var(--border); border-radius: 12px; background: var(--card); text-align: center; }
.tl-kpi b { display: block; font-size: 22px; color: var(--text); line-height: 1.2; }
.tl-kpi span { font-size: 12px; color: var(--text-lighter); }
.tl-kpi.hl b { color: var(--primary, #2bb187); }
.tl-kpi.bad { border-color: rgba(240,80,80,.5); }
.tl-kpi.bad b { color: #e05a5a; }
.tl-bar { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; margin-bottom: 12px; }
.tl-btn { padding: 7px 14px; border-radius: 9px; border: 1px solid var(--border); background: var(--card); color: var(--text-light); font-family: inherit; font-size: 12.5px; cursor: pointer; }
.tl-btn:hover { border-color: var(--primary, #2bb187); color: var(--primary, #2bb187); }
.tl-btn.tl-primary { background: var(--primary, #2bb187); color: #fff; border-color: transparent; }
.tl-btn.tl-primary:hover { color: #fff; opacity: .88; }
.tl-over { font-size: 12.5px; color: #e05a5a; }
.tl-ok { font-size: 12.5px; color: var(--primary, #2bb187); }
.tl-table-wrap { overflow-x: auto; border: 1px solid var(--border); border-radius: 12px; background: var(--card); }
.tl-table { width: 100%; border-collapse: collapse; font-size: 13px; }
.tl-table th, .tl-table td { padding: 10px 12px; border-bottom: 1px solid var(--border); text-align: left; color: var(--text); }
.tl-table th { font-size: 12px; color: var(--text-lighter); font-weight: 600; background: rgba(127,127,127,.05); }
.tl-table tr:last-child td { border-bottom: none; }
.tl-table .num { text-align: right; white-space: nowrap; }
.tl-name { font-weight: 600; }
.tl-sub { font-size: 11.5px; color: var(--text-lighter); margin-top: 2px; }
.tl-table .tl-sub { display: block; }
.tl-pt { width: 68px; padding: 5px 8px; border: 1px solid var(--border); border-radius: 8px; background: var(--card); color: var(--text); font-family: inherit; font-size: 13px; }
.tl-chance { display: inline-block; padding: 2px 9px; border-radius: 999px; font-weight: 600; background: rgba(127,127,127,.12); color: var(--text-light); }
.tl-chance.hi { background: rgba(43,177,135,.15); color: #1fa877; }
.tl-chance.mid { background: rgba(230,162,60,.15); color: #d98b2b; }
.tl-chance.lo { background: rgba(240,80,80,.13); color: #e05a5a; }
.tl-tip { margin-top: 12px; font-size: 12px; line-height: 1.7; color: var(--text-lighter); }
.tl-tip code { padding: 1px 5px; border-radius: 4px; background: rgba(127,127,127,.12); }
</style>
