<template>
  <div class="page-wrap stats-page liquid">
    <div class="stats-grid">
      <div class="stat-card"><div class="stat-icon purple" v-html="svgIcon('cart')" /><div><div class="stat-value">{{ animIntentCount }}</div><div class="stat-label">{{ t('意向课程') }}</div></div></div>
      <div class="stat-card"><div class="stat-icon blue" v-html="svgIcon('coins')" /><div><div class="stat-value">{{ animTotalPoints }}</div><div class="stat-label">{{ t('预计投入积分') }}</div></div></div>
      <div class="stat-card"><div class="stat-icon amber" v-html="svgIcon('coins')" /><div><div class="stat-value">{{ animRemaining }}</div><div class="stat-label">{{ t('本轮剩余积分') }}</div></div></div>
    </div>

    <!-- ===== 积分分配规划 ===== -->
    <div class="it-plan">
      <div class="it-plan-card it-ring-card">
        <svg viewBox="0 0 110 110" class="it-ring" role="img" :aria-label="t('积分钱包：已投与剩余')">
          <g transform="rotate(-90 55 55)">
            <circle cx="55" cy="55" r="45" class="it-ring-track" />
            <!-- 已投积分（琥珀）从 0 起画 -->
            <circle
              cx="55" cy="55" r="45"
              class="it-ring-used"
              :stroke-dasharray="`${usedPct / 100 * RING_C} ${RING_C}`"
            />
            <!-- 剩余积分（绿）紧接已投段之后 -->
            <circle
              cx="55" cy="55" r="45"
              class="it-ring-fill"
              :class="{ low: remainingPct <= 20 }"
              :stroke-dasharray="`${remainingPct / 100 * RING_C} ${RING_C}`"
              :stroke-dashoffset="-usedPct / 100 * RING_C"
              stroke-linecap="round"
            />
          </g>
          <text x="55" y="53" text-anchor="middle" class="it-ring-num">{{ animRemaining }}</text>
          <text x="55" y="70" text-anchor="middle" class="it-ring-sub">/ {{ pointPool }} {{ t('剩余') }}</text>
        </svg>
        <div class="it-ring-side">
          <div class="it-plan-title" :class="{ warn: overBudget }">
            {{ overBudget ? t('预填超出积分池 {n} 分', { n: totalPoints - pointPool }) : t('积分分配方案') }}
          </div>
          <div class="it-ring-line"><span><i class="lg-used" />{{ t('已投积分') }}</span><b>{{ used }} {{ t('分') }}</b></div>
          <div class="it-ring-line"><span><i class="lg-free" />{{ t('剩余积分') }}</span><b>{{ animRemaining }} {{ t('分') }}</b></div>
          <div class="it-ring-line"><span>{{ t('本轮积分池') }}</span><b>{{ pointPool }} {{ t('分') }}</b></div>
          <div class="it-ring-line"><span>{{ t('预填合计') }}</span><b>{{ totalPoints }} {{ t('分') }}</b></div>
        </div>
      </div>

      <div class="it-plan-card it-mix-card">
        <div class="it-plan-title">{{ t('积分池分配') }}</div>
        <div class="it-mix-bar">
          <i
            v-for="(seg, i) in poolSegments" :key="i"
            :class="'seg' + (i % 6)"
            :style="{ width: seg.pct + '%' }"
            :title="t('{name} 预填 {p} 分（占池 {pct}%）', { name: seg.name, p: seg.points, pct: seg.pct })"
          />
          <i class="seg-free" :style="{ width: freePct + '%' }" :title="t('未分配 {n} 分', { n: remaining })" />
        </div>
        <div class="it-mix-legend">
          <span v-for="(seg, i) in poolSegments" :key="i"><i :class="'seg' + (i % 6)" />{{ seg.name }} <b>{{ seg.points }}</b></span>
          <span><i class="seg-free" />{{ t('剩余') }} <b>{{ remaining }}</b></span>
        </div>
      </div>
    </div>

    <!-- ===== 积分决策沙盘 ===== -->
    <div class="sandbox" v-if="list.length">
      <div class="sb-head">
        <div class="sb-title" v-html="svgIcon('coins') + ' ' + t('投多少分能选上？试试看')" />
        <div class="sb-total" :class="{ over: simTotal > pointPool }">
          {{ t('模拟合计') }} <b>{{ simTotal }}</b> / {{ pointPool }} {{ t('分') }}
          <i v-if="simTotal > pointPool">{{ t('已超池 {n} 分', { n: simTotal - pointPool }) }}</i>
        </div>
      </div>
      <div class="sb-sub">{{ t('拖动滑块模拟投入积分，按当前报名快照实时估算录取概率（同分随机抽签，仅供参考）') }}<template v-if="currentRound >= 3">{{ t('。当前为第 3 轮先到先得制，以下模拟仅作历史参考') }}</template></div>
      <div class="sb-grid">
        <div class="sb-card" v-for="it in list" :key="'sb' + it.courseId">
          <div class="sb-top">
            <b class="sb-name" :title="it.courseName">{{ it.courseName }}</b>
            <span class="sb-cap">{{ t('容量 {c} · 已报 {b}', { c: it.capacity, b: guideOf(it.courseId).bidCount || 0 }) }}</span>
          </div>
          <div class="sb-refs">
            <span v-if="guideOf(it.courseId).topPoints" class="sb-ref" :title="t('本轮所有同学里投出的最高分')">{{ t('最高') }} <b>{{ guideOf(it.courseId).topPoints }}</b></span>
            <span v-if="guideOf(it.courseId).safeLine != null" class="sb-ref warn" :title="t('投分达到这条线，就能进入名额内（按分从高到低录满为止）')">{{ t('稳录线') }} <b>{{ guideOf(it.courseId).safeLine }}</b></span>
            <span v-if="guideOf(it.courseId).history && guideOf(it.courseId).history.cutline != null" class="sb-ref prev" :title="t('上一轮这门课最低多少分能被录取，可作参考')">{{ t('上轮录取线') }} <b>{{ guideOf(it.courseId).history.cutline }}</b></span>
            <span v-if="!guideOf(it.courseId).bidCount" class="sb-ref ok">{{ t('暂无竞争') }}</span>
          </div>
          <div class="sb-slider-row" v-if="sims[it.courseId]">
            <el-slider
              v-model="sims[it.courseId].points"
              :min="0" :max="pointPool" :step="1"
              @change="(v:any) => simulate(it, v)"
            />
            <div class="sb-pts"><b>{{ sims[it.courseId].points }}</b><i>{{ t('分') }}</i></div>
          </div>
          <div class="sb-res" v-if="sims[it.courseId]">
            <template v-if="rejectReason(it)">
              <!-- 存在时间冲突 / 超出门数上限时，无论投多少分都会被结算拒绝，不再展示概率 -->
              <div class="sb-chance-row">
                <b class="sb-chance t-bad">{{ t('将被拒绝') }}</b>
                <span class="sb-tag t-bad">{{ t('当前将被拒绝') }}</span>
              </div>
              <div class="sb-reject">{{ t('原因：{r} · 当前无论投多少分都会被结算拒绝，请先解决', { r: t(rejectReason(it)) }) }}{{ rejectReason(it) === '时间冲突' ? t('时间冲突') : t('门数超限') }}</div>
            </template>
            <template v-else-if="sims[it.courseId].res">
              <div class="sb-chance-row">
                <b class="sb-chance" :class="sims[it.courseId].res.seatsLeft === 0 ? 't-bad' : tierOf(sims[it.courseId].res.chance).cls">{{ sims[it.courseId].res.chance }}%</b>
                <span class="sb-tag" :class="sims[it.courseId].res.seatsLeft === 0 ? 't-full' : tierOf(sims[it.courseId].res.chance).cls">{{ sims[it.courseId].res.seatsLeft === 0 ? t('名额已满') : tierOf(sims[it.courseId].res.chance).label }}</span>
              </div>
              <div class="sb-bar"><i :class="tierOf(sims[it.courseId].res.chance).cls" :style="{ width: sims[it.courseId].res.chance + '%' }" /></div>
              <div class="sb-meta">
                <template v-if="(guideOf(it.courseId).bidCount || 0) > 0">
                  <span :title="t('按当前投分从高到低排，你排在第几')">{{ t('你排第') }} <b>{{ sims[it.courseId].res.myRank }}</b> {{ t('名') }}</span>
                  <span>{{ t('剩余名额') }} <b>{{ sims[it.courseId].res.seatsLeft }}</b></span>
                  <span :title="t('投分比你低的比例')">{{ t('超过') }} <b>{{ sims[it.courseId].res.beatPct }}%</b> {{ t('的竞争者') }}</span>
                </template>
                <template v-else>
                  <span>{{ t('暂无竞争者') }}</span>
                  <span>{{ t('剩余名额') }} <b>{{ sims[it.courseId].res.seatsLeft }}</b></span>
                </template>
                <span v-if="sims[it.courseId].res.seatsLeft === 0" class="sb-hint">{{ t('建议加入候补或等下一轮') }}</span>
              </div>
            </template>
            <div v-else class="sb-loading">{{ t('概率计算中…') }}</div>
          </div>
          <div class="sb-foot">
            <button class="btn btn-ghost btn-sm" :disabled="!sims[it.courseId] || sims[it.courseId].points === (it.desiredPoints ?? 0)" @click="applySim(it)">
              {{ t('应用到意向单') }}
            </button>
          </div>
        </div>
      </div>
    </div>

    <div class="section">
      <div class="section-header">
        <div class="section-title" v-html="svgIcon('cart') + ' ' + t('意向单（{n} 门）', { n: list.length })" />
        <div style="display:flex;gap:8px">
          <button class="btn btn-ghost btn-sm" :disabled="!list.length" @click="clearAll">
            <span class="icon" v-html="svgIcon('trash')" /> {{ t('清空') }}
          </button>
          <button class="btn btn-primary btn-sm" :disabled="!list.length" @click="submit">
            <span class="icon" v-html="svgIcon('check-circle')" /> {{ t('一键转正式选课') }}
          </button>
        </div>
      </div>
      <!-- ===== 冲突与门数预检（不拦截，仅提醒） ===== -->
      <div v-if="conflictRows.length || overLimit" class="it-warn">
        <div class="it-warn-title" v-html="svgIcon('alert-triangle') + ' ' + t('投分前预检提醒')"></div>
        <ul>
          <li v-for="r in conflictRows" :key="'cf' + r.courseId">
            <b>{{ r.courseName }}</b> {{ t('与 {names} 时间冲突——同报将由结算按积分高低取舍', { names: r.conflictWith.join('、') }) }}
          </li>
          <li v-if="overLimit">
            {{ t('已选选修') }} <b>{{ approvedCount }}</b> {{ t('门 + 意向') }} <b>{{ list.length }}</b> {{ t('门，超出本学期选修门数上限') }} <b>{{ maxCourses }}</b> {{ t('门，超额意向转正会被拒') }}
          </li>
        </ul>
      </div>
      <div v-if="list.length" class="data-table">
        <table>
          <thead><tr><th>{{ t('课程') }}</th><th>{{ t('类别') }}</th><th>{{ t('教师') }}</th><th>{{ t('学分') }}</th><th>{{ t('时间/地点') }}</th><th>{{ t('实时竞争线') }}</th><th>{{ t('投入积分') }}</th><th>{{ t('操作') }}</th></tr></thead>
          <tbody>
            <tr v-for="it in list" :key="it.intentId">
              <td>
                <b>{{ it.courseName }}</b>
                <span v-if="(it.conflictWith || []).length" class="it-conflict"
                  :title="t('与 {names} 时间冲突', { names: it.conflictWith.join('、') })">{{ t('冲突') }}</span>
              </td>
              <td><span class="course-cat">{{ it.category }}</span></td>
              <td>{{ it.teacherName }}</td>
              <td>{{ it.credit }}</td>
              <td>{{ it.time || '—' }} @ {{ it.location || '—' }}</td>
              <td>
                <span v-if="guideOf(it.courseId).safeLine != null" class="it-line" :class="guideOf(it.courseId).safeLine > (it.desiredPoints || 0) ? 'above' : 'below'">
                  {{ guideOf(it.courseId).safeLine }} {{ t('分') }}
                  <i>{{ guideOf(it.courseId).safeLine > (it.desiredPoints || 0) ? t('线高于预填') : t('预填达标') }}</i>
                </span>
                <span v-else-if="guideOf(it.courseId).bidCount" class="it-line safe">{{ t('未满员') }}</span>
                <span v-else class="muted">{{ t('暂无') }}</span>
              </td>
              <td>
                <el-input-number :model-value="it.desiredPoints ?? 0" :min="0" :max="pointPool" size="small" style="width:96px" @change="(v:any) => updatePoints(it, v)" />
              </td>
              <td><button class="btn btn-danger btn-sm" @click="remove(it)">{{ t('移除') }}</button></td>
            </tr>
          </tbody>
        </table>
      </div>
      <div v-else class="empty-state">
        <div class="empty-icon" v-html="svgIcon('cart')" />
        <div class="empty-title">{{ t('还没有添加任何意向课程') }}</div>
        <router-link to="/courses" class="btn btn-primary btn-sm" style="margin-top:12px">{{ t('去课程浏览') }}</router-link>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '@/api'
import { useCountUp } from '@/utils/useCountUp'
import { t } from '@/i18n'
const svgIcon = (n: string) => `<svg class="icon"><use href="#i-${n}"/></svg>`
const list = ref<any[]>([])
const remaining = ref(0)
const used = ref(0)
// 当前轮次与本轮积分池（来自 /enrollments/points）
const currentRound = ref(1)
const pointPool = ref(50)
const totalPoints = computed(() => list.value.reduce((s, it) => s + (it.desiredPoints || 0), 0))

/* 统计数字滚动：意向数/预计投入/剩余积分（预填积分时实时跟随滚动） */
const animIntentCount = useCountUp(() => list.value.length)
const animTotalPoints = useCountUp(() => totalPoints.value)
const animRemaining = useCountUp(() => remaining.value)

/* ===== 积分分配规划 ===== */
const RING_C = 2 * Math.PI * 45
const usedPct = computed(() => {
  const pool = Number(pointPool.value) || 0
  return pool <= 0 ? 0 : Math.min(100, Math.max(0, Math.round(used.value * 100 / pool)))
})
const remainingPct = computed(() => {
  const pool = Number(pointPool.value) || 0
  return pool <= 0 ? 0 : Math.min(100, Math.max(0, Math.round(remaining.value * 100 / pool)))
})
const overBudget = computed(() => totalPoints.value > pointPool.value)
/** 各意向课的实时竞争数据（来自 /courses 的 guide） */
const guides = ref<any[]>([])
const guideOf = (courseId: number) => guides.value.find(c => c.id === courseId)?.guide || { safeLine: null, bidCount: 0 }

/* ===== 冲突与门数预检（后端 /intents/mine 附带 conflictWith / maxCourses / approvedCount） ===== */
const maxCourses = computed(() => list.value[0]?.maxCourses ?? null)
const approvedCount = computed(() => list.value[0]?.approvedCount ?? 0)
const conflictRows = computed(() => list.value.filter(it => (it.conflictWith || []).length))
const overLimit = computed(() =>
  maxCourses.value != null && maxCourses.value > 0 &&
  approvedCount.value + list.value.length > maxCourses.value
)
/** 模拟器拒绝判定：该课存在时间冲突，或已选 + 意向门数超上限时，投分必被拒 */
function rejectReason(it: any): string | null {
  if ((it.conflictWith || []).length) return '时间冲突'
  if (overLimit.value) return '超出门数上限'
  return null
}
const poolSegments = computed(() =>
  list.value
    .filter(it => (it.desiredPoints || 0) > 0)
    .sort((a, b) => (b.desiredPoints || 0) - (a.desiredPoints || 0))
    .map(it => ({
      name: it.courseName?.length > 8 ? it.courseName.slice(0, 8) + '…' : it.courseName,
      points: it.desiredPoints || 0,
      pct: pointPool.value ? Math.min(100, Math.round((it.desiredPoints || 0) * 100 / pointPool.value)) : 0,
    }))
)
const freePct = computed(() => {
  const pool = Number(pointPool.value) || 0
  return pool <= 0 ? 0 : Math.max(0, Math.min(100, Math.round(remaining.value * 100 / pool)))
})

/* ===== 积分决策沙盘 ===== */
// courseId -> { points: 模拟投分, res: /enrollments/simulate 结果, loading }
const sims = ref<Record<number, any>>({})
const simTotal = computed(() => Object.values(sims.value).reduce((s: number, x: any) => s + (x?.points || 0), 0))
function tierOf(chance: number) {
  if (chance >= 85) return { label: t('十拿九稳'), cls: 't-good' }
  if (chance >= 60) return { label: t('较稳'), cls: 't-ok' }
  if (chance >= 30) return { label: t('有风险'), cls: 't-mid' }
  if (chance > 0) return { label: t('比较悬'), cls: 't-risk' }
  return { label: t('基本无望'), cls: 't-bad' }
}
async function simulate(it: any, points: number) {
  const p = Math.max(0, Math.min(pointPool.value, Number(points) || 0))
  // 注意：必须整体替换 sims.value[id]，保证走响应式代理；持有 raw 对象改字段不会触发渲染
  const prev = sims.value[it.courseId]
  sims.value[it.courseId] = { points: p, res: prev?.res ?? null, loading: true }
  try {
    const r = await http.post('/enrollments/simulate', { courseId: it.courseId, points: p })
    sims.value[it.courseId] = { points: p, res: r?.data || null, loading: false }
  } catch {
    sims.value[it.courseId] = { points: p, res: null, loading: false }
  }
}
function initSims() {
  for (const it of list.value) simulate(it, it.desiredPoints ?? 10)
}
async function applySim(it: any) {
  const p = sims.value[it.courseId]?.points
  if (p == null) return
  await updatePoints(it, p)
}

async function load() {
  try {
    const [r, p, cs] = await Promise.all([
      http.get('/intents/mine'),
      http.get('/enrollments/points').catch(() => null),
      http.get('/courses').catch(() => null),
    ])
    list.value = r?.data || []
    remaining.value = p?.data?.remaining ?? 0
    used.value = p?.data?.used ?? Math.max(0, (p?.data?.total ?? 0) - (p?.data?.remaining ?? 0))
    currentRound.value = p?.data?.round ?? 1
    pointPool.value = p?.data?.total ?? 50
    guides.value = cs?.data || []
    initSims()
  } catch {}
}
async function updatePoints(it: any, v: number) {
  if (v === null || v === undefined) return
  const old = it.desiredPoints
  it.desiredPoints = v
  try {
    // 后端 /intents/add 是 upsert：同课程再次提交即覆盖积分
    await http.post('/intents/add', { courseId: it.courseId, points: v })
    ElMessage.success({ message: t('「{name}」已设为 {p} 分', { name: it.courseName, p: v }), duration: 1500 })
  } catch (e: any) {
    it.desiredPoints = old
    ElMessage.error(e?.message || t('保存失败'))
  }
}
async function remove(it: any) {
  try {
    // 后端是 POST /intents/remove {courseId}
    await http.post('/intents/remove', { courseId: it.courseId })
    ElMessage.success(t('已移除')); load()
  } catch (e: any) { ElMessage.error(e?.message || t('移除失败')) }
}
async function clearAll() {
  try { await ElMessageBox.confirm(t('确定清空全部意向？'), t('提示'), { type: 'warning', confirmButtonText: t('确定'), cancelButtonText: t('取消') }) } catch { return }
  try {
    // 后端是 POST /intents/clear（无 body）
    await http.post('/intents/clear', {})
    ElMessage.success(t('已清空')); load()
  } catch (e: any) { ElMessage.error(e?.message || t('清空失败')) }
}
async function submit() {
  try { await ElMessageBox.confirm(t('一键转为正式选课？'), t('提示'), { type: 'warning', confirmButtonText: t('确定'), cancelButtonText: t('取消') }) } catch { return }
  try { await http.post('/intents/submit', {}); ElMessage.success(t('意向单已提交')); load() }
  catch (e: any) { ElMessage.error(e?.message || t('提交失败')) }
}
onMounted(load)
</script>

<style scoped>
.page-wrap { padding: 4px 0; }
.empty-state { text-align: center; padding: 60px 20px; color: var(--text-light); }
.empty-icon { font-size: 48px; opacity: 0.3; margin-bottom: 10px; }
.course-cat { font-size: 12px; padding: 2px 8px; border-radius: 999px; background: rgba(59,130,246,0.15); color: #60a5fa; }
.muted { color: var(--text-light); font-size: 12px; }

/* ===== 积分分配规划 ===== */
.it-plan { display: grid; grid-template-columns: auto minmax(0, 1fr); gap: 14px; }
@media (max-width: 760px) { .it-plan { grid-template-columns: minmax(0, 1fr); } }
.it-plan-card { background: var(--card); border: 1px solid var(--border); border-radius: 13px; padding: 14px 16px; }
.it-ring-card { display: flex; align-items: center; gap: 16px; }
.it-ring { width: 104px; height: 104px; flex-shrink: 0; }
.it-ring-track { fill: none; stroke: rgba(148,163,184,.16); stroke-width: 10; }
.it-ring-used { fill: none; stroke: #f59e0b; stroke-width: 10; transition: stroke-dasharray .7s cubic-bezier(.22,.9,.36,1); }
.it-ring-fill { fill: none; stroke: #10b981; stroke-width: 10; transition: stroke-dasharray .7s cubic-bezier(.22,.9,.36,1), stroke-dashoffset .7s cubic-bezier(.22,.9,.36,1); }
.it-ring-fill.low { stroke: #f43f5e; }
.lg-used, .lg-free { display: inline-block; width: 8px; height: 8px; border-radius: 50%; margin-right: 6px; vertical-align: 0; }
.lg-used { background: #f59e0b; }
.lg-free { background: #10b981; }
.it-ring-num { font-size: 22px; font-weight: 800; fill: var(--text); }
.it-ring-sub { font-size: 12px; fill: var(--text-light); }
.it-plan-title { font-size: 13px; font-weight: 700; color: var(--text); margin-bottom: 8px; }
.it-plan-title.warn { color: #fb7185; }
.it-ring-line { display: flex; justify-content: space-between; gap: 16px; font-size: 12px; color: var(--text-light); padding: 4px 0; }
.it-ring-line b { color: var(--text); font-variant-numeric: tabular-nums; }
.it-mix-bar { display: flex; height: 16px; border-radius: 99px; overflow: hidden; background: rgba(148,163,184,.12); margin: 4px 0 10px; }
.it-mix-bar i { display: block; height: 100%; min-width: 0; transition: width .5s cubic-bezier(.22,.9,.36,1); }
.it-mix-bar .seg-free { background: rgba(148,163,184,.2); }
.it-mix-bar .seg0 { background: #2bb187; }
.it-mix-bar .seg1 { background: #0ea5e9; }
.it-mix-bar .seg2 { background: #38bdf8; }
.it-mix-bar .seg3 { background: #10b981; }
.it-mix-bar .seg4 { background: #f59e0b; }
.it-mix-bar .seg5 { background: #ec4899; }
.it-mix-legend { display: flex; flex-wrap: wrap; gap: 6px 14px; font-size: 12px; color: var(--text-light); }
.it-mix-legend i { display: inline-block; width: 9px; height: 9px; border-radius: 3px; margin-right: 5px; vertical-align: middle; }
.it-mix-legend .seg-free { background: rgba(148,163,184,.3); }
.it-mix-legend .seg0 { background: #2bb187; }
.it-mix-legend .seg1 { background: #0ea5e9; }
.it-mix-legend .seg2 { background: #38bdf8; }
.it-mix-legend .seg3 { background: #10b981; }
.it-mix-legend .seg4 { background: #f59e0b; }
.it-mix-legend .seg5 { background: #ec4899; }
.it-mix-legend b { color: var(--text); margin-left: 2px; }

/* ===== 冲突与门数预检 ===== */
.it-warn { margin-top: 10px; padding: 2px 0 0 12px; border-radius: 0; background: none; }
.it-warn-title { font-size: 13px; font-weight: 700; color: #fbbf24; display: flex; align-items: center; gap: 6px; }
.it-warn-title :deep(.icon) { width: 15px; height: 15px; fill: currentColor; }
.it-warn ul { margin: 6px 0 0; padding-left: 18px; }
.it-warn li { font-size: 12.5px; color: var(--text); line-height: 1.7; }
.it-conflict { display: inline-block; margin-left: 6px; padding: 1px 7px; border-radius: 999px; font-size: 12px; font-weight: 700; color: #f87171; background: rgba(248,113,113,.14); border: 1px solid rgba(248,113,113,.4); }

/* 竞争线列 */
.it-line { font-size: 12px; font-weight: 700; padding: 2px 9px; border-radius: 999px; white-space: nowrap; }
.it-line.above { color: #fb7185; background: rgba(244,63,94,.13); }
.it-line.below { color: #2dd4bf; background: rgba(16,185,129,.13); }
.it-line.safe { color: #2563eb; background: rgba(59,130,246,.12); }
.it-line i { font-style: normal; font-weight: 500; font-size: 12px; margin-left: 4px; opacity: .85; }

/* ===== 积分决策沙盘 ===== */
.sandbox { background: var(--card); border: 1px solid var(--border); border-radius: 13px; padding: 14px 16px; margin-bottom: 14px; }
.sb-head { display: flex; align-items: center; justify-content: space-between; gap: 12px; flex-wrap: wrap; }
.sb-title { font-size: 14px; font-weight: 800; color: var(--text); display: flex; align-items: center; gap: 7px; }
.sb-title :deep(svg.icon) { width: 16px; height: 16px; }
.sb-total { font-size: 12px; color: var(--text-light); }
.sb-total b { color: var(--text); font-variant-numeric: tabular-nums; }
.sb-total.over { color: #fb7185; }
.sb-total.over b { color: #fb7185; }
.sb-total i { font-style: normal; font-weight: 700; margin-left: 6px; padding: 1px 8px; border-radius: 999px; background: rgba(244,63,94,.13); }
.sb-sub { font-size: 12px; color: var(--text-light); margin: 4px 0 12px; }
.sb-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(300px, 1fr)); gap: 12px; }
.sb-card { border: none; border-radius: 11px; padding: 12px 14px; background: rgba(148,163,184,.06); display: flex; flex-direction: column; gap: 8px; }
.sb-top { display: flex; align-items: baseline; justify-content: space-between; gap: 10px; }
.sb-name { font-size: 13px; font-weight: 700; color: var(--text); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.sb-cap { font-size: 12px; color: var(--text-light); white-space: nowrap; }
.sb-refs { display: flex; flex-wrap: wrap; gap: 6px; }
.sb-ref { font-size: 12px; padding: 2px 8px; border-radius: 999px; background: rgba(148,163,184,.14); color: var(--text-light); }
.sb-ref b { color: var(--text); margin-left: 2px; }
.sb-ref.warn { background: rgba(245,158,11,.14); color: #fbbf24; }
.sb-ref.warn b { color: #fbbf24; }
.sb-ref.prev { background: rgba(56,189,248,.14); color: #38bdf8; }
.sb-ref.prev b { color: #38bdf8; }
.sb-ref.ok { background: rgba(16,185,129,.14); color: #059669; }
.sb-slider-row { display: flex; align-items: center; gap: 12px; }
.sb-slider-row :deep(.el-slider) { flex: 1; --el-slider-main-bg-color: #2bb187; }
.sb-pts { min-width: 46px; text-align: right; }
.sb-pts b { font-size: 18px; font-weight: 800; color: var(--text); font-variant-numeric: tabular-nums; }
.sb-pts i { font-style: normal; font-size: 12px; color: var(--text-light); margin-left: 2px; }
.sb-res { min-height: 58px; }
.sb-chance-row { display: flex; align-items: center; gap: 8px; }
.sb-chance { font-size: 22px; font-weight: 800; font-variant-numeric: tabular-nums; }
.sb-chance.t-good { color: #059669; }
.sb-chance.t-ok { color: #2563eb; }
.sb-chance.t-mid { color: #fbbf24; }
.sb-chance.t-risk { color: #ea580c; }
.sb-chance.t-bad { color: #fb7185; }
.sb-tag { font-size: 12px; font-weight: 700; padding: 2px 9px; border-radius: 999px; }
.sb-tag.t-good { color: #059669; background: rgba(16,185,129,.13); }
.sb-tag.t-ok { color: #2563eb; background: rgba(59,130,246,.12); }
.sb-tag.t-mid { color: #fbbf24; background: rgba(245,158,11,.14); }
.sb-tag.t-risk { color: #ea580c; background: rgba(234,88,12,.13); }
.sb-tag.t-bad { color: #fb7185; background: rgba(244,63,94,.13); }
.sb-tag.t-full { color: #64748b; background: rgba(100,116,139,.16); }
.sb-bar { height: 6px; border-radius: 99px; background: rgba(148,163,184,.16); overflow: hidden; margin: 6px 0 8px; }
.sb-bar i { display: block; height: 100%; border-radius: 99px; transition: width .45s cubic-bezier(.22,.9,.36,1); }
.sb-bar i.t-good { background: linear-gradient(90deg, #34d399, #059669); }
.sb-bar i.t-ok { background: linear-gradient(90deg, #60a5fa, #2563eb); }
.sb-bar i.t-mid { background: linear-gradient(90deg, #fbbf24, #d97706); }
.sb-bar i.t-risk { background: linear-gradient(90deg, #fb923c, #ea580c); }
.sb-bar i.t-bad { background: linear-gradient(90deg, #fb7185, #e11d48); }
.sb-meta { display: flex; flex-wrap: wrap; gap: 4px 14px; font-size: 12px; color: var(--text-light); }
.sb-meta b { color: var(--text); font-variant-numeric: tabular-nums; }
.sb-reject { font-size: 12px; color: #fb7185; line-height: 1.6; padding: 2px 0; }
.sb-hint { color: #d97706; font-weight: 600; }
.sb-loading { font-size: 12px; color: var(--text-light); padding-top: 10px; }
.sb-foot { display: flex; justify-content: flex-end; }
</style>
