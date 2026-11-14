<template>
  <div ref="screenRef" class="stats-page liquid analysis-page stats-bs" :class="{ 'bs-mode': bigScreen }">
    <header class="an-head">
      <div>
        <h1>{{ bigScreen ? t('选课数据大屏') : t('把每一分投入，看得更清楚') }}<span class="an-eyebrow an-eyebrow-inline"><span v-html="svgIcon('sparkles')" />{{ t('选课分析中心') }}</span></h1>
        <p v-if="!bigScreen">{{ t('从积分录取线、候补流转到排课密度，三个视角复盘这一轮选课。') }}</p>
      </div>
      <div class="an-head-side">
        <span v-if="bigScreen" class="bs-clock">{{ nowText }}</span>
        <div class="an-round-live"><span class="an-live-dot" />{{ t('第 {n} 轮 · 选课进行中', { n: ov.round }) }}</div>
        <div class="an-head-mini">{{ t('数据更新于 {time} · 共 {n} 门课程', { time: updatedAt, n: ov.totalCourses }) }}</div>
        <button class="bs-screen-btn" @click="toggleBigScreen">
          {{ bigScreen ? t('退出大屏') : t('进入大屏模式') }}
        </button>
      </div>
    </header>

    <nav class="an-tabs" :aria-label="t('选课分析中心导航')">
      <button
        v-for="t in tabs"
        :key="t.key"
        class="an-tab"
        :class="{ active: tab === t.key }"
        @click="switchTab(t.key)"
      >
        <span class="an-tab-icon" v-html="svgIcon(t.icon)" />
        <span class="an-tab-text"><b>{{ t.label }}</b><small>{{ t.desc }}</small></span>
      </button>
    </nav>

    <!-- ================= 总览 ================= -->
    <div v-if="tab === 'overview'" class="an-pane">
      <!-- ===== 原版 stats-grid + stat-card：统一高度/字号/图标 + 口径说明 ===== -->
      <div class="stats-grid">
        <div class="stat-card"><div class="stat-icon blue"   v-html="svgIcon('clipboard')" />
          <div><div class="stat-value">{{ animApplied }}</div><div class="stat-label">{{ t('本轮报名人次') }}</div><div class="stat-sub">{{ t('选修课程 · 不含退课') }}</div></div></div>
        <div class="stat-card"><div class="stat-icon green"  v-html="svgIcon('check-circle')" />
          <div><div class="stat-value">{{ animApproved }}</div><div class="stat-label">{{ t('已录取人数') }}</div><div class="stat-sub">{{ t('含候补递补 · 不含必修') }}</div></div></div>
        <div class="stat-card"><div class="stat-icon purple" v-html="svgIcon('target')" />
          <div><div class="stat-value">{{ animFillRate }}%</div><div class="stat-label">{{ t('总满员率') }}</div><div class="stat-sub">{{ t('总录取 ÷ 总容量（选修）') }}</div></div></div>
        <div class="stat-card"><div class="stat-icon amber"  v-html="svgIcon('hourglass')" />
          <div><div class="stat-value">{{ animFullCount }}</div><div class="stat-label">{{ t('满员课程数') }}</div><div class="stat-sub">{{ t('候补 {n} 人在队', { n: animWaiting }) }}</div></div></div>
      </div>

      <!-- ===== 轮次趋势：上移进首屏 ===== -->
      <div class="section">
        <div class="section-header"><div class="section-title"><span v-html="svgIcon('refresh')" />{{ t('轮次报名趋势') }}<span class="section-note">{{ t('全部课程（含必修），口径与下方选修排行不同') }}</span></div></div>
        <template v-if="rounds.length">
          <div v-for="x in rounds" :key="x.round" class="rank-row">
            <div class="rank-name">{{ t('第 {n} 轮', { n: x.round }) }}</div>
            <div class="rank-bar-wrap"><div class="rank-bar round" :style="{ width: Math.round((x.applied || 0) * 100 / rmMax) + '%' }" /></div>
            <div class="rank-val"><b>{{ t('报名 {n}', { n: x.applied }) }}</b><small>{{ t('录取 {a} · 未录取 {b} · 退课 {c}', { a: x.approved, b: x.rejected, c: x.dropped }) }}</small></div>
          </div>
        </template>
        <div v-else class="empty-state"><div class="empty-icon" v-html="svgIcon('refresh')" /><p>{{ t('暂无数据') }}</p></div>
      </div>

      <!-- ===== 原版 stats-two 双排行 ===== -->
      <div class="stats-two">
        <div class="section">
          <div class="section-header"><div class="section-title"><span v-html="svgIcon('bar-chart')" />{{ t('课程热度排行（本轮报名）') }}</div></div>
          <template v-if="hotFiltered.length">
            <div v-for="(x, i) in hotFiltered" :key="x.courseId" class="rank-row">
              <div class="rank-name">
                <span class="rank-idx" :class="{ top: i < 3 }">{{ i + 1 }}</span>
                {{ x.courseName }}
                <span class="rank-sub">{{ x.teacherName || '' }}</span>
              </div>
              <div class="rank-bar-wrap"><div class="rank-bar" :style="{ width: Math.round((x.applied || 0) * 100 / hyMax) + '%' }" /></div>
              <div class="rank-val"><b>{{ x.applied }}</b><small>{{ t('人报名') }}</small></div>
            </div>
          </template>
          <div v-else class="empty-state"><div class="empty-icon" v-html="svgIcon('bar-chart')" /><p>{{ t('暂无报名数据') }}</p></div>
        </div>

        <div class="section">
          <div class="section-header"><div class="section-title"><span v-html="svgIcon('target')" />{{ t('满员率排行') }}<span class="section-note">{{ t('满员时展示候补/名额，避免同分无区分') }}</span></div></div>
          <template v-if="fillFiltered.length">
            <div v-for="(x, i) in fillFiltered" :key="x.courseId" class="rank-row">
              <div class="rank-name">
                <span class="rank-idx" :class="{ top: i < 3 }">{{ i + 1 }}</span>
                {{ x.courseName }}
                <span class="rank-sub">{{ x.approved }}/{{ x.capacity }}</span>
              </div>
              <div class="rank-bar-wrap"><div class="rank-bar fill" :class="fillCls(x)" :style="{ width: Math.min(100, x.fillRate) + '%' }" /></div>
              <div class="rank-val">
                <b>{{ x.fillRate }}%</b>
                <small v-if="x.capacity - x.approved > 0">{{ t('剩 {n} 个名额', { n: x.capacity - x.approved }) }}</small>
                <small v-else-if="x.waiting > 0">{{ t('候补 {n} 人', { n: x.waiting }) }}</small>
                <small v-else>{{ t('恰好录满') }}</small>
              </div>
            </div>
          </template>
          <div v-else class="empty-state"><div class="empty-icon" v-html="svgIcon('target')" /><p>{{ t('暂无数据') }}</p></div>
        </div>
      </div>

    </div>

    <!-- ================= 分析 Tab ================= -->
    <div v-else class="an-pane">
      <div v-if="tab === 'points' || tab === 'waitlist' || tab === 'intents' || tab === 'fairness'" class="an-filterbar">
        <span class="an-filter-label">{{ t('轮次') }}</span>
        <div class="an-chips">
          <button
            v-for="r in roundOptions"
            :key="r"
            :class="{ active: round === r }"
            @click="pickRound(r)"
          >{{ t('第 {n} 轮', { n: r }) }}</button>
        </div>
        <span class="an-filter-note">
          {{ tab === 'points' ? t('仅统计非必修课程，已退课不计入')
            : tab === 'waitlist' ? t('覆盖加入、递补、确认的完整生命周期')
            : tab === 'intents' ? t('意向转报名后即出单，统计覆盖当前在册意向')
            : t('从学生录取分布、同分抽签与年级均衡三个维度评估分配公平') }}
        </span>
      </div>

      <div v-if="loading[tab]" class="an-loading">
        <i v-for="n in 4" :key="'k' + n" />
        <i class="wide" />
        <i class="tall" />
      </div>
      <PointsAnalysis v-else-if="tab === 'points' && points" :data="points" />
      <IntentAnalysis v-else-if="tab === 'intents' && intents" :data="intents" />
      <WaitlistAnalysis v-else-if="tab === 'waitlist' && waitlist" :data="waitlist" />
      <ClassroomAnalysis v-else-if="tab === 'classroom' && classroom" :data="classroom" />
      <GradeAnalysis v-else-if="tab === 'grades' && grades" :data="grades" />
      <ReviewAnalysis v-else-if="tab === 'reviews' && reviews" :data="reviews" />
      <FairnessAnalysis v-else-if="tab === 'fairness' && fairness" :data="fairness" />
      <RiskMonitor v-else-if="tab === 'risk' && risk" :data="risk" />
      <OperationAnalysis v-else-if="tab === 'operation' && operation" :data="operation" />
      <LiveMonitor v-else-if="tab === 'live'" />
      <div v-else class="an-panel">
        <div class="an-empty">
          <div class="an-empty-icon" v-html="svgIcon('bar-chart')" />
          <p>{{ t('暂时没有可用的分析数据') }}</p>
          <small>{{ t('换一个轮次，或稍后再试') }}</small>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, onBeforeUnmount } from 'vue'
import http from '@/api'
import { useCountUp } from '@/utils/useCountUp'
import { t } from '@/i18n'
import PointsAnalysis from '@/components/analysis/PointsAnalysis.vue'
import IntentAnalysis from '@/components/analysis/IntentAnalysis.vue'
import WaitlistAnalysis from '@/components/analysis/WaitlistAnalysis.vue'
import ClassroomAnalysis from '@/components/analysis/ClassroomAnalysis.vue'
import GradeAnalysis from '@/components/analysis/GradeAnalysis.vue'
import ReviewAnalysis from '@/components/analysis/ReviewAnalysis.vue'
import LiveMonitor from '@/components/analysis/LiveMonitor.vue'
import FairnessAnalysis from '@/components/analysis/FairnessAnalysis.vue'
import RiskMonitor from '@/components/analysis/RiskMonitor.vue'
import OperationAnalysis from '@/components/analysis/OperationAnalysis.vue'
import '@/styles/analysis.css'

const svgIcon = (n: string) => `<svg class="icon"><use href="#i-${n}"/></svg>`

type TabKey = 'overview' | 'live' | 'points' | 'intents' | 'waitlist' | 'classroom' | 'grades' | 'reviews' | 'fairness' | 'risk' | 'operation'

const tabs = computed<Array<{ key: TabKey; label: string; desc: string; icon: string }>>(() => [
  { key: 'overview', label: t('总览'), desc: t('核心指标与排行'), icon: 'grid' },
  { key: 'live', label: t('实时监控'), desc: t('操作节奏 · 报名赛跑'), icon: 'coins' },
  { key: 'points', label: t('积分分析'), desc: t('录取线 · 投分分布'), icon: 'coins' },
  { key: 'intents', label: t('意向分析'), desc: t('意向流转 · 投分偏差'), icon: 'bookmark' },
  { key: 'waitlist', label: t('候补分析'), desc: t('漏斗 · 队列效率'), icon: 'hourglass' },
  { key: 'classroom', label: t('教室利用'), desc: t('排课热力 · 地点'), icon: 'building' },
  { key: 'grades', label: t('成绩分析'), desc: t('分布 · 课程对比'), icon: 'award' },
  { key: 'reviews', label: t('评价分析'), desc: t('星级 · 热词云图'), icon: 'star' },
  { key: 'fairness', label: t('公平性分析'), desc: t('基尼系数 · 抽签占比'), icon: 'target' },
  { key: 'risk', label: t('性能风控'), desc: t('QPS · 告警巡检'), icon: 'alert-triangle' },
  { key: 'operation', label: t('运营分析'), desc: t('供需 · 退课 · 预警'), icon: 'clipboard' },
])

const tab = ref<TabKey>('overview')
const ov = reactive<any>({ round: 1, totalCourses: 0, totalUsers: 0, applied: 0, approved: 0, fillRate: 0, fullCount: 0, waiting: 0 })
const hot = ref<any[]>([])
const fill = ref<any[]>([])
const rounds = ref<any[]>([])

const round = ref(1)
const roundOptions = ref<number[]>([1])
const updatedAt = ref('—')

const points = ref<any>(null)
const waitlist = ref<any>(null)
const classroom = ref<any>(null)
const grades = ref<any>(null)
const intents = ref<any>(null)
const reviews = ref<any>(null)
const fairness = ref<any>(null)
const risk = ref<any>(null)
const operation = ref<any>(null)
const loading = reactive<Record<string, boolean>>({ points: false, waitlist: false, classroom: false, grades: false, intents: false, reviews: false, fairness: false, risk: false, operation: false })
const reqSeq: Record<string, number> = { points: 0, waitlist: 0, classroom: 0, grades: 0, intents: 0, reviews: 0, fairness: 0, risk: 0, operation: 0 }

const hotFiltered = computed(() => hot.value.filter(x => !x.required).slice(0, 10))
// 满员率并列时按候补人数、报名人数递减，保证排行有区分度
const fillFiltered = computed(() => fill.value
  .filter(x => !x.required && x.capacity > 0)
  .slice()
  .sort((a: any, b: any) => (b.fillRate - a.fillRate)
    || ((b.waiting || 0) - (a.waiting || 0))
    || ((b.applied || 0) - (a.applied || 0)))
  .slice(0, 10))
const hyMax = computed(() => Math.max(1, ...hotFiltered.value.map(x => x.applied || 0)))
const rmMax = computed(() => Math.max(1, ...rounds.value.map(x => x.applied || 0)))
// 满员率柔和配色：满员=琥珀、80+ =浅橙、其余=青绿
function fillCls(x: any) {
  const r = Number(x.fillRate) || 0
  if (r >= 100) return 'full'
  if (r >= 80) return 'high'
  return ''
}

async function load() {
  try {
    const r: any = await http.get('/stats/report')
    const d = r?.data || {}
    if (d.overview) Object.assign(ov, d.overview)
    hot.value = d.hot || []
    fill.value = d.fill || []
    rounds.value = d.rounds || []

    const list = Array.from(new Set<number>([
      Number(ov.round) || 1,
      ...(d.rounds || []).map((x: any) => Number(x.round)).filter((n: number) => n > 0),
    ])).sort((a, b) => a - b)
    roundOptions.value = list.length ? list : [1]
    if (!roundOptions.value.includes(round.value)) round.value = ov.round || 1

    updatedAt.value = new Date().toLocaleTimeString('zh-CN', { hour12: false, hour: '2-digit', minute: '2-digit' })


    // 预取分析 Tab，切换时无需等待（live 自带轮询，不在此取）
    loadAnalysis('points')
    loadAnalysis('waitlist')
    loadAnalysis('classroom')
    loadAnalysis('grades')
    loadAnalysis('intents')
    loadAnalysis('reviews')
    loadAnalysis('fairness')
  } catch {}
}

type AnalysisKey = 'points' | 'waitlist' | 'classroom' | 'grades' | 'intents' | 'reviews' | 'fairness' | 'risk' | 'operation'

async function loadAnalysis(k: AnalysisKey) {
  const seq = ++reqSeq[k]
  loading[k] = true
  try {
    const roundScoped = k === 'points' || k === 'waitlist' || k === 'intents' || k === 'fairness'
    const r: any = await http.get(`/stats/${k}`, roundScoped ? { params: { round: round.value } } : undefined)
    if (seq !== reqSeq[k]) return
    if (k === 'points') points.value = r?.data || null
    else if (k === 'waitlist') waitlist.value = r?.data || null
    else if (k === 'classroom') classroom.value = r?.data || null
    else if (k === 'grades') grades.value = r?.data || null
    else if (k === 'intents') intents.value = r?.data || null
    else if (k === 'fairness') fairness.value = r?.data || null
    else if (k === 'risk') risk.value = r?.data || null
    else if (k === 'operation') operation.value = r?.data || null
    else reviews.value = r?.data || null
  } catch {
    if (seq === reqSeq[k]) {
      if (k === 'points') points.value = null
      else if (k === 'waitlist') waitlist.value = null
      else if (k === 'classroom') classroom.value = null
      else if (k === 'grades') grades.value = null
      else if (k === 'intents') intents.value = null
      else if (k === 'fairness') fairness.value = null
      else if (k === 'risk') risk.value = null
      else if (k === 'operation') operation.value = null
      else reviews.value = null
    }
  } finally {
    if (seq === reqSeq[k]) loading[k] = false
  }
}

function pickRound(r: number) {
  if (round.value === r) return
  round.value = r
  points.value = null
  waitlist.value = null
  intents.value = null
  fairness.value = null
  if (tab.value === 'points') loadAnalysis('points')
  if (tab.value === 'waitlist') loadAnalysis('waitlist')
  if (tab.value === 'intents') loadAnalysis('intents')
  if (tab.value === 'fairness') loadAnalysis('fairness')
}

function switchTab(k: TabKey) {
  tab.value = k
  if (k === 'points' && !points.value && !loading.points) loadAnalysis('points')
  if (k === 'waitlist' && !waitlist.value && !loading.waitlist) loadAnalysis('waitlist')
  if (k === 'classroom' && !classroom.value && !loading.classroom) loadAnalysis('classroom')
  if (k === 'grades' && !grades.value && !loading.grades) loadAnalysis('grades')
  if (k === 'intents' && !intents.value && !loading.intents) loadAnalysis('intents')
  if (k === 'reviews' && !reviews.value && !loading.reviews) loadAnalysis('reviews')
  if (k === 'fairness' && !fairness.value && !loading.fairness) loadAnalysis('fairness')
  if (k === 'risk' && !risk.value && !loading.risk) loadAnalysis('risk')
  if (k === 'operation' && !operation.value && !loading.operation) loadAnalysis('operation')
}

/* 数字滚动计数动画（统一 composable；fillRate 保留 1 位小数） */
const animApplied = useCountUp(() => Number(ov.applied) || 0)
const animApproved = useCountUp(() => Number(ov.approved) || 0)
const animFillRate = useCountUp(() => Number(ov.fillRate) || 0, { decimals: 1 })
const animFullCount = useCountUp(() => Number(ov.fullCount) || 0)
const animWaiting = useCountUp(() => Number(ov.waiting) || 0)
/* ===== 大屏模式：全屏 + 深色控制中心 ===== */
const screenRef = ref<HTMLElement | null>(null)
const bigScreen = ref(false)
const nowText = ref('')
let bsClock: any = null
function bsTick() {
  const d = new Date()
  const p = (n: number) => String(n).padStart(2, '0')
  nowText.value = `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${t(['周日', '周一', '周二', '周三', '周四', '周五', '周六'][d.getDay()])} ${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`
}
async function toggleBigScreen() {
  if (!bigScreen.value) {
    try { await screenRef.value?.requestFullscreen() } catch {}
  } else {
    try { await document.exitFullscreen() } catch {}
  }
}
function onFsChange() {
  bigScreen.value = document.fullscreenElement === screenRef.value
}
onMounted(() => {
  document.addEventListener('fullscreenchange', onFsChange)
  bsClock = setInterval(bsTick, 1000)
  bsTick()
  load()
})
onBeforeUnmount(() => {
  document.removeEventListener('fullscreenchange', onFsChange)
  clearInterval(bsClock)
})
</script>

<style scoped>
/* 排行副文字提到可读下限以上（原浏览器 small 默认 ~11.5px） */
.rank-val small { font-size: 12px; }
</style>
